package org.elasticsearch.search.highlight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.base.Function;
import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.core.StringFieldMapper;
import org.elasticsearch.search.highlight.ExperimentalHighlighter.HighlightExecutionContext;
import org.elasticsearch.search.highlight.SearchContextHighlight.FieldOptions;
import org.wikimedia.highlighter.experimental.elasticsearch.BytesRefTermWeigherCache;
import org.wikimedia.highlighter.experimental.elasticsearch.SegmenterFactory;
import org.wikimedia.highlighter.experimental.lucene.hit.DocsAndPositionsHitEnum;
import org.wikimedia.highlighter.experimental.lucene.hit.TokenStreamHitEnum;
import org.wikimedia.highlighter.experimental.lucene.hit.weight.BasicQueryWeigher;
import org.wikimedia.highlighter.experimental.lucene.hit.weight.DefaultSimilarityTermWeigher;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.Segmenter;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.hit.ConcatHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.PositionBoostingHitEnumWrapper;
import org.wikimedia.search.highlighter.experimental.hit.ReplayingHitEnum.HitEnumAndLength;
import org.wikimedia.search.highlighter.experimental.hit.TermWeigher;
import org.wikimedia.search.highlighter.experimental.hit.WeightFilteredHitEnumWrapper;
import org.wikimedia.search.highlighter.experimental.hit.weight.CachingTermWeigher;
import org.wikimedia.search.highlighter.experimental.hit.weight.ConstantTermWeigher;
import org.wikimedia.search.highlighter.experimental.snippet.MultiSegmenter;
import org.wikimedia.search.highlighter.experimental.source.NonMergingMultiSourceExtracter;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;

public class FieldWrapper {
    private final HighlightExecutionContext executionContext;
    private final HighlighterContext context;
    private final BasicQueryWeigher weigher;
    private List<String> values;
    /**
     * If there is a TokenStream still open during the highlighting.
     */
    private TokenStream tokenStream;
    /**
     * Position gap for the field.  Only looked up if needed.  < 0 means not looked up.
     */
    private int positionGap = 1;

    /**
     * Build a wrapper around the default field in the context.
     */
    public FieldWrapper(HighlightExecutionContext executionContext, HighlighterContext context, 
            BasicQueryWeigher weigher) {
        this.executionContext = executionContext;
        this.context = context;
        this.weigher = weigher;
    }

    /**
     * Build a wrapper around fieldName which is not the default field in the
     * context.
     */
    public FieldWrapper(HighlightExecutionContext executionContext, HighlighterContext context,
            BasicQueryWeigher weigher, String fieldName) {
        assert !context.fieldName.equals(fieldName);
        FieldMapper<?> mapper = context.context.smartNameFieldMapper(fieldName);
        this.executionContext = executionContext;
        this.context = new HighlighterContext(fieldName, context.field, mapper, context.context,
                context.hitContext, context.query);
        this.weigher = weigher;
    }

    /**
     * Name of the wrapped field.
     */
    public String fieldName() {
        return context.fieldName;
    }

    @Override
    public String toString() {
        return context.fieldName;
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

    /**
     * Does this field have more then one value?
     */
    public boolean isMultValued() throws IOException {
        return getFieldValues().size() > 1;
    }

    public Segmenter buildSegmenter() throws IOException {
        List<String> fieldValues = getFieldValues();
        SegmenterFactory segmenterFactory = executionContext.getSegmenterFactory();
        switch (fieldValues.size()) {
        case 0:
            return segmenterFactory.build("");
        case 1:
            return segmenterFactory.build(fieldValues.get(0));
        default:
            // Elasticsearch uses a string offset gap of 1, the default on the
            // builder.
            MultiSegmenter.Builder builder = MultiSegmenter.builder();
            for (String s : fieldValues) {
                builder.add(segmenterFactory.build(s), s.length());
            }
            return builder.build();
        }
    }

    /**
     * Can this field produce hits? This'll return false when there are no terms
     * worth anything that aren't in phrases AND there aren't any phrases on
     * this field.
     */
    public boolean canProduceHits() {
        return weigher.maxTermWeight() > 0 || weigher.areTherePhrasesOnField(context.fieldName);
    }

    public HitEnum buildHitEnum() throws IOException {
        HitEnum e = buildHitEnumForSource();

        // Support phrase matches. Note that this must be done here rather than
        // after merging HitEnums because each hit could map offsets to
        // different positions. Since they are merged based on _offset_ the
        // phrase wrapper will see jumbled positions, causing it to break
        // horribly. Don't do it. I've tried.
        e = weigher.wrap(context.fieldName, e);

        FieldOptions options = context.field.fieldOptions();
        if (!options.scoreOrdered()) {
            Boolean topScoring = (Boolean)executionContext.getOption("top_scoring");
            if (topScoring == null || !topScoring) {
                // If we don't pay attention to scoring then there is no point
                // is messing with the weights.

                // Filter 0 weight hits which pop out from the TokenStreamHitEnum,
                // phrase match misses.
                e = new WeightFilteredHitEnumWrapper(e, 0f);

                return e;
            }
        }
        // TODO move this up so we don't have to redo it per matched_field
        @SuppressWarnings("unchecked")
        Map<String, Object> boostBefore = (Map<String, Object>)executionContext.getOption("boost_before");
        if (boostBefore != null) {
            TreeMap<Integer, Float> ordered = new TreeMap<Integer, Float>();
            for (Map.Entry<String, Object> entry : boostBefore.entrySet()) {
                if (!(entry.getValue() instanceof Number)) {
                    throw new IllegalArgumentException("boost_before must be a flat object who's values are numbers.");
                }
                ordered.put(Integer.valueOf(entry.getKey()), ((Number)entry.getValue()).floatValue());
            }
            PositionBoostingHitEnumWrapper boosting = new PositionBoostingHitEnumWrapper(e);
            e = boosting;
            for (Map.Entry<Integer, Float> entry: ordered.entrySet()) {
                boosting.add(entry.getKey(), entry.getValue());
            }
        }

        // Filter 0 weight hits which pop out from the TokenStreamHitEnum,
        // phrase match misses, and boost_before being used as a filter.
        e = new WeightFilteredHitEnumWrapper(e, 0f);

        return e;
    }

    private HitEnum buildHitEnumForSource() throws IOException {
        if (context.field.fieldOptions().options() != null) {
            String hitSource = (String) context.field.fieldOptions().options().get("hit_source");
            if (hitSource != null) {
                if (hitSource.equals("postings")) {
                    if (!canUsePostingsHitEnum()) {
                        throw new IllegalArgumentException(
                                "Can't use postings as a hit source without setting index_options to postings");
                    }
                    return buildPostingsHitEnum();
                } else if (hitSource.equals("vectors")) {
                    if (!canUseVectorsHitEnum()) {
                        throw new IllegalArgumentException(
                                "Can't use vectors as a hit source without setting term_vector to with_positions_offsets");
                    }
                    return buildTermVectorsHitEnum();
                } else if (hitSource.equals("analyze")) {
                    return buildTokenStreamHitEnum();
                } else {
                    throw new IllegalArgumentException("Unknown hit source:  " + hitSource);
                }
            }
        }
        if (canUsePostingsHitEnum()) {
            return buildPostingsHitEnum();
        }
        if (canUseVectorsHitEnum()) {
            return buildTermVectorsHitEnum();
        }
        return buildTokenStreamHitEnum();
    }

    private boolean canUsePostingsHitEnum() {
        return context.mapper.fieldType().indexOptions() == FieldInfo.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;
    }

    private boolean canUseVectorsHitEnum() {
        return context.mapper.fieldType().storeTermVectors()
                && context.mapper.fieldType().storeTermVectorOffsets()
                && context.mapper.fieldType().storeTermVectorPositions();
    }

    private HitEnum buildPostingsHitEnum() throws IOException {
        return DocsAndPositionsHitEnum.fromPostings(context.hitContext.reader(),
                context.hitContext.docId(), context.mapper.names().indexName(),
                weigher.acceptableTerms(), getQueryWeigher(false), getCorpusWeigher(false), weigher);
    }

    private HitEnum buildTermVectorsHitEnum() throws IOException {
        return DocsAndPositionsHitEnum.fromTermVectors(context.hitContext.reader(),
                context.hitContext.docId(), context.mapper.names().indexName(),
                weigher.acceptableTerms(), getQueryWeigher(false), getCorpusWeigher(false), weigher);
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
            /*
             * Note that it is super important that this process is _lazy_
             * because we can't have multiple TokenStreams open per analyzer.
             */
            Iterator<HitEnumAndLength> hitEnumsFromStreams = Iterators.transform(
                    fieldValues.iterator(), new Function<String, HitEnumAndLength>() {
                        @Override
                        public HitEnumAndLength apply(String fieldValue) {
                            try {
                                if (tokenStream != null) {
                                    tokenStream.close();
                                }
                                return new HitEnumAndLength(buildTokenStreamHitEnum(analyzer,
                                        fieldValue), fieldValue.length());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                        }
                    });
            return new ConcatHitEnum(hitEnumsFromStreams, getPositionGap(), 1);
        }
    }

    private HitEnum buildTokenStreamHitEnum(Analyzer analyzer, String source) throws IOException {
        TokenStream tokenStream;
        try {
            tokenStream = analyzer.tokenStream(context.fieldName, source);
        } catch (IllegalStateException e) {
            // Uhg, I wish we didn't have this limitation but it isn't really
            // very common and shouldn't be too big of a problem.
            throw new UnsupportedOperationException(
                    "If analyzing to find hits each matched field must have a unique analyzer.", e);
        }
        this.tokenStream = tokenStream;
        return new TokenStreamHitEnum(tokenStream, getQueryWeigher(true), getCorpusWeigher(true), weigher);
    }

    private TermWeigher<BytesRef> getQueryWeigher(boolean mightWeighTermsMultipleTimes) {
        return weigher;
    }

    private TermWeigher<BytesRef> getCorpusWeigher(boolean mightWeighTermsMultipleTimes) {
        // No need to add fancy term weights if there is only one term or we
        // aren't using score order.
        if (weigher.singleTerm() || !executionContext.scoreMatters()) {
            return new ConstantTermWeigher<BytesRef>();
        }
        Boolean useDefaultSimilarity = (Boolean) executionContext.getOption("default_similarity");
        if (useDefaultSimilarity == null || useDefaultSimilarity == true) {
            // Use a top level reader to fetch the frequency information
            TermWeigher<BytesRef> corpusWeigher = new DefaultSimilarityTermWeigher(context.hitContext.topLevelReader(),
                    context.fieldName);
            // TODO maybe switch to a recycling instance on the off chance that
            // we find a ton of terms in the document. That'd require more work
            // to make sure everything is properly Releasable.
            if (mightWeighTermsMultipleTimes) {
                corpusWeigher = new CachingTermWeigher<BytesRef>(new BytesRefTermWeigherCache(
                        BigArrays.NON_RECYCLING_INSTANCE), corpusWeigher);
            }
            return corpusWeigher;
        }
        return new ConstantTermWeigher<BytesRef>();
    }
    
    public int getPositionGap() {
        if (positionGap < 0) {
            if (context.mapper instanceof StringFieldMapper) {
                positionGap = ((StringFieldMapper) context.mapper).getPositionOffsetGap();
            } else {
                positionGap = 1;
            }
        }
        return positionGap;
    }
}
