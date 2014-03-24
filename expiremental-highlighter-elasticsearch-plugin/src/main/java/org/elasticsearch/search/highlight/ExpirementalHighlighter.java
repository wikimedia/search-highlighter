package org.elasticsearch.search.highlight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.FieldInfo;
import org.elasticsearch.common.text.StringText;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.search.fetch.FetchPhaseExecutionException;
import org.elasticsearch.search.highlight.SearchContextHighlight.FieldOptions;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.Snippet;
import expiremental.highlighter.SnippetChooser;
import expiremental.highlighter.SnippetFormatter;
import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.elasticsearch.ElasticsearchQueryFlattener;
import expiremental.highlighter.hit.WeightFilteredHitEnumWrapper;
import expiremental.highlighter.lucene.hit.DocsAndPositionsHitEnum;
import expiremental.highlighter.lucene.hit.TokenStreamHitEnum;
import expiremental.highlighter.lucene.hit.weight.BasicQueryWeigher;
import expiremental.highlighter.snippet.BasicScoreOrderSnippetChooser;
import expiremental.highlighter.snippet.BasicSourceOrderSnippetChooser;
import expiremental.highlighter.snippet.CharScanningSegmenter;
import expiremental.highlighter.snippet.MultiSegmenter;
import expiremental.highlighter.snippet.MultiSegmenter.ConstituentSegmenter;
import expiremental.highlighter.source.AbstractMultiSourceExtracter.ConstituentExtracter;
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
         * Any TokenStreams required for highlighting.
         */
        private List<TokenStream> tokenStreams;

        private HighlightExecutionContext(HighlighterContext context, CacheEntry cacheEntry) {
            this.context = context;
            this.cacheEntry = cacheEntry;
        }

        private Text[] highlight() throws IOException {
            return formatSnippets(buildChooser().choose(buildSegmenter(), buildHitEnum(),
                    context.field.fieldOptions().numberOfFragments()));
        }

        private void cleanup() throws IOException {
            if (tokenStreams != null) {
                for (TokenStream tokenStream : tokenStreams) {
                    try {
                        tokenStream.end();
                    } finally {
                        tokenStream.close();
                    }
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
                List<ConstituentExtracter<String>> consituents = new ArrayList<ConstituentExtracter<String>>(
                        fieldValues.size());
                for (String s : fieldValues) {
                    consituents.add(new ConstituentExtracter<String>(new StringSourceExtracter(s),
                            s.length()));
                }
                return new NonMergingMultiSourceExtracter<String>(consituents);
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
                List<ConstituentSegmenter> consituents = new ArrayList<ConstituentSegmenter>(
                        fieldValues.size());
                for (String s : fieldValues) {
                    consituents.add(new ConstituentSegmenter(buildSegmenter(s), s.length()));
                }
                return new MultiSegmenter(consituents);
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
                String hitSource = (String)context.field.fieldOptions().options().get("hit_source");
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
            this.tokenStreams = new ArrayList<TokenStream>();
            List<String> fieldValues = getFieldValues();
            switch (fieldValues.size()) {
            case 0:
                return buildTokenStreamHitEnum("");
            case 1:
                return buildTokenStreamHitEnum(fieldValues.get(0));
            default:
                // TODO something to combine token stream hit enums
                throw new UnsupportedOperationException(
                        "Can't extract matches from multiple sources yet");
            }
        }

        private HitEnum buildTokenStreamHitEnum(String source) throws IOException {
            Analyzer analyzer = context.mapper.indexAnalyzer();
            if (analyzer == null) {
                analyzer = context.context.analysisService().defaultIndexAnalyzer();
            }
            TokenStream tokenStream = analyzer.tokenStream(context.fieldName, source);
            this.tokenStreams.add(tokenStream);
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
