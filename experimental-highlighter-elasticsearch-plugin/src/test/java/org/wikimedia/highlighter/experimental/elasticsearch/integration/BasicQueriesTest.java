package org.wikimedia.highlighter.experimental.elasticsearch.integration;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.fuzzyQuery;
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
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.Test;
import org.wikimedia.highlighter.experimental.elasticsearch.AbstractExperimentalHighlighterIntegrationTestBase;

/**
 * Test for basic query types.
 */
public class BasicQueriesTest extends AbstractExperimentalHighlighterIntegrationTestBase {
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
}
