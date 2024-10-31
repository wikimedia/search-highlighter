package org.wikimedia.highlighter.cirrus.opensearch.integration;

import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.opensearch.index.query.QueryBuilders.prefixQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHighlight;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder.Order;
import org.junit.Test;
import org.wikimedia.highlighter.cirrus.opensearch.AbstractCirrusHighlighterIntegrationTestBase;

/**
 * Test for the fragment weighers.
 */
public class FragmentWeightTest extends AbstractCirrusHighlighterIntegrationTestBase {
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

    private void checkFragmentWeigher(QueryBuilder query, String sumMatch, String exponentialMatch) throws IOException {
        buildIndex();
        indexTestData(new String[] {
                sumMatch.replaceAll("<em>", "").replaceAll("</em>", ""),
                exponentialMatch.replaceAll("<em>", "").replaceAll("</em>", ""),
        });

        HighlightBuilder builder = newHLBuilder().order(Order.SCORE);
        SearchRequestBuilder search = testSearch(query).highlighter(builder);

        builder.field(new HighlightBuilder.Field("test").numOfFragments(2));
        Map<String, Object> options = new HashMap<String, Object>();
        builder.options(options);

        options.put("fragment_weigher", "sum");
        options.put("phrase_as_terms", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo(sumMatch));
            assertHighlight(response, 0, "test", 1, equalTo(exponentialMatch));
        }

        options.put("fragment_weigher", "exponential");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo(exponentialMatch));
        }

        // Exponential is the default
        options.remove("fragment_weigher");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo(exponentialMatch));
        }

        // Adding a type doesn't break it.
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo(exponentialMatch));
        }
    }
}
