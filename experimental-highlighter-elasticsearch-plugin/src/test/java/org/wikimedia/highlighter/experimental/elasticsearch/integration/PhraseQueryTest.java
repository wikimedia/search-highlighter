package org.wikimedia.highlighter.experimental.elasticsearch.integration;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchPhrasePrefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.elasticsearch.index.query.QueryBuilders.queryString;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNotHighlighted;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.junit.Test;
import org.wikimedia.highlighter.experimental.elasticsearch.AbstractExperimentalHighlighterIntegrationTestBase;

/**
 * Tests for phrase queries.
 */
public class PhraseQueryTest extends AbstractExperimentalHighlighterIntegrationTestBase {
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
    }

    @Test
    public void singlePhraseWithPhraseAsTermsSwitch() throws IOException {
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

    @Test
    public void phraseWithStopWords() throws IOException {
        buildIndex();
        indexTestData("and and test test");

        Map<String, Object> options = new HashMap<String, Object>();
        SearchRequestBuilder search = testSearch(boolQuery()
                .should(matchPhraseQuery("test", "and test"))
                .should(matchPhraseQuery("test.english", "and test")));
        search.setHighlighterOptions(options).addHighlightedField("test.english");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test.english", 0,
                    equalTo("and and <em>test</em> <em>test</em>"));
        }

        search.addHighlightedField(new HighlightBuilder.Field("test.english").matchedFields("test.english", "test"));
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test.english", 0,
                    equalTo("and <em>and</em> <em>test</em> <em>test</em>"));
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

    /**
     * Single position phrase queries crash the PhraseHitEnumWrapper so we avoid
     * sending them to it.
     */
    @Test
    public void singlePositionPhraseQueryOnItsOwn() throws IOException {
        singlePositionPhraseQueryTestCase("forÀ", "forÀ", "for<em>À</em>");
    }

    @Test
    public void singlePositionPhraseQueryWithFriends() throws IOException {
        singlePositionPhraseQueryTestCase("Sju svarta be-hå", "Sju svarta be\\-hå \\(1954 film\\)",
                "<em>Sju</em> <em>svarta</em> be-<em>hå</em>");
    }

    private void singlePositionPhraseQueryTestCase(String data, String query, String expected)
            throws IOException {
        buildIndex();
        indexTestData(data);

        SearchRequestBuilder search = testSearch(
                queryString(query).defaultField("test.cirrus_english").autoGeneratePhraseQueries(
                        true)).addHighlightedField("test.cirrus_english");
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test.cirrus_english", 0, equalTo(expected));
        }
    }
}
