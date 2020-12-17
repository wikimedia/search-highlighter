package org.wikimedia.highlighter.experimental.elasticsearch;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.plugins.AnalysisPlugin.requiresAnalysisSettings;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.KStemFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.StemmerOverrideFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.pattern.PatternTokenizer;
import org.elasticsearch.Version;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.Booleans;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.analysis.AbstractTokenizerFactory;
import org.elasticsearch.index.analysis.Analysis;
import org.elasticsearch.index.analysis.AnalyzerProvider;
import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.NormalizingTokenFilterFactory;
import org.elasticsearch.index.analysis.PreConfiguredTokenizer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.wikimedia.highlighter.experimental.elasticsearch.plugin.ExperimentalHighlighterPlugin;

import com.google.common.collect.ImmutableList;

//import org.elasticsearch.plugin.analysis.icu.AnalysisICUPlugin;

@SuppressWarnings("checkstyle:classfanoutcomplexity") // do not care too much about complexity of test classes
@ClusterScope(scope = ESIntegTestCase.Scope.SUITE, transportClientRatio = 0.0)
public abstract class AbstractExperimentalHighlighterIntegrationTestBase extends
ESIntegTestCase {
    protected static final List<String> HIT_SOURCES = ImmutableList.of("postings", "vectors",
            "analyze");

    protected HighlightBuilder newHLBuilder() {
        return new HighlightBuilder()
                .highlighterType("experimental")
                .field("test");
    }

    protected SearchRequestBuilder testSearch() {
        return testSearch(termQuery("test", "test"), null);
    }

    /**
     * A simple search for the term "test".
     */
    protected SearchRequestBuilder testSearch(Consumer<HighlightBuilder> func) {
        return testSearch(termQuery("test", "test"), func);
    }

    protected SearchRequestBuilder testSearch(QueryBuilder builder) {
        return testSearch(builder, null);
    }

    protected Consumer<HighlightBuilder> field(HighlightBuilder.Field field) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                hb.field(field);
            }
        };
    }

    protected Consumer<HighlightBuilder> field(String field) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                hb.field(field);
            }
        };
    }

    protected Consumer<HighlightBuilder> order(String order) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                hb.order(order);
            }
        };
    }

    protected Consumer<HighlightBuilder> options(Map<String, Object> options) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                if (hb.options() == null) {
                    hb.options(new HashMap<>());
                }
                hb.options().putAll(options);
            }
        };
    }

    protected Consumer<HighlightBuilder> fragmentSize(Integer size) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                hb.fragmentSize(size);
            }
        };
    }

    protected Consumer<HighlightBuilder> option(String name, Object value) {
        return new Consumer<HighlightBuilder>() {
            @Override
            public void accept(HighlightBuilder hb) {
                if (hb.options() == null) {
                    hb.options(new HashMap<>());
                }
                hb.options().put(name, value);
            }
        };
    }

    protected Consumer<HighlightBuilder> hitSource(String hitSource) {
        return option("hit_source", hitSource);
    }
    /**
     * A simple search for the term test.
     */
    protected SearchRequestBuilder testSearch(QueryBuilder builder, Consumer<HighlightBuilder> func) {
        HighlightBuilder hbuilder = newHLBuilder();
        if (func != null) {
            func.accept(hbuilder);
        }

        return client().prepareSearch("test").setQuery(builder)
                .highlighter(hbuilder)
                .setSize(1);
    }

    protected void buildIndex() throws IOException {
        buildIndex(true, true, between(1, 5));
    }

    protected void buildIndex(boolean offsetsInPostings, boolean fvhLikeTermVectors, int shards)
            throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject();
        mapping.startObject("properties");
        mapping.startObject("bar").field("type", "integer").endObject();
        addField(mapping, "custom_all", offsetsInPostings, fvhLikeTermVectors, false);
        addField(mapping, "test", offsetsInPostings, fvhLikeTermVectors, true);
        addField(mapping, "test2", offsetsInPostings, fvhLikeTermVectors, true);
        mapping.startObject("keyword_field")
                .field("type").value("keyword")
                .field("normalizer").value("asciifolding_normalizer")
            .endObject();
        mapping.startObject("pos_gap_big")
                .field("type").value("text")
                .field("position_increment_gap", 1000)
                .field("index_options", "offsets")
                .field("term_vector", "with_positions_offsets")
            .endObject();
        mapping.startObject("pos_gap_small")
                .field("type").value("text")
                .field("position_increment_gap", 0)
                .field("index_options", "offsets")
                .field("term_vector", "with_positions_offsets")
            .endObject();
        mapping.startObject("foo").field("type").value("object").startObject("properties");
        addField(mapping, "test", offsetsInPostings, fvhLikeTermVectors, true);
        mapping.endObject().endObject().endObject().endObject();

        XContentBuilder settings = jsonBuilder().startObject().startObject("index");
        settings.field("number_of_shards", shards);
        settings.startObject("analysis");
        settings.startObject("analyzer");
        {
            settings.startObject("chars").field("tokenizer", "chars").endObject();
            /*
             * This is a clone of the English analyzer cirrus uses that we can
             * use to run down errors that come up in CirrusSearch.
             */
            settings.startObject("cirrus_english");
            {
                settings.field("tokenizer", "standard");
                settings.array("filter", //
                        "aggressive_splitting", //
                        "possessive_english", //
                        //"icu_normalizer", //
                        "stop", //
                        "kstem", //
                        "custom_stem", //
                        "asciifolding_preserve" //
                        );
                settings.array("char_filter", "word_break_helper");
            }
            settings.endObject();
        }
        settings.endObject();
        settings.startObject("tokenizer");
        {
            settings.startObject("chars");
            {
                settings.field("type", "pattern");
                settings.field("pattern", "(.)");
                settings.field("group", 0);
            }
            settings.endObject();
        }
        settings.endObject();
        settings.startObject("filter");
        {
            settings.startObject("possessive_english");
            {
                settings.field("type", "stemmer_possessive_english");
            }
            settings.endObject();
            settings.startObject("aggressive_splitting");
            {
                settings.field("type", "word_delimiter_graph");
                settings.field("stem_english_possessive", "false");
                settings.field("preserve_original", "false");
            }
            settings.endObject();
            settings.startObject("custom_stem");
            {
                settings.field("type", "stemmer_override");
                settings.field("rules", "guidelines => guideline");
            }
            settings.endObject();
            settings.startObject("asciifolding_preserve");
            {
                settings.field("type", "asciifolding");
                settings.field("preserve_original", "true");
            }
            settings.endObject();
            settings.startObject("asciifolding_nopreserve");
            {
                settings.field("type", "asciifolding");
                settings.field("preserve_original", "false");
            }
            settings.endObject();
            /*
            settings.startObject("icu_normalizer");
            {
                settings.field("type", "icu_normalizer");
                settings.field("name", "nfkc_cf");
            }
            settings.endObject();
            */
        }
        settings.endObject();
        settings.startObject("char_filter");
        {
            settings.startObject("word_break_helper");
            {
                settings.field("type", "mapping");
                settings.array("mappings", //
                        "_=>\\u0020", //
                        ".=>\\u0020", //
                        "(=>\\u0020", //
                        ")=>\\u0020");
            }
            settings.endObject();
        }
        settings.endObject();
        settings.startObject("normalizer")
                .startObject("asciifolding_normalizer")
                .field("type", "custom")
                .array("filter", "asciifolding_nopreserve")
                .endObject()
                .endObject();

        settings.endObject();
        settings.endObject();
        settings.endObject();
        assertAcked(prepareCreate("test").setSettings(settings).addMapping("_doc", mapping));
        ensureYellow();
    }

    private void addField(XContentBuilder builder, String name, boolean offsetsInPostings,
            boolean fvhLikeTermVectors, boolean includeInAll) throws IOException {
        builder.startObject(name).field("type", "text");
        addProperties(builder, offsetsInPostings, fvhLikeTermVectors);
        if (includeInAll) {
            builder.field("copy_to", "custom_all");
        }
        builder.startObject("fields");
        addSubField(builder, "whitespace", "whitespace", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "english", "english", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "english2", "english", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "cirrus_english", "cirrus_english", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "chars", "chars", offsetsInPostings, fvhLikeTermVectors);

        builder.endObject().endObject();
    }

    private void addSubField(XContentBuilder builder, String name, String analyzer,
            boolean offsetsInPostings, boolean fvhLikeTermVectors) throws IOException {
        builder.startObject(name);
        builder.field("type", "text");
        builder.field("analyzer", analyzer);
        addProperties(builder, offsetsInPostings, fvhLikeTermVectors);
        builder.endObject();
    }

    private void addProperties(XContentBuilder builder, boolean offsetsInPostings,
            boolean fvhLikeTermVectors) throws IOException {
        if (offsetsInPostings) {
            builder.field("index_options", "offsets");
        }
        if (fvhLikeTermVectors) {
            builder.field("term_vector", "with_positions_offsets");
        }
    }

    protected void indexTestData() {
        indexTestData("tests very simple test");
    }

    protected void indexTestData(Object contents) {
        client().prepareIndex("test", "_doc", "1").setSource("test", contents).get();
        refresh();
    }

    /**
     * Enable plugin loading.
     */
    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.unmodifiableCollection(
                Arrays.asList(ExperimentalHighlighterPlugin.class,
                        MockPlugin.class
                ));
    }

    /**
     * Replicate some analysis components that were moved to a plugin.
     * @see <a href="https://github.com/elastic/elasticsearch/blob/master/modules/analysis-common">analysis-common.java</a>}
     */
    public static class MockPlugin extends Plugin implements AnalysisPlugin {
        @Override
        public Map<String, AnalysisModule.AnalysisProvider<CharFilterFactory>> getCharFilters() {
            Map<String, AnalysisModule.AnalysisProvider<CharFilterFactory>> map = new HashMap<>();
            // org/elasticsearch/analysis/common/MappingCharFilterFactory.java
            map.put("mapping", (isettings, env, name, settings) -> {

                Pattern p = Pattern.compile("^(.*?)=>(.*)$");

                NormalizeCharMap.Builder builder = new NormalizeCharMap.Builder();
                List<String> patterns = settings.getAsList("mappings");
                for (String pattern : patterns) {
                    Matcher m = p.matcher(pattern);
                    if (!m.find()) {
                        throw new IllegalArgumentException("Invalid pattern [" + pattern + "]");
                    }
                    builder.add(m.group(1), m.group(2));
                }

                final NormalizeCharMap charMap = builder.build();


                return new CharFilterFactory() {
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public Reader create(Reader reader) {
                        return new MappingCharFilter(charMap, reader);
                    }
                };
            });
            return Collections.unmodifiableMap(map);
        }

        @Override
        public Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
            Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> map = new HashMap<>();
            // org/elasticsearch/analysis/common/ASCIIFoldingTokenFilterFactory.java
            map.put("asciifolding", requiresAnalysisSettings(ASCIIFoldingTokenFilterFactory::new));
            // org/elasticsearch/analysis/common/KStemTokenFilterFactory.java
            map.put("kstem", (isettings, env, name, settings) -> {
                return new TokenFilterFactory() {
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public TokenStream create(TokenStream tokenStream) {
                        return new KStemFilter(tokenStream);
                    }
                };
            });
            // org/elasticsearch/analysis/common/WordDelimiterGraphTokenFilterFactory.java
            map.put("word_delimiter_graph", requiresAnalysisSettings((isettings, env, name, settings) -> {
                int wflags = 0;
                // If set, causes parts of words to be generated: "PowerShot" => "Power" "Shot"
                wflags |= getFlag(WordDelimiterGraphFilter.GENERATE_WORD_PARTS, settings, "generate_word_parts", true);
                // If set, causes number subwords to be generated: "500-42" => "500" "42"
                wflags |= getFlag(WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS, settings, "generate_number_parts", true);
                // 1, causes maximum runs of word parts to be catenated: "wi-fi" => "wifi"
                wflags |= getFlag(WordDelimiterGraphFilter.CATENATE_WORDS, settings, "catenate_words", false);
                // If set, causes maximum runs of number parts to be catenated: "500-42" => "50042"
                wflags |= getFlag(WordDelimiterGraphFilter.CATENATE_NUMBERS, settings, "catenate_numbers", false);
                // If set, causes all subword parts to be catenated: "wi-fi-4000" => "wifi4000"
                wflags |= getFlag(WordDelimiterGraphFilter.CATENATE_ALL, settings, "catenate_all", false);
                // 1, causes "PowerShot" to be two tokens; ("Power-Shot" remains two parts regards)
                wflags |= getFlag(WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE, settings, "split_on_case_change", true);
                // If set, includes original words in subwords: "500-42" => "500" "42" "500-42"
                wflags |= getFlag(WordDelimiterGraphFilter.PRESERVE_ORIGINAL, settings, "preserve_original", false);
                // 1, causes "j2se" to be three tokens; "j" "2" "se"
                wflags |= getFlag(WordDelimiterGraphFilter.SPLIT_ON_NUMERICS, settings, "split_on_numerics", true);
                // If set, causes trailing "'s" to be removed for each subword: "O'Neil's" => "O", "Neil"
                wflags |= getFlag(WordDelimiterGraphFilter.STEM_ENGLISH_POSSESSIVE, settings, "stem_english_possessive", true);
                // If not null is the set of tokens to protect from being delimited
                Set<?> protectedWords = Analysis.getWordSet(env, settings, "protected_words");
                final CharArraySet protoWords = protectedWords == null ? null : CharArraySet.copy(protectedWords);
                final int flags = wflags;

                return new TokenFilterFactory() {
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public TokenStream create(TokenStream tokenStream) {
                        return new WordDelimiterGraphFilter(tokenStream, flags, protoWords);
                    }
                };
            }));
            // org/elasticsearch/analysis/common/StemmerTokenFilterFactory.java#L138
            map.put("stemmer_possessive_english", (isettings, env, name, settings) -> {
                return new TokenFilterFactory() {
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public TokenStream create(TokenStream tokenStream) {
                        return new EnglishPossessiveFilter(tokenStream);
                    }
                };
            });
            // org/elasticsearch/analysis/common/StemmerOverrideTokenFilterFactory.java
            map.put("stemmer_override", requiresAnalysisSettings((isettings, env, name, settings) -> {
                List<String> rules = Analysis.getWordList(env, settings, "rules");
                if (rules == null) {
                    throw new IllegalArgumentException("stemmer override filter requires either `rules` or `rules_path` to be configured");
                }

                StemmerOverrideFilter.Builder builder = new StemmerOverrideFilter.Builder(false);
                parseRules(rules, builder, "=>");
                final StemmerOverrideFilter.StemmerOverrideMap overrideMap = builder.build();
                return new TokenFilterFactory() {
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public TokenStream create(TokenStream tokenStream) {
                        return new StemmerOverrideFilter(tokenStream, overrideMap);
                    }
                };
            }));
            return Collections.unmodifiableMap(map);
        }

        @Override
        public Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> getTokenizers() {
            return Collections.singletonMap("pattern",
                requiresAnalysisSettings((isettings, env, name, settings) -> new AbstractTokenizerFactory(isettings, settings, name) {
                    @Override
                    public Tokenizer create() {
                        String sPattern = settings.get("pattern", "\\W+" /*PatternAnalyzer.NON_WORD_PATTERN*/);
                        if (sPattern == null) {
                            throw new IllegalArgumentException("pattern is missing for [" + name + "] tokenizer of type 'pattern'");
                        }

                        Pattern pattern = Regex.compile(sPattern, settings.get("flags"));
                        int group = settings.getAsInt("group", -1);

                        return new PatternTokenizer(pattern, group);
                    }
                })
            );
        }

        @Override
        public List<PreConfiguredTokenizer> getPreConfiguredTokenizers() {
            return Collections.singletonList(PreConfiguredTokenizer.singleton("keyword", KeywordTokenizer::new));
        }

        @Override
        public Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> getAnalyzers() {
            return Collections.singletonMap("english",
                    (isettings, env, name, settings) -> new AbstractIndexAnalyzerProvider<Analyzer>(isettings, name, settings) {
                        @Override
                        public Analyzer get() {
                            return new EnglishAnalyzer(
                                    Analysis.parseStopWords(env, settings, EnglishAnalyzer.getDefaultStopSet()),
                                    Analysis.parseStemExclusion(settings, CharArraySet.EMPTY_SET));
                        }
                    });
        }

        private int getFlag(int flag, Settings settings, String key, boolean defaultValue) {
            if (settings.getAsBoolean(key, defaultValue)) {
                return flag;
            }
            return 0;
        }

        static void parseRules(List<String> rules, StemmerOverrideFilter.Builder builder, String mappingSep) {
            for (String rule : rules) {
                String key;
                String override;
                List<String> mapping = Strings.splitSmart(rule, mappingSep, false);
                if (mapping.size() == 2) {
                    key = mapping.get(0).trim();
                    override = mapping.get(1).trim();
                } else {
                    throw new RuntimeException("Invalid Keyword override Rule:" + rule);
                }

                if (key.isEmpty() || override.isEmpty()) {
                    throw new RuntimeException("Invalid Keyword override Rule:" + rule);
                } else {
                    builder.add(key, override);
                }
            }
        }
    }

    static class ASCIIFoldingTokenFilterFactory extends AbstractTokenFilterFactory implements NormalizingTokenFilterFactory {
        static final ParseField PRESERVE_ORIGINAL = new ParseField("preserve_original");
        static final boolean DEFAULT_PRESERVE_ORIGINAL = false;
        private final boolean preserveOriginal;
        ASCIIFoldingTokenFilterFactory(IndexSettings indexSettings, Environment environment,
                                              String name, Settings settings) {
            super(indexSettings, name, settings);
            if (indexSettings.getIndexVersionCreated().before(Version.V_6_0_0_alpha1)) {
                //Only emit a warning if the setting's value is not a proper boolean
                final String value = settings.get(PRESERVE_ORIGINAL.getPreferredName(), "false");
                if (!Booleans.isBoolean(value)) {
                    @SuppressWarnings("deprecation")
                    boolean convertedValue = Booleans.parseBooleanLenient(settings.get(PRESERVE_ORIGINAL.getPreferredName()), DEFAULT_PRESERVE_ORIGINAL);
                    deprecationLogger.deprecate("The value [{}] of setting [{}] is not coerced into boolean anymore. Please change " +
                            "this value to [{}].", value, PRESERVE_ORIGINAL.getPreferredName(), String.valueOf(convertedValue));
                    preserveOriginal =  convertedValue;
                } else {
                    preserveOriginal = settings.getAsBoolean(PRESERVE_ORIGINAL.getPreferredName(), DEFAULT_PRESERVE_ORIGINAL);
                }
            } else {
                preserveOriginal = settings.getAsBoolean(PRESERVE_ORIGINAL.getPreferredName(), DEFAULT_PRESERVE_ORIGINAL);
            }
        }

        @Override
        public TokenStream create(TokenStream tokenStream) {
            return new ASCIIFoldingFilter(tokenStream, preserveOriginal);
        }

    }

}
