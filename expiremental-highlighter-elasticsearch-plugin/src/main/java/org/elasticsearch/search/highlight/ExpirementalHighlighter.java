package org.elasticsearch.search.highlight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.FieldInfo;
import org.elasticsearch.common.base.Function;
import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.text.StringText;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.mapper.core.StringFieldMapper;
import org.elasticsearch.search.fetch.FetchPhaseExecutionException;
import org.elasticsearch.search.highlight.SearchContextHighlight.FieldOptions;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.Snippet;
import expiremental.highlighter.SnippetChooser;
import expiremental.highlighter.SnippetFormatter;
import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.elasticsearch.ElasticsearchQueryFlattener;
import expiremental.highlighter.hit.ReplayingHitEnum;
import expiremental.highlighter.hit.WeightFilteredHitEnumWrapper;
import expiremental.highlighter.lucene.hit.DocsAndPositionsHitEnum;
import expiremental.highlighter.lucene.hit.TokenStreamHitEnum;
import expiremental.highlighter.lucene.hit.weight.BasicQueryWeigher;
import expiremental.highlighter.snippet.BasicScoreOrderSnippetChooser;
import expiremental.highlighter.snippet.BasicSourceOrderSnippetChooser;
import expiremental.highlighter.snippet.CharScanningSegmenter;
import expiremental.highlighter.snippet.MultiSegmenter;
import expiremental.highlighter.source.NonMergingMultiSourceExtracter;
import expiremental.highlighter.source.StringSourceExtracter;

public class ExpirementalHighlighter implements Highlighter {
    private static final String CACHE_KEY = "highlight-expiremental";

    @Override
    public String[] names() {
        return new String[] { "expiremental" };
    }

    @Override
    public HighlightField highlight(HighlighterContext context) {
        try {
            CacheEntry entry = (CacheEntry) context.hitContext.cache().get(CACHE_KEY);
            if (entry == null) {
                entry = new CacheEntry();
                entry.queryWeigher = new BasicQueryWeigher(new ElasticsearchQueryFlattener(100),
                        context.hitContext.reader(), context.query.originalQuery());
                context.hitContext.cache().put(CACHE_KEY, entry);
            }
            HighlightExecutionContext executionContext = new HighlightExecutionContext(context,
                    entry);
            try {
                return new HighlightField(context.fieldName, executionContext.highlight());
            } finally {
                executionContext.cleanup();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new FetchPhaseExecutionException(context.context, "Failed to highlight field ["
                    + context.fieldName + "]", e);
        }
    }

    private static class CacheEntry {
        private BasicQueryWeigher queryWeigher;
    }

    private static class HighlightExecutionContext {
        private final HighlighterContext context;
        private final CacheEntry cacheEntry;
        /**
         * The source fields, cached.
         */
        private List<String> source;
        /**
         * If there is a TokenStream still open during the highlighting.
         */
        private TokenStream tokenStream;

        private HighlightExecutionContext(HighlighterContext context, CacheEntry cacheEntry) {
            this.context = context;
            this.cacheEntry = cacheEntry;
        }

        private Text[] highlight() throws IOException {
            return formatSnippets(buildChooser().choose(buildSegmenter(), buildHitEnum(),
                    context.field.fieldOptions().numberOfFragments()));
        }

        private void cleanup() throws IOException {
            if (tokenStream != null) {
                try {
                    tokenStream.end();
                } finally {
                    tokenStream.close();
                }
            }
        }

        private List<String> getFieldValues() throws IOException {
            if (this.source == null) {
                List<Object> objs = HighlightUtils.loadFieldValues(context.field, context.mapper,
                        context.context, context.hitContext);
                this.source = new ArrayList<String>(objs.size());
                for (Object obj : objs) {
                    this.source.add(obj.toString());
                }
            }
            return this.source;
        }

        private SourceExtracter<String> buildSourceExtracter() throws IOException {
            // TODO loading source is expensive if you don't need it. Delay
            // this.
            List<String> fieldValues = getFieldValues();
            switch (fieldValues.size()) {
            case 0:
                return new StringSourceExtracter("");
            case 1:
                return new StringSourceExtracter(fieldValues.get(0));
            default:
                // Elasticsearch uses a string offset gap of 1, the default on the builder.
                NonMergingMultiSourceExtracter.Builder<String> builder = NonMergingMultiSourceExtracter.builder();
                for (String s : fieldValues) {
                    builder.add(new StringSourceExtracter(s), s.length());
                }
                return builder.build();
            }
        }

        private Segmenter buildSegmenter() throws IOException {
            // TODO loading source is expensive if you don't need it. Delay
            // this.
            List<String> fieldValues = getFieldValues();
            switch (fieldValues.size()) {
            case 0:
                return buildSegmenter("");
            case 1:
                return buildSegmenter(fieldValues.get(0));
            default:
                // Elasticsearch uses a string offset gap of 1, the default on the builder.
                MultiSegmenter.Builder builder = MultiSegmenter.builder();
                for (String s : fieldValues) {
                    builder.add(buildSegmenter(s), s.length());
                }
                return builder.build();
            }
        }

        private Segmenter buildSegmenter(String source) {
            FieldOptions options = context.field.fieldOptions();
            // TODO boundaryChars
            return new CharScanningSegmenter(source, options.fragmentCharSize(),
                    options.boundaryMaxScan());
        }

        private HitEnum buildHitEnum() throws IOException {
            if (context.field.fieldOptions().options() != null) {
                String hitSource = (String) context.field.fieldOptions().options()
                        .get("hit_source");
                if (hitSource != null) {
                    if (hitSource.equals("postings")) {
                        return buildPostingsHitEnum();
                    } else if (hitSource.equals("vectors")) {
                        return buildTermVectorHitEnum();
                    } else if (hitSource.equals("analyze")) {
                        return buildTokenStreamHitEnum();
                    } else {
                        throw new IllegalArgumentException("Unknown hit source:  " + hitSource);
                    }
                }
            }
            if (context.mapper.fieldType().indexOptions() == FieldInfo.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) {
                return buildPostingsHitEnum();
            }
            if (context.mapper.fieldType().storeTermVectors()
                    && context.mapper.fieldType().storeTermVectorOffsets()
                    && context.mapper.fieldType().storeTermVectorPositions()) {
                return buildTermVectorHitEnum();
            }
            return buildTokenStreamHitEnum();
        }

        private HitEnum buildPostingsHitEnum() throws IOException {
            return DocsAndPositionsHitEnum.fromPostings(context.hitContext.reader(),
                    context.hitContext.docId(), context.fieldName,
                    cacheEntry.queryWeigher.acceptableTerms(),
                    cacheEntry.queryWeigher.termWeigher());
        }

        private HitEnum buildTermVectorHitEnum() throws IOException {
            return DocsAndPositionsHitEnum.fromTermVectors(context.hitContext.reader(),
                    context.hitContext.docId(), context.fieldName,
                    cacheEntry.queryWeigher.acceptableTerms(),
                    cacheEntry.queryWeigher.termWeigher());
        }

        private HitEnum buildTokenStreamHitEnum() throws IOException {
            List<String> fieldValues = getFieldValues();
            switch (fieldValues.size()) {
            case 0:
                return buildTokenStreamHitEnum("");
            case 1:
                return buildTokenStreamHitEnum(fieldValues.get(0));
            default:
                // TODO switch to just in time creation of the TokenStream so we don't have to record
                ReplayingHitEnum replaying = new ReplayingHitEnum();
                int positionGap = 1;
                if (context.mapper instanceof StringFieldMapper) {
                    positionGap = ((StringFieldMapper) context.mapper).getPositionOffsetGap();
                }
                replaying.record(Iterators.transform(fieldValues.iterator(),
                        new Function<String, HitEnum>() {
                            @Override
                            public HitEnum apply(String fieldValue) {
                                try {
                                    if (tokenStream != null) {
                                        try {
                                            tokenStream.end();
                                        } finally {
                                            tokenStream.close();
                                        }
                                    }
                                    return buildTokenStreamHitEnum(fieldValue);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }

                            }
                        }), positionGap, 1);
                return replaying;
            }
        }

        private HitEnum buildTokenStreamHitEnum(String source) throws IOException {
            Analyzer analyzer = context.mapper.indexAnalyzer();
            if (analyzer == null) {
                analyzer = context.context.analysisService().defaultIndexAnalyzer();
            }
            TokenStream tokenStream = analyzer.tokenStream(context.fieldName, source);
            this.tokenStream = tokenStream;
            return new WeightFilteredHitEnumWrapper(new TokenStreamHitEnum(tokenStream,
                    cacheEntry.queryWeigher.termWeigher()), 0);
        }

        private SnippetChooser buildChooser() {
            if (context.field.fieldOptions().scoreOrdered()) {
                return new BasicScoreOrderSnippetChooser();
            }
            return new BasicSourceOrderSnippetChooser();
        }

        private Text[] formatSnippets(List<Snippet> snippets) throws IOException {
            SnippetFormatter formatter = new SnippetFormatter(buildSourceExtracter(), context.field
                    .fieldOptions().preTags()[0], context.field.fieldOptions().postTags()[0]);
            Text[] result = new Text[snippets.size()];
            int i = 0;
            for (Snippet snippet : snippets) {
                result[i++] = new StringText(formatter.format(snippet));
            }
            return result;
        }
    }
}
