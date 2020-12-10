package org.wikimedia.highlighter.experimental.elasticsearch;


import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.LocaleUtils;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.search.fetch.FetchPhaseExecutionException;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.fetch.subphase.highlight.Highlighter;
import org.elasticsearch.search.fetch.subphase.highlight.FieldHighlightContext;
import org.elasticsearch.search.fetch.subphase.highlight.SearchHighlightContext.FieldOptions;
import org.wikimedia.highlighter.experimental.lucene.hit.AutomatonHitEnum;
import org.wikimedia.highlighter.experimental.lucene.hit.weight.BasicQueryWeigher;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.Snippet.HitBuilder;
import org.wikimedia.search.highlighter.experimental.SnippetChooser;
import org.wikimedia.search.highlighter.experimental.SnippetFormatter;
import org.wikimedia.search.highlighter.experimental.SnippetWeigher;
import org.wikimedia.search.highlighter.experimental.hit.ConcatHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.EmptyHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.MergingHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.OverlapMergingHitEnumWrapper;
import org.wikimedia.search.highlighter.experimental.hit.RegexHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.ReplayingHitEnum.HitEnumAndLength;
import org.wikimedia.search.highlighter.experimental.snippet.BasicScoreBasedSnippetChooser;
import org.wikimedia.search.highlighter.experimental.snippet.BasicSourceOrderSnippetChooser;
import org.wikimedia.search.highlighter.experimental.snippet.ExponentialSnippetWeigher;
import org.wikimedia.search.highlighter.experimental.snippet.SumSnippetWeigher;
import org.wikimedia.search.highlighter.experimental.tools.GraphvizHit;
import org.wikimedia.search.highlighter.experimental.tools.GraphvizHitEnum;
import org.wikimedia.search.highlighter.experimental.tools.GraphvizSnippetFormatter;

@SuppressWarnings("checkstyle:classfanoutcomplexity") // to improve if we ever touch that code again
public class ExperimentalHighlighter implements Highlighter {
    public static final String NAME = "experimental";
    private static final String CACHE_KEY = "highlight-experimental";
    private static final Text EMPTY_STRING = new Text("");

    @Override
    public boolean canHighlight(MappedFieldType field) {
        return true;
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public HighlightField highlight(FieldHighlightContext context) {
        try {
            CacheEntry entry = (CacheEntry) context.hitContext.cache().get(CACHE_KEY);
            if (entry == null) {
                entry = new CacheEntry();
                context.hitContext.cache().put(CACHE_KEY, entry);
            }
            HighlightExecutionContext executionContext = new HighlightExecutionContext(context, entry);
            try {
                return executionContext.highlight();
            } finally {
                executionContext.cleanup();
            }
        } catch (Exception e) {
            getLogger(context).error("Failed to highlight field [{}]", e, context.fieldName);
            throw new FetchPhaseExecutionException(context.hitContext.hit().getShard(), "Failed to highlight field [" + context.fieldName + "]", e);
        }
    }

    private Logger getLogger(FieldHighlightContext context) {
        return Loggers.getLogger(ExperimentalHighlighter.class, context.context.getIndexName());
    }

    static class CacheEntry {
        private final Map<QueryCacheKey, BasicQueryWeigher> queryWeighers = new HashMap<>();
        private Map<String, AutomatonHitEnum.Factory> automatonHitEnumFactories;
        private boolean lastMatched;
        private int lastDocId = -1;
    }

    static class QueryCacheKey {
        private final Query query;
        private final int maxExpandedTerms;
        private final boolean phraseAsTerms;
        private final boolean removeHighFrequencyTermsFromCommonTerms;

        QueryCacheKey(Query query, int maxExpandedTerms, boolean phraseAsTerms, boolean removeHighFrequencyTermsFromCommonTerms) {
            this.query = query;
            this.maxExpandedTerms = maxExpandedTerms;
            this.phraseAsTerms = phraseAsTerms;
            this.removeHighFrequencyTermsFromCommonTerms = removeHighFrequencyTermsFromCommonTerms;
        }

        @Override
        public int hashCode() {
            return Objects.hash(maxExpandedTerms, phraseAsTerms, removeHighFrequencyTermsFromCommonTerms, query);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            QueryCacheKey other = (QueryCacheKey) obj;
            return Objects.equals(maxExpandedTerms, other.maxExpandedTerms)
                    && Objects.equals(phraseAsTerms, other.phraseAsTerms)
                    && Objects.equals(removeHighFrequencyTermsFromCommonTerms, other.removeHighFrequencyTermsFromCommonTerms)
                    && Objects.equals(query, other.query);
        }
    }

    static class HighlightExecutionContext {
        private static final String OPTION_RETURN_DEBUG_GRAPH = "return_debug_graph";
        private static final String OPTION_RETURN_SNIPPETS_WITH_OFFSET = "return_snippets_and_offsets";
        private static final int DEFAULT_MAX_DETERMINIZED_STATES = 20000;
        private final FieldHighlightContext context;
        private final CacheEntry cache;
        private BasicQueryWeigher weigher;
        private FieldWrapper defaultField;
        private List<FieldWrapper> extraFields;
        private SegmenterFactory segmenterFactory;
        private DelayedSegmenter segmenter;
        private boolean scoreMatters;
        private Locale locale;
        private int maxDeterminizedStates;

        HighlightExecutionContext(FieldHighlightContext context, CacheEntry cache) {
            this.context = context;
            this.cache = cache;
        }

        HighlightField highlight() throws IOException {
            if (shouldSkip()) {
                return null;
            }

            // TODO it might be possible to not build the weigher at all if just
            // using regex highlighting
            ensureWeigher();
            scoreMatters = context.field.fieldOptions().scoreOrdered();
            if (!scoreMatters) {
                Boolean topScoring = (Boolean) getOption("top_scoring");
                scoreMatters = topScoring != null && topScoring;
            }
            defaultField = new FieldWrapper(this, context, weigher);
            int numberOfSnippets = context.field.fieldOptions().numberOfFragments();
            if (numberOfSnippets == 0) {
                numberOfSnippets = 1;
            }
            segmenter = new DelayedSegmenter(defaultField);
            List<Snippet> snippets = buildChooser().choose(segmenter, buildHitEnum(), numberOfSnippets);
            if (!snippets.isEmpty()) {
                cache.lastMatched = true;
                return new HighlightField(context.fieldName, formatSnippets(snippets));
            }
            cache.lastMatched = false;
            int noMatchSize = context.field.fieldOptions().noMatchSize();
            if (noMatchSize <= 0) {
                return null;
            }
            List<String> fieldValues = defaultField.getFieldValues();
            if (fieldValues.isEmpty()) {
                return null;
            }
            Text fragment = new Text(getSegmenterFactory().extractNoMatchFragment(fieldValues.get(0), noMatchSize));
            return new HighlightField(context.fieldName, new Text[] {fragment});
        }

        private boolean shouldSkip() {
            // Maintain lastMatched - it should be false if we shift to a new
            // doc.
            if (cache.lastDocId != context.hitContext.docId()) {
                cache.lastMatched = false;
                cache.lastDocId = context.hitContext.docId();
            }

            Boolean skipIfLastMatched = (Boolean) getOption("skip_if_last_matched");
            return skipIfLastMatched != null && skipIfLastMatched && cache.lastMatched;
        }

        @SuppressWarnings("checkstyle:IllegalCatch")
        // We might be able to improve this a bit with AutoClosable magic,
        // but not worth doing it unless we revisit that code.
        void cleanup() throws Exception {
            Exception lastCaught = null;
            try {
                if (defaultField != null) {
                    // If we throw an exception before defining default field
                    // then we can't clean it up!
                    defaultField.cleanup();
                }
            } catch (Exception e) {
                lastCaught = e;
            }
            if (extraFields != null) {
                for (FieldWrapper extra : extraFields) {
                    try {
                        extra.cleanup();
                    } catch (Exception e) {
                        if (lastCaught != null) {
                            e.addSuppressed(lastCaught);
                        }
                        lastCaught = e;
                    }
                }
            }
            if (lastCaught != null) {
                throw lastCaught;
            }
        }

        SegmenterFactory getSegmenterFactory() {
            if (segmenterFactory == null) {
                segmenterFactory = buildSegmenterFactory();
            }
            return segmenterFactory;
        }

        Object getOption(String key) {
            if (context.field.fieldOptions().options() == null) {
                return null;
            }
            return context.field.fieldOptions().options().get(key);
        }

        <T> T getOption(String key, T defaultValue) {
            @SuppressWarnings("unchecked")
            T value = (T) getOption(key);
            return value == null ? defaultValue : value;
        }

        boolean scoreMatters() {
            return scoreMatters;
        }

        private void ensureWeigher() {
            if (weigher != null) {
                return;
            }
            boolean phraseAsTerms = getOption("phrase_as_terms", FALSE);
            boolean removeHighFrequencyTermsFromCommonTerms = getOption("remove_high_freq_terms_from_common_terms", TRUE);
            int maxExpandedTerms = getOption("max_expanded_terms", 1024);
            // TODO simplify
            QueryCacheKey key = new QueryCacheKey(context.query, maxExpandedTerms, phraseAsTerms,
                    removeHighFrequencyTermsFromCommonTerms);
            weigher = cache.queryWeighers.get(key);
            if (weigher != null) {
                return;
            }
            // TODO recycle. But addReleasble doesn't seem to close it properly
            // later. I believe this is fixed in later Elasticsearch versions.
            BytesRefHashTermInfos infos = new BytesRefHashTermInfos(BigArrays.NON_RECYCLING_INSTANCE);
            // context.context.addReleasable(infos);
            weigher = new BasicQueryWeigher(new ElasticsearchQueryFlattener(maxExpandedTerms, phraseAsTerms,
                    removeHighFrequencyTermsFromCommonTerms), infos, context.hitContext.topLevelReader(), context.query);
            // Build the QueryWeigher with the top level reader to get all
            // the frequency information
            cache.queryWeighers.put(key, weigher);
        }

        /**
         * Builds the hit enum including any required wrappers.
         */
        private HitEnum buildHitEnum() throws IOException {
            HitEnum e = buildHitFindingHitEnum();

            // Merge any overlapping hits to support matched fields and
            // analyzers that make overlaps.
            e = new OverlapMergingHitEnumWrapper(e);

            if (getOption(OPTION_RETURN_DEBUG_GRAPH, FALSE)) {
                e = new GraphvizHitEnum(e);
            }
            return e;
        }

        private HitEnum buildHitFindingHitEnum() throws IOException {
            List<HitEnum> hitEnums = buildHitFindingHitEnums();
            switch (hitEnums.size()) {
            case 0:
                return EmptyHitEnum.INSTANCE;
            case 1:
                return hitEnums.get(0);
            default:
                return new MergingHitEnum(hitEnums, HitEnum.LessThans.OFFSETS);
            }
        }

        /**
         * Builds the HitEnum that actually finds the hits in the first place.
         */
        private List<HitEnum> buildHitFindingHitEnums() throws IOException {
            Boolean skipQuery = (Boolean) getOption("skip_query");
            List<HitEnum> hitEnums = buildRegexHitEnums();
            if (skipQuery == null || !skipQuery) {
                hitEnums.addAll(buildLuceneHitFindingHitEnums());
            }
            return hitEnums;
        }

        @SuppressWarnings("checkstyle:ModifiedControlVariable")
        // cleanup the re-assignment of `regex` if we revisit that code
        private List<HitEnum> buildRegexHitEnums() throws IOException {
            boolean luceneRegex = isLuceneRegexFlavor();
            if (luceneRegex) {
                cache.automatonHitEnumFactories = new HashMap<>();
            }
            Boolean caseInsensitiveOption = (Boolean) getOption("regex_case_insensitive");
            boolean caseInsensitive = caseInsensitiveOption == null ? false : caseInsensitiveOption;

            List<HitEnum> hitEnums = new ArrayList<>();
            List<String> fieldValues = defaultField.getFieldValues();
            if (fieldValues.isEmpty()) {
                return hitEnums;
            }

            for (String regex : getRegexes()) {
                if (luceneRegex) {
                    if (caseInsensitive) {
                        regex = regex.toLowerCase(getLocale());
                    }
                    AutomatonHitEnum.Factory factory = cache.automatonHitEnumFactories.get(regex);
                    if (factory == null) {
                        factory = buildFactoryForRegex(regex);
                        cache.automatonHitEnumFactories.put(regex, factory);
                    }
                    hitEnums.add(buildLuceneRegexHitEnumForRegex(factory, fieldValues, caseInsensitive));
                } else {
                    int options = 0;
                    if (caseInsensitive) {
                        options |= Pattern.CASE_INSENSITIVE;
                    }
                    hitEnums.add(buildJavaRegexHitEnumForRegex(Pattern.compile(regex, options), fieldValues));
                }
            }
            return hitEnums;
        }

        private AutomatonHitEnum.Factory buildFactoryForRegex(String regex) {
            return AutomatonHitEnum.factory(regex, getMaxDeterminizedStates());
        }

        private int getMaxDeterminizedStates() {
            if (maxDeterminizedStates != 0) {
                return maxDeterminizedStates;
            }
            Integer maxDeterminizedStates = (Integer) getOption("max_determinized_states");
            if (maxDeterminizedStates == null) {
                this.maxDeterminizedStates = DEFAULT_MAX_DETERMINIZED_STATES;
            } else {
                this.maxDeterminizedStates = maxDeterminizedStates;
            }
            return this.maxDeterminizedStates;
        }

        /**
         * Get the list of regexes to highlight or null if there aren't any.
         */
        @SuppressWarnings("unchecked")
        private List<String> getRegexes() {
            Object regexes = getOption("regex");
            if (regexes == null) {
                return Collections.emptyList();
            }
            if (regexes instanceof String) {
                return Collections.singletonList((String) regexes);
            }
            return (List<String>) regexes;
        }

        private HitEnum buildLuceneRegexHitEnumForRegex(final AutomatonHitEnum.Factory factory, List<String> fieldValues,
                final boolean caseInsensitive) {
            final int positionGap = defaultField.getPositionGap();
            if (fieldValues.size() == 1) {
                String fieldValue = fieldValues.get(0);
                if (caseInsensitive) {
                    fieldValue = fieldValue.toLowerCase(getLocale());
                }
                return factory.build(fieldValue);
            } else {
                Iterator<HitEnumAndLength> hitEnumsFromStreams = fieldValues.stream().map(fieldValue -> {
                    if (caseInsensitive) {
                        fieldValue = fieldValue.toLowerCase(getLocale());
                    }
                    return new HitEnumAndLength(factory.build(fieldValue), fieldValue.length());
                }).iterator();
                return new ConcatHitEnum(hitEnumsFromStreams, positionGap, 1);
            }
        }

        private HitEnum buildJavaRegexHitEnumForRegex(final Pattern pattern, List<String> fieldValues) {
            final int positionGap = defaultField.getPositionGap();
            if (fieldValues.size() == 1) {
                return new RegexHitEnum(pattern.matcher(fieldValues.get(0)));
            } else {
                Iterator<HitEnumAndLength> hitEnumsFromStreams = fieldValues.stream()
                        .map(fieldValue -> new HitEnumAndLength(new RegexHitEnum(pattern.matcher(fieldValue)), fieldValue.length()))
                        .iterator();
                return new ConcatHitEnum(hitEnumsFromStreams, positionGap, 1);
            }
        }

        private boolean isLuceneRegexFlavor() {
            Object regexFlavor = getOption("regex_flavor");
            if (regexFlavor == null || "lucene".equals(regexFlavor)) {
                return true;
            }
            if ("java".equals(regexFlavor)) {
                return false;
            }
            throw new IllegalArgumentException("Unknown regex flavor:  " + regexFlavor);
        }

        /**
         * Builds the HitEnum that finds the hits from Lucene.
         */
        private List<HitEnum> buildLuceneHitFindingHitEnums() throws IOException {
            Set<String> matchedFields = context.field.fieldOptions().matchedFields();
            if (matchedFields == null) {
                if (!defaultField.canProduceHits()) {
                    return Collections.emptyList();
                }
                return Collections.singletonList(defaultField.buildHitEnum());
            }
            List<HitEnum> hitEnums = new ArrayList<>(matchedFields.size());
            extraFields = new ArrayList<>(matchedFields.size());
            for (String field : matchedFields) {
                FieldWrapper wrapper;
                if (context.fieldName.equals(field)) {
                    wrapper = defaultField;
                } else {
                    wrapper = new FieldWrapper(this, context, weigher, field);
                    if (!wrapper.exists()) {
                        continue;
                    }
                }
                if (wrapper.canProduceHits()) {
                    hitEnums.add(wrapper.buildHitEnum());
                }
                extraFields.add(wrapper);
            }
            if (hitEnums.isEmpty()) {
                return Collections.emptyList();
            }
            return hitEnums;
        }

        private SnippetChooser buildChooser() {
            HitBuilder hitBuilder = Snippet.DEFAULT_HIT_BUILDER;
            if (getOption(OPTION_RETURN_DEBUG_GRAPH, FALSE)) {
                hitBuilder = GraphvizHit.GRAPHVIZ_HIT_BUILDER;
            }
            if (context.field.fieldOptions().scoreOrdered()) {
                return buildScoreBasedSnippetChooser(true, hitBuilder);
            }
            Boolean topScoring = (Boolean) getOption("top_scoring");
            if (topScoring != null && topScoring) {
                return buildScoreBasedSnippetChooser(false, hitBuilder);
            }
            return new BasicSourceOrderSnippetChooser(hitBuilder);
        }

        private SnippetChooser buildScoreBasedSnippetChooser(boolean scoreOrdered, HitBuilder hitBuilder) {
            Integer maxFragmentsScored = (Integer) getOption("max_fragments_scored");
            if (maxFragmentsScored == null) {
                maxFragmentsScored = Integer.MAX_VALUE;
            }
            return new BasicScoreBasedSnippetChooser(scoreOrdered, buildSnippetWeigher(), hitBuilder, maxFragmentsScored);
        }

        private SnippetWeigher buildSnippetWeigher() {
            float defaultBase = 1.1f;
            Object config = getOption("fragment_weigher");
            if (config == null) {
                return new ExponentialSnippetWeigher(defaultBase);
            }
            if (config.equals("sum")) {
                return new SumSnippetWeigher();
            }
            if (config.equals("exponential")) {
                return new ExponentialSnippetWeigher(defaultBase);
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) config;
                if (map.containsKey("sum")) {
                    return new SumSnippetWeigher();
                }
                Object exponentialConfig = map.get("exponential");
                if (exponentialConfig != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> exponentialConfigMap = (Map<String, Object>) exponentialConfig;
                    Number base = (Number) exponentialConfigMap.get("base");
                    if (base == null) {
                        return new ExponentialSnippetWeigher(defaultBase);
                    }
                    return new ExponentialSnippetWeigher(base.floatValue());
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Invalid snippet weigher config:  " + config, e);
            }
            throw new IllegalArgumentException("Invalid snippet weigher config:  " + config);
        }

        private Text[] formatSnippets(List<Snippet> snippets) throws IOException {
            final SnippetFormatter formatter;
            if (getOption("return_offsets", FALSE)) {
                formatter = new OffsetSnippetFormatter();
            } else if (getOption(OPTION_RETURN_DEBUG_GRAPH, FALSE)) {
                formatter = new GraphvizSnippetFormatter(defaultField.buildSourceExtracter());
            } else if (getOption(OPTION_RETURN_SNIPPETS_WITH_OFFSET, FALSE)) {
                formatter = new OffsetAugmenterSnippetFormatter(
                        new SnippetFormatter.Default(
                                defaultField.buildSourceExtracter(),
                                context.field.fieldOptions().preTags()[0],
                                context.field.fieldOptions().postTags()[0]));
            } else {
                formatter = new SnippetFormatter.Default(defaultField.buildSourceExtracter(), context.field.fieldOptions().preTags()[0],
                        context.field.fieldOptions().postTags()[0]);
            }

            List<FieldWrapper> fetchFields = buildFetchFields();
            if (fetchFields == null) {
                Text[] result = new Text[snippets.size()];
                int i = 0;
                for (Snippet snippet : snippets) {
                    result[i++] = new Text(formatter.format(snippet));
                }
                return result;
            }

            int fieldsPerSnippet = 1 + fetchFields.size();
            Text[] result = new Text[snippets.size() * fieldsPerSnippet];
            FetchedFieldIndexPicker picker = segmenter.buildFetchedFieldIndexPicker();
            int i = 0;
            for (Snippet snippet : snippets) {
                result[i++] = new Text(formatter.format(snippet));
                int index = picker.index(snippet);
                for (FieldWrapper fetchField : fetchFields) {
                    List<String> values = fetchField.getFieldValues();
                    if (index >= 0 && index < values.size()) {
                        result[i++] = new Text(values.get(index));
                    } else {
                        result[i++] = EMPTY_STRING;
                    }
                }
            }
            return result;
        }

        /**
         * Return FieldWrappers for all fetch_fields or null if there aren't
         * any.
         */
        private List<FieldWrapper> buildFetchFields() {
            @SuppressWarnings("unchecked")
            List<String> fetchFields = (List<String>) getOption("fetch_fields");
            if (fetchFields == null) {
                return null;
            }
            List<FieldWrapper> fetchFieldWrappers = new ArrayList<>(fetchFields.size());
            List<FieldWrapper> newExtraFields = new ArrayList<>();
            try {
                for (String fetchField : fetchFields) {
                    boolean found = false;
                    if (extraFields != null) {
                        for (FieldWrapper extraField : extraFields) {
                            if (extraField.fieldName().equals(fetchField)) {
                                fetchFieldWrappers.add(extraField);
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        FieldWrapper fieldWrapper = new FieldWrapper(this, context, weigher, fetchField);
                        newExtraFields.add(fieldWrapper);
                        fetchFieldWrappers.add(fieldWrapper);
                    }
                }
            } finally {
                if (extraFields == null) {
                    extraFields = newExtraFields;
                } else {
                    extraFields.addAll(newExtraFields);
                }
            }
            return fetchFieldWrappers;
        }

        private SegmenterFactory buildSegmenterFactory() {
            FieldOptions options = context.field.fieldOptions();
            if (options.numberOfFragments() == 0) {
                return new WholeSourceSegmenterFactory();
            }
            if (options.fragmenter() == null || options.fragmenter().equals("scan")) {
                // TODO boundaryChars
                return new CharScanningSegmenterFactory(options.fragmentCharSize(), options.boundaryMaxScan());
            }
            if (options.fragmenter().equals("sentence")) {
                return new SentenceIteratorSegmenterFactory(getLocale(), options.boundaryMaxScan());
            }
            if (options.fragmenter().equals("none")) {
                return new WholeSourceSegmenterFactory();
            }
            throw new IllegalArgumentException("Unknown fragmenter:  '" + options.fragmenter() + "'.  Options are 'scan' or 'sentence'.");
        }

        private Locale getLocale() {
            if (locale != null) {
                return locale;
            }
            String localeString = (String) getOption("locale");
            locale = localeString == null ? Locale.US : LocaleUtils.parse(localeString);
            return locale;
        }
    }
}
