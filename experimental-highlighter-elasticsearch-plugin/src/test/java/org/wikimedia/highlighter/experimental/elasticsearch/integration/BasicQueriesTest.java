package org.wikimedia.highlighter.experimental.elasticsearch.integration;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.functionScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.fuzzyQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.regexpQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanFirstQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanNearQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanNotQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanOrQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanTermQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders.fieldValueFactorFunction;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;
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

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "test"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void boolOfTermQueries() throws IOException {
        buildIndex();
        indexTestData();
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(boolQuery().must(termQuery("test", "test")).must(
                    termQuery("test", "simple")), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests very <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void singleFuzzyQuery() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(fuzzyQuery("test", "test"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(matchQuery("test", "test").fuzziness(Fuzziness.AUTO), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleRangeQuery() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(rangeQuery("test").from("teso").to("tesz"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleWildcardQuery() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(wildcardQuery("test", "te?t"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(wildcardQuery("test", "te*"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleRegexpQuery() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(regexpQuery("test", "tests?"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanTermQuery() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(spanTermQuery("test", "test"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanFirstQuery() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(spanFirstQuery(spanTermQuery("test", "test"), 5), hitSource(hitSource)).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanNearQuery() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    spanNearQuery(spanTermQuery("test", "tests"), 5)
                        .addClause(spanTermQuery("test", "test")),
                    hitSource(hitSource)).get();
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

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    spanNotQuery(spanTermQuery("test", "test"), spanTermQuery("test", "tests")),
                    hitSource(hitSource)).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanOrQuery() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    spanOrQuery(spanTermQuery("test", "test"))
                        .addClause(spanTermQuery("test", "tests")), hitSource(hitSource)).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void functionScoreQueryWithoutFilter() throws IOException {
        buildIndex();
        client().prepareIndex("test", "_doc", "1").setSource("test", "test", "bar", 2).get();
        refresh();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(functionScoreQuery(termQuery("test", "test"), fieldValueFactorFunction("bar")), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }
    }

    @Test
    public void functionScoreQueryWithFilter() throws IOException {
        buildIndex();
        client().prepareIndex("test", "_doc", "1").setSource("test", "test", "bar", 2).get();
        refresh();

        QueryBuilder fbuilder = functionScoreQuery(
                        termQuery("test", "test"),
                        new FilterFunctionBuilder[]{
                                new FilterFunctionBuilder(
                                        termQuery("test", "test"),
                                        fieldValueFactorFunction("bar")
                                 )
                        });
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(fbuilder, hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }
    }
}
