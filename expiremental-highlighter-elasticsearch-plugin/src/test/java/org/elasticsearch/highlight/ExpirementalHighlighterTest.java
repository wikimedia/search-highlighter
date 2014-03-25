package org.elasticsearch.highlight;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

public class ExpirementalHighlighterTest extends ElasticsearchIntegrationTest {
    private static final List<String> HIT_SOURCES = ImmutableList.of(
            "postings", "vectors", "analyze");

    @Test
    public void basic() {
        buildIndex();
        client().prepareIndex("test", "test", "1").setSource("test", "a very simple test").get();
        refresh();
        SearchRequestBuilder search = client().prepareSearch("test").setTypes("test")
                .setQuery(QueryBuilders.termQuery("test", "test"))
                .setHighlighterType("expiremental").addHighlightedField("test");

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = search.setHighlighterOptions(
                    ImmutableMap.<String, Object> of("hit_source", hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("a very simple <em>test</em>"));
        }
    }

    @Test
    public void multiValued() {
        buildIndex();
        client().prepareIndex("test", "test", "1")
                .setSource("test", new String[] { "a very simple test", "with two fields to test" })
                .get();
        refresh();
        SearchRequestBuilder search = client().prepareSearch("test").setTypes("test")
                .setQuery(QueryBuilders.termQuery("test", "test"))
                .setHighlighterType("expiremental").addHighlightedField("test", 100, 100);

        for (String hitSource : HIT_SOURCES) {
            System.err.println(hitSource);
            SearchResponse response = search.setHighlighterOptions(
                    ImmutableMap.<String, Object> of("hit_source", hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("a very simple <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("with two fields to <em>test</em>"));
        }
    }

    private void buildIndex() {
        assertAcked(prepareCreate("test").addMapping("test", "test",
                "type=string,index_options=offsets,term_vector=with_positions_offsets"));
        ensureYellow();
    }
}
