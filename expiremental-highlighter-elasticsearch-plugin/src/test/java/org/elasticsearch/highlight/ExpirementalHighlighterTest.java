package org.elasticsearch.highlight;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.fuzzyQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchPhrasePrefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.regexpQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanFirstQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanNearQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanNotQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanOrQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanTermQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

public class ExpirementalHighlighterTest extends ElasticsearchIntegrationTest {
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
        indexTestData();

        SearchRequestBuilder search = testSearch(matchPhraseQuery("test", "simple test"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests very <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void singlePhrasePrefixQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(matchPhrasePrefixQuery("test", "simple te"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            // You can see right here that we aren't careful with phrase queries
            // like the FVH is.
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very <em>simple</em> <em>test</em>"));
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
    public void multiValued() throws IOException {
        buildIndex();
        indexTestData(new String[] { "tests very simple test", "with two fields to test" });

        SearchRequestBuilder search = testSearch().addHighlightedField("test", 100, 100);
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
    public void useDefaultSimilarity() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test").setSource("test", new String[] {"test", "foo foo"}).get();
        // We need enough "foo" so that a whole bunch end up on the shard with the above entry.
        for (int i = 0; i < 100; i++) {
            client().prepareIndex("test", "test").setSource("test", "foo").get();    
        }
        refresh();

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
            SearchResponse response = setHitSource(search, hitSource).get();
            if (hitSource.equals("analyze")) {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(response);
            } else {
                assertHighlight(response, 0, "test", 0,
                        equalTo("<em>tests</em> very simple <em>test</em>"));
            }
        }
    }

    @Test
    public void settingHitSourceWithoutDataIsAnError() throws IOException {
        buildIndex(false, false);
        indexTestData();

        SearchRequestBuilder search = testSearch();
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            if (hitSource.equals("analyze")) {
                assertNoFailures(response);
            } else {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(response);
            }
        }

        // Now with matched fields!
        search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test", "test.english"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            if (hitSource.equals("analyze")) {
                assertNoFailures(response);
            } else {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(response);
            }
        }
    }

    // TODO matched_fields with different hit source
    
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
                .setHighlighterType("expiremental").addHighlightedField("test");
    }

    private SearchRequestBuilder setHitSource(SearchRequestBuilder search, String hitSource) {
        return search.setHighlighterOptions(ImmutableMap.<String, Object> of("hit_source",
                hitSource));
    }

    private void buildIndex() throws IOException {
        buildIndex(true, true);
    }

    private void buildIndex(boolean offsetsInPostings, boolean fvhLikeTermVectors)
            throws IOException {
        XContentBuilder builder = jsonBuilder().startObject().startObject("test")
                .startObject("properties").startObject("test").field("type", "string");
        addProperties(builder, offsetsInPostings, fvhLikeTermVectors);
        builder.startObject("fields");
        addField(builder, "whitespace", "whitespace", offsetsInPostings, fvhLikeTermVectors);
        addField(builder, "english", "english", offsetsInPostings, fvhLikeTermVectors);
        addField(builder, "english2", "english", offsetsInPostings, fvhLikeTermVectors);
        builder.endObject().endObject().endObject().endObject();
        assertAcked(prepareCreate("test").addMapping("test", builder));
        ensureYellow();
    }

    private void addField(XContentBuilder builder, String name, String analyzer,
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
}
