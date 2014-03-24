package org.elasticsearch.highlight;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.*;
import static org.hamcrest.Matchers.*;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

public class ExpirementalHighlighterTest extends ElasticsearchIntegrationTest {
    @Test
    public void basic() {
        buildIndex();
        client().prepareIndex("test", "test", "1").setSource("test", "a very simple test").get();
        refresh();
        SearchRequestBuilder search = client().prepareSearch("test").setTypes("test").setQuery(QueryBuilders.termQuery("test", "test"))
                .setHighlighterType("expiremental").addHighlightedField("test");
        SearchResponse response = search.get();
        assertHighlight(response, 0, "test", 0, equalTo("a very simple <em>test</em>"));
    }
    
    private void buildIndex() {
        String settings = ",index_options=offsets";
        if (rarely()) {
            logger.info("Using term vectors.");
            settings = ",term_vector=with_positions_offsets";
        } else if (rarely()) {
            logger.info("Reanalyzing hits.");
            settings = "";
        }
        assertAcked(prepareCreate("test").addMapping("test", "test", "type=string" + settings));
        ensureYellow();
    }
}
