package org.wikimedia.highlighter.cirrus.opensearch.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHighlight;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.wikimedia.highlighter.cirrus.opensearch.AbstractCirrusHighlighterIntegrationTestBase;

public class BCNameTest extends AbstractCirrusHighlighterIntegrationTestBase {
    @Override
    protected HighlightBuilder newHLBuilder() {
        return new HighlightBuilder()
            .highlighterType("experimental")
            .field("test");
    }

    @Test
    public void singleTermQuery() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "test"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }
}
