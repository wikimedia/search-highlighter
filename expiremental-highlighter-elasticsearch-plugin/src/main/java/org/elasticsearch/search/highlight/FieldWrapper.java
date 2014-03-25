package org.elasticsearch.search.highlight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.FieldInfo;
import org.elasticsearch.common.base.Function;
import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.core.StringFieldMapper;
import org.elasticsearch.search.highlight.ExpirementalHighlighter.CacheEntry;
import org.elasticsearch.search.highlight.SearchContextHighlight.FieldOptions;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.hit.ConcatHitEnum;
import expiremental.highlighter.hit.WeightFilteredHitEnumWrapper;
import expiremental.highlighter.lucene.hit.DocsAndPositionsHitEnum;
import expiremental.highlighter.lucene.hit.TokenStreamHitEnum;
import expiremental.highlighter.snippet.CharScanningSegmenter;
import expiremental.highlighter.snippet.MultiSegmenter;
import expiremental.highlighter.source.NonMergingMultiSourceExtracter;
import expiremental.highlighter.source.StringSourceExtracter;

public class FieldWrapper {
    private final HighlighterContext context;
    private final CacheEntry cacheEntry;
    private List<String> values;
    /**
     * If there is a TokenStream still open during the highlighting.
     */
    private TokenStream tokenStream;

    /**
     * Build a wrapper around the default field in the context.
     */
    public FieldWrapper(HighlighterContext context, CacheEntry cacheEntry) {
        this.context = context;
        this.cacheEntry = cacheEntry;
    }

    /**
     * Cleanup any resources we still have open.
     */
    public void cleanup() throws IOException {
        if (tokenStream != null) {
            try {
                tokenStream.end();
            } finally {
                tokenStream.close();
            }
        }
    }

    /**
     * Build a wrapper around fieldName which is not the default field in the
     * context.
     */
    public FieldWrapper(HighlighterContext context, CacheEntry cacheEntry, String fieldName) {
        assert !context.fieldName.equals(fieldName);
        FieldMapper<?> mapper = context.context.smartNameFieldMapper(fieldName);
        this.context = new HighlighterContext(fieldName, context.field, mapper, context.context,
                context.hitContext, context.query);
        this.cacheEntry = cacheEntry;
    }

    public List<String> getFieldValues() throws IOException {
        if (values == null) {
            List<Object> objs = HighlightUtils.loadFieldValues(context.field, context.mapper,
                    context.context, context.hitContext);
            values = new ArrayList<String>(objs.size());
            for (Object obj : objs) {
                values.add(obj.toString());
            }
        }
        return values;
    }

    public SourceExtracter<String> buildSourceExtracter() throws IOException {
        // TODO loading source is expensive if you don't need it. Delay
        // this.
        List<String> fieldValues = getFieldValues();
        switch (fieldValues.size()) {
        case 0:
            return new StringSourceExtracter("");
        case 1:
            return new StringSourceExtracter(fieldValues.get(0));
        default:
            // Elasticsearch uses a string offset gap of 1, the default on the
            // builder.
            NonMergingMultiSourceExtracter.Builder<String> builder = NonMergingMultiSourceExtracter
                    .builder();
            for (String s : fieldValues) {
                builder.add(new StringSourceExtracter(s), s.length());
            }
            return builder.build();
        }
    }

    public Segmenter buildSegmenter() throws IOException {
        // TODO loading source is expensive if you don't need it. Delay
        // this.
        List<String> fieldValues = getFieldValues();
        switch (fieldValues.size()) {
        case 0:
            return buildSegmenter("");
        case 1:
            return buildSegmenter(fieldValues.get(0));
        default:
            // Elasticsearch uses a string offset gap of 1, the default on the
            // builder.
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

    public HitEnum buildHitEnum() throws IOException {
        if (context.field.fieldOptions().options() != null) {
            String hitSource = (String) context.field.fieldOptions().options().get("hit_source");
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
                cacheEntry.queryWeigher.acceptableTerms(), cacheEntry.queryWeigher.termWeigher());
    }

    private HitEnum buildTermVectorHitEnum() throws IOException {
        return DocsAndPositionsHitEnum.fromTermVectors(context.hitContext.reader(),
                context.hitContext.docId(), context.fieldName,
                cacheEntry.queryWeigher.acceptableTerms(), cacheEntry.queryWeigher.termWeigher());
    }

    private HitEnum buildTokenStreamHitEnum() throws IOException {
        Analyzer analyzer = context.mapper.indexAnalyzer();
        if (analyzer == null) {
            analyzer = context.context.analysisService().defaultIndexAnalyzer();
        }
        return buildTokenStreamHitEnum(analyzer);
    }

    private HitEnum buildTokenStreamHitEnum(final Analyzer analyzer) throws IOException {
        List<String> fieldValues = getFieldValues();
        switch (fieldValues.size()) {
        case 0:
            return buildTokenStreamHitEnum(analyzer, "");
        case 1:
            return buildTokenStreamHitEnum(analyzer, fieldValues.get(0));
        default:
            int positionGap = 1;
            if (context.mapper instanceof StringFieldMapper) {
                positionGap = ((StringFieldMapper) context.mapper).getPositionOffsetGap();
            }
            /*
             * Note that it is super important that this process is _lazy_
             * because we can't have multiple TokenStreams open per analyzer.
             */
            Iterator<HitEnum> hitEnumsFromStreams = Iterators.transform(fieldValues.iterator(),
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
                                return buildTokenStreamHitEnum(analyzer, fieldValue);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                        }
                    });
            return new ConcatHitEnum(hitEnumsFromStreams, positionGap, 1);
        }
    }

    private HitEnum buildTokenStreamHitEnum(Analyzer analyzer, String source) throws IOException {
        TokenStream tokenStream;
        try {
            tokenStream = analyzer.tokenStream(context.fieldName, source);
        } catch(IllegalStateException e) {
            // Uhg, I wish we didn't have this limitation but it isn't really very common and shouldn't be too big of a problem.
            throw new UnsupportedOperationException("If analyzing to find hits each matched field must have a unique analyzer.", e);
        }
        this.tokenStream = tokenStream;
        return new WeightFilteredHitEnumWrapper(new TokenStreamHitEnum(tokenStream,
                cacheEntry.queryWeigher.termWeigher()), 0);
    }
}
