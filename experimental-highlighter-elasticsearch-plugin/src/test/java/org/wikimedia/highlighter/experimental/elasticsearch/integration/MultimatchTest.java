package org.wikimedia.highlighter.experimental.elasticsearch.integration;

import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.junit.Test;
import org.wikimedia.highlighter.experimental.elasticsearch.AbstractExperimentalHighlighterIntegrationTestBase;

public class MultimatchTest extends AbstractExperimentalHighlighterIntegrationTestBase {
    @Test
    public void multiMatch() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(multiMatchQuery("test", "test"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void multiMatchCutoffAllLow() throws IOException {
        buildIndex(true, true, 1);
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(multiMatchQuery("very test", "test"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> simple <em>test</em>"));
        }
    }

    @Test
    public void multiMatchCutoffHighAndLow() throws IOException {
        buildIndex(true, true, 1);
        client().prepareIndex("test", "_doc", "2").setSource("test", "test").get();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(multiMatchQuery("very test", "test")
                    .cutoffFrequency(1f), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> simple test"));
        }
    }

    @Test
    public void multiMatchPhraseCutoffHighAndLow() throws IOException {
        buildIndex(true, true, 1);
        client().prepareIndex("test", "_doc", "2").setSource("test", "simple").get();
        indexTestData();

        // Looks like phrase doesn't respect cutoff in multimatch
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(multiMatchQuery("very simple", "test").type(MultiMatchQueryBuilder.Type.PHRASE),
                    hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> <em>simple</em> test"));
        }
    }

    @Test
    public void multiMatchPhrasePrefixCutoffHighAndLow() throws IOException {
        buildIndex(true, true, 1);
        client().prepareIndex("test", "_doc", "2").setSource("test", "simple").get();
        indexTestData();

        // Looks like phrase doesn't respect cutoff in multimatch
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(multiMatchQuery("very simple", "test").type(MultiMatchQueryBuilder.Type.PHRASE_PREFIX), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> <em>simple</em> test"));
        }
    }

    @Test
    public void multiMatchCutoffAllHigh() throws IOException {
        buildIndex(true, true, 1);
        client().prepareIndex("test", "_doc", "2").setSource("test", "very test").get();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(boolQuery()
                    .must(multiMatchQuery("very test", "test"))
                    .filter(idsQuery().addIds("1")), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> simple <em>test</em>"));
        }
    }
}
