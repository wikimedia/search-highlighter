package org.wikimedia.highlighter.experimental.elasticsearch;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.fuzzyQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchPhrasePrefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.queryString;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.regexpQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanFirstQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanNearQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanNotQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanOrQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanTermQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.index.query.QueryBuilders.wrapperQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNotHighlighted;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.admin.indices.optimize.OptimizeResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

import com.carrotsearch.ant.tasks.junit4.dependencies.com.google.common.base.Charsets;
import com.google.common.io.Resources;


public class ExperimentalHighlighterTest extends ElasticsearchIntegrationTest {
    private static final List<String> HIT_SOURCES = ImmutableList.of("postings", "vectors",
            "analyze");

    @Test
    public void singleTermQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch();
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void boolOfTermQueries() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(boolQuery().must(termQuery("test", "test")).must(
                termQuery("test", "simple")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests very <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void singlePhraseQuery() throws IOException {
        buildIndex();
        indexTestData("test very simple test");

        Map<String, Object> options = new HashMap<String, Object>();
        SearchRequestBuilder search = testSearch(matchPhraseQuery("test", "simple test")).setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("test very <em>simple</em> <em>test</em>"));
        }

        options.put("phrase_as_terms", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>test</em> very <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void phraseAsTermsSwitch() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test", "1").setSource("test", "phrase test test", "test2", "phrase phrase test").get();
        refresh();

        Map<String, Object> options = new HashMap<String, Object>();
        SearchRequestBuilder search = testSearch(matchPhraseQuery("test", "phrase test"))
                .addHighlightedField(new HighlightBuilder.Field("test2").options(options));
        options.put("phrase_as_terms", true);
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>phrase</em> <em>test</em> test"));
            assertHighlight(response, 0, "test2", 0,
                    equalTo("<em>phrase</em> <em>phrase</em> <em>test</em>"));
        }
    }

    /**
     * Makes sure we skip fields if the query is just phrases and none of them are on the field.  Causes tons of false negatives....
     */
//    @Test
    public void phraseQueryNoPossiblePhrasesSpeed() throws IOException {
        buildIndex();
        // Size has to be big enough to make it really expensive to highlight
        // the field to exaggerate the issue
        int size = 100000;
        int iterations = 100;
        StringBuilder b = new StringBuilder(5 * size);
        for (int i = 0; i < size; i++) {
            b.append("test ");
        }
        client().prepareIndex("test", "test", "1").setSource("test", b.toString(), "test2", "simple test").get();
        refresh();

        SearchRequestBuilder search = setHitSource(testSearch(matchPhraseQuery("test2", "simple test")), "analyze");
        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            SearchResponse response = search.get();
            assertNotHighlighted(response, 0, "test");
        }
        long total = System.currentTimeMillis() - start;
        // Without the optimization this runs about 7 seconds, with, about 1.8.
        assertThat(total, lessThan(3000l));

        search.addHighlightedField(new HighlightBuilder.Field("test2").matchedFields("test.english", "test.whitespace"));
        start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            SearchResponse response = search.get();
            assertNotHighlighted(response, 0, "test");
        }
        total = System.currentTimeMillis() - start;
        // Without the optimization this runs about 7 seconds, with, about 1.8.
        assertThat(total, lessThan(3000l));

        search.addHighlightedField(new HighlightBuilder.Field("test2").matchedFields("test", "test2.whitespace"));
        start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            assertNoFailures(search.get());
        }
        total = System.currentTimeMillis() - start;
        // Without the optimization this runs about 7 seconds, with, about 1.8.
        assertThat(total, lessThan(3000l));
    }

    @Test
    public void phraseWithSeparations() throws IOException {
        buildIndex();
        indexTestData("simple test very simple stuff stuff stuff test");

        SearchRequestBuilder search = testSearch(matchPhraseQuery("test", "simple test"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>simple</em> <em>test</em> very simple stuff stuff stuff test"));
        }

        search = testSearch(matchPhraseQuery("test", "simple test").slop(3));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>simple</em> <em>test</em> very <em>simple</em> stuff stuff stuff <em>test</em>"));
        }
    }

    @Test
    public void singlePhrasePrefixQuery() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<String, Object>();
        SearchRequestBuilder search = testSearch(matchPhrasePrefixQuery("test", "simple te")).setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests very <em>simple</em> <em>test</em>"));
        }

        options.put("phrase_as_terms", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void doublePhraseQuery() throws IOException {
        buildIndex();
        indexTestData("test very simple test double");

        Map<String, Object> options = new HashMap<String, Object>();
        SearchRequestBuilder search = testSearch(
                boolQuery().should(matchPhraseQuery("test", "simple test")).should(
                        matchPhraseQuery("test", "test double"))).setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("test very <em>simple</em> <em>test</em> <em>double</em>"));
        }

        options.put("phrase_as_terms", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>test</em> very <em>simple</em> <em>test</em> <em>double</em>"));
        }
    }

    @Test
    public void termAndPhraseQuery() throws IOException {
        buildIndex();
        indexTestData("test very simple test double");

        Map<String, Object> options = new HashMap<String, Object>();
        SearchRequestBuilder search = testSearch(
                boolQuery().should(matchPhraseQuery("test", "simple test")).should(
                        termQuery("test", "test"))).setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>test</em> very <em>simple</em> <em>test</em> double"));
        }

        options.put("phrase_as_terms", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>test</em> very <em>simple</em> <em>test</em> double"));
        }
    }

    /**
     * Even without require_field_match phrases must be restricted to the field
     * on which they are defined. If they aren't then you can get some really
     * confusing false matches.
     */
    @Test
    public void phraseQueryOnJustOneOfTwoMatchedFields() throws IOException {
        buildIndex();
        indexTestData("Blah blah blah video games blah blah blah.  video game blah blah.");

        HighlightBuilder.Field field = new HighlightBuilder.Field("test").matchedFields("test",
                "test.english");
        SearchRequestBuilder search = testSearch(matchPhraseQuery("test", "video game"))
                .addHighlightedField(field);
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("Blah blah blah video games blah blah blah.  <em>video</em> <em>game</em> blah blah."));
        }

        search = testSearch(matchPhraseQuery("test.english", "video game"))
                .addHighlightedField(field);
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("Blah blah blah <em>video</em> <em>games</em> blah blah blah.  <em>video</em> <em>game</em> blah blah."));
        }
    }

    /**
     * Make sure term queries don't overwhelm phrase queries in the presence of
     * boosts.
     */
    @Test
    public void termAndPhraseQueryWeightsAndDifferentFields() throws IOException {
        buildIndex();
        indexTestData(new Object[] { "test very simple foo", "test test" });
        SearchRequestBuilder search = testSearch(
                boolQuery().should(matchPhraseQuery("test", "simple foo"))
                        .should(termQuery("test", "test"))
                        .should(termQuery("fake", "test").boost(1000f))
                        .should(matchPhraseQuery("test", "simple foo").boost(1000f))).setHighlighterOrder(
                "score");
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>test</em> very <em>simple</em> <em>foo</em>"));
        }
    }

    /**
     * Checks that phrase query filtering works in the presence of matched
     * fields with very different tokenizers.
     */
    @Test
    public void matchedFieldsPhraseQuery() throws IOException {
        buildIndex();
        indexTestData("test very simple test");

        Map<String, Object> options = new HashMap<String, Object>();
        SearchRequestBuilder search = testSearch(matchPhraseQuery("test", "simple test"))
                .setHighlighterOptions(options).addHighlightedField(
                        new HighlightBuilder.Field("test").matchedFields("test", "test.chars"));
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("test very <em>simple</em> <em>test</em>"));
        }

        options.put("phrase_as_terms", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>test</em> very <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void singleFuzzyQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(fuzzyQuery("test", "test"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void mixOfAutomataAndNotQueries() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(boolQuery().should(fuzzyQuery("test", "test"))
                .should(fuzzyQuery("test", "simpl")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very <em>simple</em> <em>test</em>"));
        }

        search = testSearch(boolQuery().should(fuzzyQuery("test", "test"))
                .should(termQuery("test", "simple")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very <em>simple</em> <em>test</em>"));
        }

        search = testSearch(boolQuery().should(fuzzyQuery("test", "test"))
                .should(termQuery("test", "simple")).should(termQuery("test", "very")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> <em>very</em> <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void singleRangeQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(rangeQuery("test").from("teso").to("tesz"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleWildcardQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(wildcardQuery("test", "te?t"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }

        search = testSearch(wildcardQuery("test", "te*"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleRegexpQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(regexpQuery("test", "tests?"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanTermQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(spanTermQuery("test", "test"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanFirstQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(spanFirstQuery(spanTermQuery("test", "test"), 5));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanNearQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(spanNearQuery().slop(5)
                .clause(spanTermQuery("test", "tests")).clause(spanTermQuery("test", "test")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanNotQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(spanNotQuery().include(
                spanTermQuery("test", "test")).exclude(spanTermQuery("test", "tests")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanOrQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(spanOrQuery()
                .clause(spanTermQuery("test", "test")).clause(spanTermQuery("test", "tests")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleRegex() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "v.ry");
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> simple <em>test</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> simple test"));
        }
    }

    @Test
    public void multipleRegexes() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", ImmutableList.of("v.ry", "simple"));
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> <em>simple</em> <em>test</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> <em>simple</em> test"));
        }
    }

    @Test
    public void multipleRegexesMultipleValues() throws IOException {
        buildIndex();
        indexTestData(ImmutableList.of("tests very simple test", "simple"));

        Map<String, Object> options = new HashMap<>();
        options.put("regex", ImmutableList.of("v.ry", "si.*le"));
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> <em>simple</em> <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("<em>simple</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> <em>simple</em> test"));
            assertHighlight(response, 0, "test", 1, equalTo("<em>simple</em>"));
        }
    }

    @Test
    public void javaRegex() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "v\\wry");
        options.put("regex_flavor", "java");
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> simple <em>test</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            // The hit source shouldn't matter, but just in case
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> simple test"));
        }
    }

    @Test
    public void unboundedRegex() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "v.*");
        options.put("skip_query", true);
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        SearchResponse response = search.get();
        assertHighlight(response, 0, "test", 0, equalTo("tests <em>very simple test</em>"));
    }

    @Test
    public void insenitiveRegex() throws IOException {
        buildIndex();
        indexTestData("TEsts VEry simPLE tesT");

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "verY");
        options.put("skip_query", true);
        options.put("locale", "en_US");
        options.put("regex_case_insensitive", true);
        for (String flavor: new String[] {"java", "lucene"}) {
            options.put("regex_flavor", flavor);
            SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("TEsts <em>VEry</em> simPLE tesT"));
        }
    }

    @Test
    public void insenitiveManyRegex() throws IOException {
        buildIndex();
        indexTestData("TEsts VEry simPLE tesT");

        Map<String, Object> options = new HashMap<>();
        options.put("regex", ImmutableList.of("verY", "teSt.?"));
        options.put("skip_query", true);
        options.put("locale", "en_US");
        options.put("regex_case_insensitive", true);
        for (String flavor: new String[] {"java", "lucene"}) {
            options.put("regex_flavor", flavor);
            SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>TEsts</em> <em>VEry</em> simPLE <em>tesT</em>"));
        }
    }

    @Test
    public void multiValued() throws IOException {
        buildIndex();
        indexTestData(new String[] { "tests very simple test", "with two fields to test" });
        client().prepareIndex("test", "test", "2")
            .setSource("test", new String[] {"no match here", "this one"}, "fetched", new Integer[] {0, 1}).get();
        client().prepareIndex("test", "test", "3")
            .setSource("test", new String[] {"sentences.", "two sentences."}, "fetched", new Integer[] {0, 1}).get();
        refresh();

        SearchRequestBuilder search = testSearch();
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("with two fields to <em>test</em>"));
        }

        search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test.english"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("with two fields to <em>test</em>"));
        }

        search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test.english").order("score"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("with two fields to <em>test</em>"));
        }

        search = testSearch(termQuery("test", "one"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("this <em>one</em>"));
        }

        search = testSearch(termQuery("test", "this"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>this</em> one"));
        }

        search = testSearch(termQuery("test", "sentences"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>sentences</em>."));
            assertHighlight(response, 0, "test", 1, equalTo("two <em>sentences</em>."));
        }
    }

    @Test
    public void sentenceFragmenter() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test sentence.");

        SearchRequestBuilder search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(3));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("And some other <em>test</em> sentence."));
        }
    }

    @Test
    public void noneFragmenter() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test sentence.");

        SearchRequestBuilder search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").fragmenter("none").numOfFragments(3));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "
                            + "And some other <em>test</em> sentence."));
        }
    }

    @Test
    public void ordering() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test test.  " +
                "Junk junk junk junk junk junk junk junk junk junk junk test test test.");

        SearchRequestBuilder search = testSearch(
                boolQuery().should(termQuery("test", "test")).should(termQuery("test", "foo")))
                .addHighlightedField(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                .setHighlighterOrder("score");
        Map<String, Object> options = new HashMap<String, Object>();
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("Junk junk junk junk junk junk junk "
                    + "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
            assertHighlight(response, 0, "test", 1,
                    equalTo("And some other <em>test</em> <em>test</em>.  "));
        }

        search.setHighlighterOrder("source");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1,
                    equalTo("And some other <em>test</em> <em>test</em>.  "));
        }

        options.put("top_scoring", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("And some other <em>test</em> <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("Junk junk junk junk junk junk junk "
                    + "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
        }
    }

    @Test
    public void boostBefore() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test.  " +
                "Junk junk junk junk junk junk junk junk junk junk junk test test test.");

        SearchRequestBuilder search = testSearch(
                boolQuery().should(termQuery("test", "test")).should(termQuery("test", "foo")))
                .addHighlightedField(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                .setHighlighterOrder("score");
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("boost_before", ImmutableMap.of("10", 4, "20", 2f));
        options.put("fragment_weigher", "sum");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("Junk junk junk junk junk junk junk " +
                    "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
        }

        // Should also apply when sorting by source using top_scoring
        search.setHighlighterOrder("source");
        options.put("top_scoring", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("Junk junk junk junk junk junk junk " +
                    "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
        }

        options.put("boost_before", ImmutableMap.of("10", 4, Integer.toString(Integer.MAX_VALUE), 0f));
        options.put("fragment_weigher", "sum");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, /* total fragments */1,
                    equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
        }
    }

    @Test
    public void useDefaultSimilarity() throws IOException, InterruptedException, ExecutionException {
        buildIndex(true, true, 1);
        client().prepareIndex("test", "test").setSource("test", new String[] {"test", "foo foo"}).get();
        // We need enough "foo" so that a whole bunch end up on the shard with the above entry.
        List<IndexRequestBuilder> indexes = new ArrayList<IndexRequestBuilder>();
        for (int i = 0; i < 1000; i++) {
            indexes.add(client().prepareIndex("test", "test").setSource("test", "foo"));
        }
        indexRandom(true, indexes);

        SearchRequestBuilder search = testSearch(
                boolQuery().should(termQuery("test", "test")).should(termQuery("test", "foo")))
                .setHighlighterOrder("score");
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }
        
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("default_similarity", false);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>foo</em> <em>foo</em>"));
        }
        
        options.put("default_similarity", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }

        // And it still works when using top_scoring
        options.put("default_similarity", true);
        options.put("top_scoring", true);
        search.setHighlighterOrder("source");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }
    }

    @Test
    public void matchedFields() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test.english"));
        // One matched field
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }

        // Two matched fields
        search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test", "test.english"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }

        search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test", "test.english",
                        "test.whitespace"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void matchedFieldsSameAnalyzer() throws IOException {
        buildIndex();
        indexTestData();
        SearchRequestBuilder search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test.english", "test.english2"));

        for (String hitSource : HIT_SOURCES) {
            setHitSource(search, hitSource);
            if (hitSource.equals("analyze")) {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(search, RestStatus.INTERNAL_SERVER_ERROR,
                        containsString("unique analyzer"));
            } else {
                SearchResponse response = search.get();
                assertHighlight(response, 0, "test", 0,
                        equalTo("<em>tests</em> very simple <em>test</em>"));
            }
        }
    }

    @Test
    public void settingHitSourceWithoutDataIsAnError() throws IOException {
        buildIndex(false, false, between(1, 5));
        indexTestData();

        SearchRequestBuilder search = testSearch();
        for (String hitSource : HIT_SOURCES) {
            setHitSource(search, hitSource);
            if (hitSource.equals("analyze")) {
                SearchResponse response = search.get();
                assertNoFailures(response);
            } else {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(search, RestStatus.INTERNAL_SERVER_ERROR,
                        containsString("as a hit source without setting"));
            }
        }

        // Now with matched fields!
        search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test", "test.english"));
        for (String hitSource : HIT_SOURCES) {
            setHitSource(search, hitSource);
            if (hitSource.equals("analyze")) {
                SearchResponse response = search.get();
                assertNoFailures(response);
            } else {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(search, RestStatus.INTERNAL_SERVER_ERROR,
                        containsString("as a hit source without setting"));
            }
        }
    }
    
    @Test
    public void highlightWithoutOptionsDoesntBlowUp() throws IOException {
        buildIndex();
        indexTestData();

        assertNoFailures(testSearch().get());
        assertNoFailures(testSearch().setHighlighterOrder("score").get());
    }

    @Test
    public void dataInOtherFields() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test", "1")
                .setSource("test", "tests very simple test", "other",
                        "break me maybe?  lets make this pretty long tests").get();
        refresh();

        SearchRequestBuilder search = testSearch();
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void dataInOtherDocuments() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test", "2")
                .setSource("test", "break me maybe?  lets make this pretty long tests").get();
        indexTestData();

        SearchRequestBuilder search = testSearch();
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void noMatchesThisDocButMatchesOthers() throws IOException, InterruptedException,
            ExecutionException {
        buildIndex();
        // This is the doc we're looking for and it doesn't have a match in the
        // column we're highlighting
        client().prepareIndex("test", "test", "1")
                .setSource("test", "no match here", "find_me", "test").get();
        // These docs have a match in the column we're highlighting. We need a
        // bunch of them to make sure some end up in the same segment as what
        // we're looking for.
        List<IndexRequestBuilder> extra = new ArrayList<IndexRequestBuilder>();
        for (int i = 0; i < 100; i++) {
            extra.add(client().prepareIndex("test", "test", "other " + i).setSource("test", "test"));
        }
        indexRandom(true, extra);

        SearchRequestBuilder search = testSearch(termQuery("find_me", "test"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertNotHighlighted(response, 0, "test");
        }
    }

    @Test
    public void noMatch() throws IOException {
        String shortString = "Lets segment this thing.  Yay.";
        String longString = "Lets segment a much longer sentence because "
                + "we really like long sentences but we shouldn't return "
                + "everything because that'd be too much don't you think?  Yes.";

        buildIndex();
        client().prepareIndex("test", "test", "short")
                .setSource("test", shortString, "find_me", "shortstring").get();
        client().prepareIndex("test", "test", "long")
                .setSource("test", longString, "find_me", "longstring").get();
        refresh();

        // No match on a short string
        HighlightBuilder.Field field = new HighlightBuilder.Field("test").noMatchSize(10);
        SearchRequestBuilder search = testSearch(termQuery("find_me", "shortstring")).addHighlightedField(
                field);
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment"));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment this thing.  "));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));

        // No match on a longer one
        search = testSearch(termQuery("find_me", "longstring")).addHighlightedField(
                field);
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment"));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment a much longer "));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(longString));

        // No match size > string size
        field.noMatchSize(1000);
        search = testSearch(termQuery("find_me", "shortstring")).addHighlightedField(
                field);
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));

        // boundaryMaxScan + size > string size but size < string size
        field.noMatchSize(10).boundaryMaxScan(10000);
        search = testSearch(termQuery("find_me", "shortstring")).addHighlightedField(
                field);
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment"));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment this thing.  "));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));
    }

    @Test
    public void zeroFragmentsReturnsWholeField() throws IOException {
        buildIndex();
        indexTestData("This test is long enough to demonstrate that we switched to the whole field segmenter.");

        SearchRequestBuilder search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").numOfFragments(0).fragmentSize(10));
        assertHighlight(search.get(), 0, "test", 0, equalTo("This <em>test</em> is "
                + "long enough to demonstrate that we switched to the whole field segmenter."));
    }

    @Test
    public void maxFragmentsScored() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test.  " +
                "Junk junk junk junk junk junk junk junk junk junk junk test test test.");

        SearchRequestBuilder search = testSearch(termQuery("test", "test"))
                .addHighlightedField(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                .setHighlighterOrder("score");
        Map<String, Object> options = new HashMap<String, Object>();

        // We find the top scoring fragment if it is within max_fragments_scored
        options.put("max_fragments_scored", 10);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("Junk junk junk junk junk junk junk " +
                    "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
            assertHighlight(response, 0, "test", 1, equalTo("And some other <em>test</em>.  "));
        }

        // We don't if it isn't
        options.put("max_fragments_scored", 2);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("And some other <em>test</em>.  "));
        }
    }

    @Test
    public void fetchedFields() throws IOException, InterruptedException,
            ExecutionException {
        buildIndex();
        // This is the doc we're looking for and it doesn't have a match in the
        // column we're highlighting
        client().prepareIndex("test", "test", "1")
                .setSource("test", new String[] {"no match here", "this one"}, "fetched", new Integer[] {0, 1}).get();
        client().prepareIndex("test", "test", "2")
                .setSource("test", new String[] {"firstplace", "no match here"}, "fetched", new Integer[] {0, 1, 2}).get();
        client().prepareIndex("test", "test", "3")
                .setSource("test", new String[] {"no match here", "nobuddy"}, "fetched", new Integer[] {0}).get();
        XContentBuilder nested = jsonBuilder().startObject().startArray("foo");
        for (int i = 0 ; i < 200; i++) {
            nested.startObject().field("test").value("nested" + Integer.toString(i));
            if (i < 100) {
                nested.field("fetched").value(Integer.toString(i));
                nested.field("fetched2").value(Integer.toString(1000+i));
            }
            nested.endObject();
        }
        nested.endArray().endObject();
        client().prepareIndex("test", "test", "4").setSource(nested).get();
        refresh();

        SearchRequestBuilder search = testSearch(termQuery("test", "one"));
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("fetch_fields", new String[] {"fetched"});
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("this <em>one</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("1"));
        }

        search = testSearch(termQuery("test", "firstplace"));
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>firstplace</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("0"));
        }

        search = testSearch(termQuery("test", "nobuddy"));
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>nobuddy</em>"));
            assertHighlight(response, 0, "test", 1, equalTo(""));
        }

        search = testSearch(termQuery("foo.test", "nested99")).addHighlightedField("foo.test");
        options.put("fetch_fields", new String[] {"foo.fetched"});
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested99</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("99"));
        }

        search = testSearch(boolQuery().should(termQuery("foo.test", "nested99"))
                .should(termQuery("foo.test", "nested54"))).addHighlightedField("foo.test");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            // Score Ordered
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested54</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("54"));
            assertHighlight(response, 0, "foo.test", 2, equalTo("<em>nested99</em>"));
            assertHighlight(response, 0, "foo.test", 3, equalTo("99"));
        }

        search = testSearch(boolQuery().should(termQuery("foo.test", "nested54"))
                .should(termQuery("foo.test", "nested123"))).addHighlightedField("foo.test");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            // Score Ordered
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested54</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("54"));
            assertHighlight(response, 0, "foo.test", 2, equalTo("<em>nested123</em>"));
            assertHighlight(response, 0, "foo.test", 3, equalTo(""));
        }

        search = testSearch(termQuery("foo.test", "nested99")).addHighlightedField("foo.test");
        options.put("fetch_fields", new String[] {"foo.fetched", "foo.fetched2"});
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested99</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("99"));
            assertHighlight(response, 0, "foo.test", 2, equalTo("1099"));
        }
    }

    @Test
    public void fragmentWeigherTermQuery() throws IOException {
        checkFragmentWeigher(boolQuery().should(termQuery("test", "fee")).should(termQuery("test", "phi")),
                "<em>Fee</em>-<em>fee</em>-<em>fee</em>-<em>fee</em>.",
                "<em>Fee</em> <em>phi</em>.");
    }

    @Test
    public void fragmentWeigherPhraseQuery() throws IOException {
        checkFragmentWeigher(matchPhraseQuery("test", "fee phi"),
                "<em>Fee</em>-<em>fee</em>-<em>fee</em>-<em>fee</em>.",
                "<em>Fee</em> <em>phi</em>.");
    }

    @Test
    public void fragmentWeigherPrefixQuery() throws IOException {
        checkFragmentWeigher(boolQuery().should(prefixQuery("test", "f")).should(termQuery("test", "phi")),
                "<em>Fee</em>-<em>fi</em>-<em>fo</em>-<em>fum</em>.",
                "<em>Fee</em> <em>phi</em>.");
    }

    @Test
    public void highlightQuery() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test").setSource("test", "foo", "test2", "bar").get();
        refresh();

        SearchRequestBuilder search = testSearch(termQuery("test", "foo")).addHighlightedField(
                new HighlightBuilder.Field("test2").highlightQuery(termQuery("test2", "bar")));

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>foo</em>"));
            assertHighlight(response, 0, "test2", 0, equalTo("<em>bar</em>"));
        }
    }

    @Test
    public void manyTerms() throws IOException {
        buildIndex();
        indexTestData("a b c d e f g h i j");

        assertNoFailures(testSearch(
                boolQuery().should(termQuery("test", "a")).should(termQuery("test", "b"))
                        .should(termQuery("test", "c")).should(termQuery("test", "d"))
                        .should(termQuery("test", "e")).should(termQuery("test", "f"))
                        .should(termQuery("test", "g")).should(termQuery("test", "h"))
                        .should(termQuery("test", "i"))).get());
    }

    /**
     * This will catch really busted automatons like you can get with allow_mutate.
     */
    @Test
    public void whatIsLove() throws IOException {
        buildIndex();
        indexTestData("What-a-Mess is a series of children's books written by British comedy writer Frank Muir and illustrated by Joseph Wright. It was later made into an animated series in the UK in 1990 and again in 1995 by DIC Entertainment and aired on ABC in the United States. It aired on YTV from 1995 to 1999 in Canada. The title character is a disheveled (hence his nickname), accident-prone Afghan Hound puppy, whose real name was Prince Amir of Kinjan. Central Independent Television, the Independent Television contractor for the Midlands, created following the restructuring of ATV and commencing broadcast on 1 January 1982, Link Licensing & Bevanfield Films produced the first series and DIC Entertainment produced the second series. Both of them were narrated by Frank Muir.   What-a-Mess - A scruffy Afghan puppy in which is the main character of the entire franchise. His Breed name is Prince Amir of Kinjan, and has a yellow duck sitting on top of his head. In the US animated version, the duck was coloured blue, as if his character was merged with the blue bird in the UK animated version and books, and was also given a name by What-A-Mess called Baldwin. In the US animated version, What-A-Mess is voiced by Ryan O'Donohue. What-a-Mess's Mother - Also known as The Duchess of Kinjan is a beautiful pedigree Afghan Hound mother to What-a-Mess, and is voiced by Miriam Flynn in the US version. Archbishop of Canterbury - A scruffy dark blue dog with brown patches which What-A-Mess met and befriended in What-A-Mess Goes to the Seaside. He's named this way because when What-A-Mess introduces himself with his breed name he sarcastically replies \"Sure, and I'm the Archbishop of Canterbury!\", which the naive pup takes as his actual name. His name was changed to Norton in the US Animated Version, and he was voiced by Dana Hill. The Cat Next Door - Also known as Felicia in the US animated version, is a brown Siamese Cat that loves to tease What-A-Mess at times. In the US animated version, she was coloured blue and she was voiced by Jo Ann Harris Belson. Cynthia - A Hedgehog which What-A-Mess befriended in What-A-Mess Goes to School. Her character was redesigned to become a mole named Ramona in the US animated version, due to the fact that Hedgehogs aren't native to America. In the US animated version, she is voiced by Candi Milo. Trash - Only in the US animated version, Trash is a Bull Terrier who is a real trouble maker to What-A-Mess. His real name is actually Francis He is voiced by Joe Nipote. Frank - An Old English Sheepdog that narrates the US animated version of What-A-Mess, voiced by Frank Muir himself!   What-a-Mess What-a-Mess The Good What-a-Mess at the Seaside What-a-Mess Goes to School Prince What-a-Mess Super What-a-Mess What-a-Mess and the Cat Next Door What-a-Mess and the Hairy Monster  Four Seasons What-a-Mess in Spring What-a-Mess in Summer What-a-Mess in Autumn What-a-Mess in Winter Four Square Meals What-a-Mess has Breakfast What-a-Mess has Lunch What-a-Mess has Tea What-a-Mess has Supper Mini Books What-a-Mess has a Brain Wave What-a-Mess and Little Poppet What-a-Mess and a trip to the Vet What-a-Mess the Beautiful What-a-Mess Goes to Town What-a-Mess Goes on Television What-a-Mess Goes Camping   What-a-Mess Goes to the Seaside / 1990.03.26 What-a-Mess Goes to School / 1990.04.02 Prince What-a-Mess / 1990.04.09 Super What-a-Mess / 1990.04.16 What-a-Mess Keeps Cool / 1990.04.30 What-a-Mess and Cynthia the Hedgehog / 1990.05.14 What-a-Mess Has a Brain Wave! / 1990.05.21 What-a-Mess and the Cat Next Door / 1990.06.04 What-a-Mess and Little Poppet / 1990.06.18 What-a-Mess Goes Camping / 1990.07.02 What-a-Mess The Beautiful / 1990.07.09 What-a-Mess Goes to Town / 1990.07.16 What-a-Mess Goes to the Vet / 1990.07.23   Talkin' Trash (September 16, 1995) A Bone to Pick Midnight Snack Schoolin' Around The Legend of Junkyard Jones It's Raining Cats and Dogs Home Alone...Almost Super What-A-Mess The Recliner Afghan Holiday The Bone Tree Just Four More Left The Ropes What-A-Mess Has Breakfast Prize Puppy The Great Eascape The Scarecrow and Prince Amir Shampooed Show and Tail I Spy, I Cry, I Try What-A-Mess and the Hairy Monster Trick Or Treat My Teatime with Frank Out With the Garbage Dr. What-A-Mess Ultimate What-A-Mess This Hydrant Is Mine His Majesty, Prince What-A-Mess Trash's Wonderful Life Snowbound The Thanksgiving Turkey Santa What-A-Mess Here Comes Santa Paws All Around the Mallberry Bush What-A-Mess At the Movies His Royal Highness, Prince What-A-Mess Party at Poppet's Take Me Out to the Dog Park The Watch Out Dog Molenapped! Pound Pals Taste Test Slobber on a Stick Scout's Honor Seein' Double Luck on His Side What-A-Mess Keeps the Doctor Away There's No Business like Shoe Business Joy Rider Baldwin's Family Reunion Do the Mess Around On Vacation Messy Encounters Dog Days of Summer Fetch! Real Puppies Don't Meow Invasion of the Puppy Snatchers The Ballad of El Pero What-a-Mess Has Lunch Walking the Boy    Russell Williams, Imogen (4 July 2007). \"Whatever happened to What-a-mess?\". London: The Guardian. Retrieved 3 January 2011.   \"IMDB What-a-mess\". Retrieved 3 January 2011.   1990 series episode guide at the Big Cartoon DataBase");

        SearchRequestBuilder search = testSearch(queryString("what love?"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertNoFailures(response);
            assertHitCount(response, 1);
        }
    }

    @Test
    public void largeText() throws IOException {
        buildIndex();
        indexTestData(Resources.toString(Resources.getResource(this.getClass(), "large_text.txt"), Charsets.UTF_8));

        SearchRequestBuilder search = testSearch(termQuery("test", "browser")).addHighlightedField(
                "test", 100).setHighlighterOrder("score");

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("json (bug 61659) git #4d2209e " +
                    "- [<em>Browser</em> test] Headless <em>browser</em> test(s) (bug 53691) git #6a238d2 -"));
        }
    }

    /**
     * Skipped until we have a way to verify something. It is useful for
     * spitting out performance information though.
     */
//    @Test
    public void lotsOfTerms() throws IOException, InterruptedException, ExecutionException {
        StopWatch watch = new StopWatch();
        watch.start("load");
        buildIndex(true, true, 1);
        for (char l1 = 'a'; l1 <= 'z'; l1++) {
            BulkRequestBuilder request = client().prepareBulk();
            for (char l2 = 'a'; l2 <= 'z'; l2++) {
                for (char l3 = 'a'; l3 <= 'z'; l3++) {
                    StringBuilder b = new StringBuilder();
                    for (char l4 = 'a'; l4 <= 'z'; l4++) {
                        b.append('z').append(l1).append(l2).append(l3).append(l4).append(' ');
                    }
                    request.add(client().prepareIndex("test", "test").setSource("test", b.toString()));
                }
            }
            request.get();
            logger.info("Sending for {}", l1);
        }
        refresh();
        // Optimizing to one segment makes the timing more consistent
        waitForRelocation();
        OptimizeResponse actionGet = client().admin().indices().prepareOptimize().setMaxNumSegments(1).execute().actionGet();
        assertNoFailures(actionGet);
        watch.stop();

        lotsOfTermsTestCase(watch, "warmup", fuzzyQuery("test", "zooom"));
        lotsOfTermsTestCase(watch, "single fuzzy", fuzzyQuery("test", "zooom"));
        lotsOfTermsTestCase(watch, "multiple fuzzy", boolQuery().should(fuzzyQuery("test", "zooom"))
                .should(fuzzyQuery("test", "zats")).should(fuzzyQuery("test", "zouni")));
        lotsOfTermsTestCase(watch, "multiple term", boolQuery().should(termQuery("test", "zooma"))
                .should(termQuery("test", "zats")).should(termQuery("test", "zouna")));
        lotsOfTermsTestCase(watch, "single term", termQuery("test", "zooma"));
        lotsOfTermsTestCase(watch, "fuzzy and term", boolQuery().should(fuzzyQuery("test", "zooma"))
                .should(termQuery("test", "zouna")));
        lotsOfTermsTestCase(watch, "two and two",
                boolQuery().should(fuzzyQuery("test", "zooms")).should(fuzzyQuery("test", "zaums"))
                        .should(termQuery("test", "zeesa")).should(termQuery("test", "zouqn")));
        lotsOfTermsTestCase(watch, "regexp", regexpQuery("test", "zo[om]mt"));
        lotsOfTermsTestCase(watch, "regexp and term", boolQuery().should(regexpQuery("test", "zo[azxo]my"))
                .should(termQuery("test", "zouny")));
        // Postings are really slow for stuff like "z*"
        lotsOfTermsTestCase(watch, "wildcard", wildcardQuery("test", "zap*"));
        lotsOfTermsTestCase(watch, "wildcard and term", boolQuery().should(wildcardQuery("test", "zap*"))
                .should(termQuery("test", "zouny")));
        lotsOfTermsTestCase(watch, "wildcard", prefixQuery("test", "zap"));
        lotsOfTermsTestCase(watch, "wildcard and term", boolQuery().should(prefixQuery("test", "zap"))
                .should(termQuery("test", "zouny")));
        // The boolQuery here is required because the test data doesn't contain
        // a single document that'll match the phrasePrefix
        lotsOfTermsTestCase(watch, "phrase prefix and term", boolQuery()
                .should(matchPhrasePrefixQuery("test", "zooma zoomb zoo"))
                .should(termQuery("test", "zooma")));
        lotsOfTermsTestCase(watch, "phrase prefix and term", queryString("test:\"zoooo\" OR test2:\"zaaap\""));

        logger.info(watch.prettyPrint());
    }

    private void lotsOfTermsTestCase(StopWatch watch, String name, QueryBuilder query) throws IOException {
        logger.info("starting {}", name);
        watch.start(name);
        SearchRequestBuilder search = testSearch(query);
        for (String hitSource : HIT_SOURCES) {
            setHitSource(search, hitSource);
            for (int i = 0; i < 10; i++) {
                SearchResponse response = search.get();
                assertHighlight(response, 0, "test", 0,
                        both(containsString("<em>z")).and(containsString("</em>")));
            }
        }
        watch.stop();

        logger.info("starting {} many highlighted fields", name);
        watch.start(String.format(Locale.ENGLISH, "%s many highlighted fields", name));
        search.addHighlightedField("test.english").addHighlightedField("test.english2").addHighlightedField("test2");
        for (String hitSource : HIT_SOURCES) {
            setHitSource(search, hitSource);
            for (int i = 0; i < 10; i++) {
                SearchResponse response = search.get();
                assertHighlight(response, 0, "test", 0,
                        both(containsString("<em>z")).and(containsString("</em>")));
            }
        }
        watch.stop();

        logger.info("starting {} many queried fields", name);
        BoolQueryBuilder many = boolQuery();
        many.should(query);
        for(String field: new String[] {"test.english", "test.english2", "test2", "test2.english"}) {
            XContentBuilder builder = XContentBuilder.builder(JsonXContent.jsonXContent);
            query.toXContent(builder, null);
            many.should(wrapperQuery(
                    builder.string().replaceAll("test", field)));
        }
        search.setQuery(many);

        watch.start(String.format(Locale.ENGLISH, "%s many queried fields", name));
        for (String hitSource : HIT_SOURCES) {
            setHitSource(search, hitSource);
            for (int i = 0; i < 10; i++) {
                SearchResponse response = search.get();
                assertHighlight(response, 0, "test", 0,
                        both(containsString("<em>z")).and(containsString("</em>")));
            }
        }
        watch.stop();
    }

    // TODO matched_fields with different hit source
    // TODO infer proper hit source
    
    /**
     * A simple search for the term "test".
     */
    private SearchRequestBuilder testSearch() {
        return testSearch(termQuery("test", "test"));
    }

    /**
     * A simple search for the term test.
     */
    private SearchRequestBuilder testSearch(QueryBuilder builder) {
        return client().prepareSearch("test").setTypes("test").setQuery(builder)
                .setHighlighterType("experimental").addHighlightedField("test")
                .setSize(1);
    }

    private SearchRequestBuilder setHitSource(SearchRequestBuilder search, String hitSource) {
        return search.setHighlighterOptions(ImmutableMap.<String, Object> of("hit_source",
                hitSource));
    }

    private void buildIndex() throws IOException {
        buildIndex(true, true, between(1, 5));
    }

    private void buildIndex(boolean offsetsInPostings, boolean fvhLikeTermVectors, int shards)
            throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject();
        mapping.startObject("test").startObject("properties");
        addField(mapping, "test", offsetsInPostings, fvhLikeTermVectors);
        addField(mapping, "test2", offsetsInPostings, fvhLikeTermVectors);
        mapping.startObject("foo").field("type").value("object").startObject("properties");
        addField(mapping, "test", offsetsInPostings, fvhLikeTermVectors);
        mapping.endObject().endObject().endObject().endObject();

        XContentBuilder settings = jsonBuilder().startObject().startObject("index");
        settings.field("number_of_shards", shards);
        settings.startObject("analysis");
        settings.startObject("analyzer");
        settings.startObject("chars").field("tokenizer", "chars").endObject();
        settings.endObject();
        settings.startObject("tokenizer");
        settings.startObject("chars").field("type", "pattern").field("pattern", "(.)")
                .field("group", 0).endObject();
        settings.endObject();
        settings.endObject().endObject();
        assertAcked(prepareCreate("test").setSettings(settings).addMapping("test", mapping));
        ensureYellow();
    }

    private void addField(XContentBuilder builder, String name, boolean offsetsInPostings,
            boolean fvhLikeTermVectors) throws IOException {
        builder.startObject(name).field("type", "string");
        addProperties(builder, offsetsInPostings, fvhLikeTermVectors);
        builder.startObject("fields");
        addSubField(builder, "whitespace", "whitespace", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "english", "english", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "english2", "english", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "chars", "chars", offsetsInPostings, fvhLikeTermVectors);
        builder.endObject().endObject();
    }

    private void addSubField(XContentBuilder builder, String name, String analyzer,
            boolean offsetsInPostings, boolean fvhLikeTermVectors) throws IOException {
        builder.startObject(name);
        builder.field("type", "string");
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

    private void indexTestData() {
        indexTestData("tests very simple test");
    }

    private void indexTestData(Object contents) {
        client().prepareIndex("test", "test", "1").setSource("test", contents).get();
        refresh();
    }

    private void checkFragmentWeigher(QueryBuilder query, String sumMatch, String exponentialMatch) throws IOException {
        buildIndex();
        indexTestData(new String[] {
                sumMatch.replaceAll("<em>", "").replaceAll("</em>", ""),
                exponentialMatch.replaceAll("<em>", "").replaceAll("</em>", ""),
        });

        SearchRequestBuilder search = testSearch(query)
                .addHighlightedField(new HighlightBuilder.Field("test").numOfFragments(2))
                .setHighlighterOrder("score");
        Map<String, Object> options = new HashMap<String, Object>();

        options.put("fragment_weigher", "sum");
        options.put("phrase_as_terms", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo(sumMatch));
            assertHighlight(response, 0, "test", 1, equalTo(exponentialMatch));
        }

        options.put("fragment_weigher", "exponential");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo(exponentialMatch));
        }

        // Exponential is the default
        options.remove("fragment_weigher");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo(exponentialMatch));
        }

        // Adding a type doesn't break it.
        search.setTypes("test");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo(exponentialMatch));
        }
    }
}
