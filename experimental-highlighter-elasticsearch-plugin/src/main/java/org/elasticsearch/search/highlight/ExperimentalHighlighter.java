package org.elasticsearch.search.highlight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.text.StringAndBytesText;
import org.elasticsearch.common.text.StringText;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.search.fetch.FetchPhaseExecutionException;
import org.elasticsearch.search.highlight.SearchContextHighlight.FieldOptions;
import org.wikimedia.highlighter.experimental.elasticsearch.BytesRefHashTermInfos;
import org.wikimedia.highlighter.experimental.elasticsearch.CharScanningSegmenterFactory;
import org.wikimedia.highlighter.experimental.elasticsearch.DelayedSegmenter;
import org.wikimedia.highlighter.experimental.elasticsearch.ElasticsearchQueryFlattener;
import org.wikimedia.highlighter.experimental.elasticsearch.FetchedFieldIndexPicker;
import org.wikimedia.highlighter.experimental.elasticsearch.SegmenterFactory;
import org.wikimedia.highlighter.experimental.elasticsearch.SentenceIteratorSegmenterFactory;
import org.wikimedia.highlighter.experimental.elasticsearch.WholeSourceSegmenterFactory;
import org.wikimedia.highlighter.experimental.lucene.hit.weight.BasicQueryWeigher;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.Snippet;
import org.wikimedia.search.highlighter.experimental.SnippetChooser;
import org.wikimedia.search.highlighter.experimental.SnippetFormatter;
import org.wikimedia.search.highlighter.experimental.SnippetWeigher;
import org.wikimedia.search.highlighter.experimental.hit.EmptyHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.MergingHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.OverlapMergingHitEnumWrapper;
import org.wikimedia.search.highlighter.experimental.snippet.BasicScoreBasedSnippetChooser;
import org.wikimedia.search.highlighter.experimental.snippet.BasicSourceOrderSnippetChooser;
import org.wikimedia.search.highlighter.experimental.snippet.ExponentialSnippetWeigher;
import org.wikimedia.search.highlighter.experimental.snippet.SumSnippetWeigher;

public class ExperimentalHighlighter implements Highlighter {
    private static final String CACHE_KEY = "highlight-experimental";
    private static final Text EMPTY_STRING = new StringAndBytesText("");

    @Override
    public String[] names() {
        return new String[] { "experimental" };
    }

    @Override
    public HighlightField highlight(HighlighterContext context) {
        try {
            CacheEntry entry = (CacheEntry) context.hitContext.cache().get(CACHE_KEY);
            if (entry == null) {
                entry = new CacheEntry();
                context.hitContext.cache().put(CACHE_KEY, entry);
            }
            boolean phraseAsTerms = false;
            if (context.field.fieldOptions().options() != null) {
                Boolean phraseAsTermsOption = (Boolean) context.field.fieldOptions().options()
                        .get("phrase_as_terms");
                if (phraseAsTermsOption != null) {
                    phraseAsTerms = phraseAsTermsOption;
                }
            }
            QueryCacheKey key = new QueryCacheKey(context.query.originalQuery(), phraseAsTerms);
            BasicQueryWeigher weigher = entry.queryWeighers.get(key);
            if (weigher == null) {
                // TODO recycle. But addReleasble doesn't seem to close it
                // properly later. I believe this is fixed in later
                // Elasticsearch versions.
                BytesRefHashTermInfos infos = new BytesRefHashTermInfos(BigArrays.NON_RECYCLING_INSTANCE);
//                context.context.addReleasable(infos);
                weigher = new BasicQueryWeigher(
                        new ElasticsearchQueryFlattener(100, phraseAsTerms), infos,
                        context.hitContext.topLevelReader(), context.query.originalQuery());
                // Build the QueryWeigher with the top level reader to get all
                // the frequency information
                entry.queryWeighers.put(key, weigher);
            }
            HighlightExecutionContext executionContext = new HighlightExecutionContext(context,
                    weigher);
            try {
                return executionContext.highlight();
            } finally {
                executionContext.cleanup();
            }
        } catch (Exception e) {
            throw new FetchPhaseExecutionException(context.context, "Failed to highlight field ["
                    + context.fieldName + "]", e);
        }
    }

    static class CacheEntry {
        private final Map<QueryCacheKey, BasicQueryWeigher> queryWeighers = new HashMap<QueryCacheKey, BasicQueryWeigher>();
    }

    static class QueryCacheKey {
        private final Query query;
        private final boolean phraseAsTerms;
        public QueryCacheKey(Query query, boolean phraseAsTerms) {
            this.query = query;
            this.phraseAsTerms = phraseAsTerms;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (phraseAsTerms ? 1231 : 1237);
            result = prime * result + ((query == null) ? 0 : query.hashCode());
            return result;
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
            if (phraseAsTerms != other.phraseAsTerms)
                return false;
            if (query == null) {
                if (other.query != null)
                    return false;
            } else if (!query.equals(other.query))
                return false;
            return true;
        }
    }

    static class HighlightExecutionContext {
        private final HighlighterContext context;
        private final BasicQueryWeigher weigher;
        private FieldWrapper defaultField;
        private List<FieldWrapper> extraFields;
        private SegmenterFactory segmenterFactory;
        private DelayedSegmenter segmenter;

        HighlightExecutionContext(HighlighterContext context, BasicQueryWeigher weigher) {
            this.context = context;
            this.weigher = weigher;
            defaultField = new FieldWrapper(this, context, weigher);
        }

        HighlightField highlight() throws IOException {
            int numberOfSnippets = context.field.fieldOptions().numberOfFragments();
            if (numberOfSnippets == 0) {
                numberOfSnippets = 1;
            }
            segmenter = new DelayedSegmenter(defaultField);
            List<Snippet> snippets = buildChooser().choose(segmenter, buildHitEnum(),
                    numberOfSnippets);
            if (snippets.size() != 0) {
                return new HighlightField(context.fieldName, formatSnippets(snippets));
            }
            int noMatchSize = context.field.fieldOptions().noMatchSize();
            if (noMatchSize <= 0) {
                return null;
            }
            List<String> fieldValues = defaultField.getFieldValues();
            if (fieldValues.isEmpty()) {
                return null;
            }
            Text fragment = new StringText(getSegmenterFactory()
                    .extractNoMatchFragment(fieldValues.get(0), noMatchSize));
            return new HighlightField(context.fieldName, new Text[] {fragment});
        }

        void cleanup() throws Exception {
            Exception lastCaught = null;
            try {
                defaultField.cleanup();
            } catch (Exception e) {
                lastCaught = e;
            }
            if (extraFields != null) {
                for (FieldWrapper extra : extraFields) {
                    try {
                        extra.cleanup();
                    } catch (Exception e) {
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

        /**
         * Builds the hit enum including any required wrappers.
         */
        private HitEnum buildHitEnum() throws IOException {
            HitEnum e = buildHitFindingHitEnum();

            // Merge any overlapping hits to support matched fields and
            // analyzers that make overlaps.
            e = new OverlapMergingHitEnumWrapper(e);
            return e;
        }

        /**
         * Builds the HitEnum that actually finds the hits in the first place.
         */
        private HitEnum buildHitFindingHitEnum() throws IOException {
            Set<String> matchedFields = context.field.fieldOptions().matchedFields();
            if (matchedFields == null) {
                if (!defaultField.canProduceHits()) {
                    return EmptyHitEnum.INSTANCE;
                }
                return defaultField.buildHitEnum();
            }
            List<HitEnum> toMerge = new ArrayList<HitEnum>(matchedFields.size());
            extraFields = new ArrayList<FieldWrapper>(matchedFields.size());
            for (String field : matchedFields) {
                FieldWrapper wrapper;
                if (context.fieldName.equals(field)) {
                    wrapper = defaultField;
                } else {
                    wrapper = new FieldWrapper(this, context, weigher, field);
                }
                if (wrapper.canProduceHits()) {
                    toMerge.add(wrapper.buildHitEnum());
                }
                extraFields.add(wrapper);
            }
            if (toMerge.size() == 0) {
                return EmptyHitEnum.INSTANCE;
            }
            if (toMerge.size() == 1) {
                return toMerge.get(0);
            }
            return new MergingHitEnum(toMerge, HitEnum.LessThans.OFFSETS);
        }

        private SnippetChooser buildChooser() {
            if (context.field.fieldOptions().scoreOrdered()) {
                return buildScoreBasedSnippetChooser(true);
            }
            Boolean topScoring = (Boolean) getOption("top_scoring");
            if (topScoring != null && topScoring) {
                return buildScoreBasedSnippetChooser(false);
            }
            return new BasicSourceOrderSnippetChooser();
        }

        private SnippetChooser buildScoreBasedSnippetChooser(boolean scoreOrdered) {
            Integer maxFragmentsScored = (Integer) getOption("max_fragments_scored");
            if (maxFragmentsScored == null) {
                maxFragmentsScored = Integer.MAX_VALUE;
            }
            return new BasicScoreBasedSnippetChooser(scoreOrdered, buildSnippetWeigher(), maxFragmentsScored);
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
            SnippetFormatter formatter = new SnippetFormatter(defaultField.buildSourceExtracter(),
                    context.field.fieldOptions().preTags()[0], context.field.fieldOptions()
                            .postTags()[0]);

            List<FieldWrapper> fetchFields = buildFetchFields();
            if (fetchFields == null) {
                Text[] result = new Text[snippets.size()];
                int i = 0;
                for (Snippet snippet : snippets) {
                    result[i++] = new StringText(formatter.format(snippet));
                }
                return result;
            }

            int fieldsPerSnippet = 1 + fetchFields.size();
            Text[] result = new Text[snippets.size() * fieldsPerSnippet];
            FetchedFieldIndexPicker picker = segmenter.buildFetchedFieldIndexPicker();
            int i = 0;
            for (Snippet snippet : snippets) {
                result[i++] = new StringText(formatter.format(snippet));
                int index = picker.index(snippet);
                for (FieldWrapper fetchField: fetchFields) {
                    List<String> values = fetchField.getFieldValues();
                    if (index >= 0 && index < values.size()) {
                        result[i++] = new StringText(values.get(index));
                    } else {
                        result[i++] = EMPTY_STRING;
                    }
                }
            }
            return result;
        }

        /**
         * Return FieldWrappers for all fetch_fields or null if there aren't any.
         */
        private List<FieldWrapper> buildFetchFields() {
            @SuppressWarnings("unchecked")
            List<String> fetchFields = (List<String>) getOption("fetch_fields");
            if (fetchFields == null) {
                return null;
            }
            List<FieldWrapper> fetchFieldWrappers = new ArrayList<FieldWrapper>(fetchFields.size());
            List<FieldWrapper> newExtraFields = new ArrayList<FieldWrapper>();
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
                        FieldWrapper fieldWrapper = new FieldWrapper(this, context, weigher,
                                fetchField);
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
                return new CharScanningSegmenterFactory(options.fragmentCharSize(),
                        options.boundaryMaxScan());
            }
            if (options.fragmenter().equals("sentence")) {
                String localeString = (String) getOption("locale");
                Locale locale;
                if (localeString == null) {
                    locale = Locale.US;
                } else {
                    locale = Strings.parseLocaleString(localeString);
                }
                return new SentenceIteratorSegmenterFactory(locale, options.boundaryMaxScan());
            }
            if (options.fragmenter().equals("none")) {
                return new WholeSourceSegmenterFactory();
            }
            throw new IllegalArgumentException("Unknown fragmenter:  '" + options.fragmenter()
                    + "'.  Options are 'scan' or 'sentence'.");
        }
    }
}
