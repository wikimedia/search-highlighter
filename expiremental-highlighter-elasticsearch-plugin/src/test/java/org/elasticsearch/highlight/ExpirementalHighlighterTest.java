package org.elasticsearch.highlight;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.List;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

public class ExpirementalHighlighterTest extends ElasticsearchIntegrationTest {
    private static final List<String> HIT_SOURCES = ImmutableList.of("postings", "vectors",
            "analyze");

    @Test
    public void basic() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test", "1").setSource("test", "a very simple test").get();
        refresh();
        SearchRequestBuilder search = testSearch().addHighlightedField("test");

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("a very simple <em>test</em>"));
        }
    }

    @Test
    public void multiValued() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test", "1")
                .setSource("test",
                        new String[] { "tests very simple test", "with two fields to test" }).get();
        refresh();
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
    public void matchedFields() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test", "1").setSource("test", "tests very simple test")
                .get();
        refresh();
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
        client().prepareIndex("test", "test", "1").setSource("test", "tests very simple test")
                .get();
        refresh();
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

    /**
     * A simple search for the term test.
     */
    private SearchRequestBuilder testSearch() {
        return client().prepareSearch("test").setTypes("test")
                .setQuery(QueryBuilders.termQuery("test", "test"))
                .setHighlighterType("expiremental");
    }

    private SearchRequestBuilder setHitSource(SearchRequestBuilder search, String hitSource) {
        return search.setHighlighterOptions(ImmutableMap.<String, Object> of("hit_source",
                hitSource));
    }

    private void buildIndex() throws IOException {
        XContentBuilder builder = jsonBuilder().startObject().startObject("test")
                .startObject("properties").startObject("test").field("type", "string")
                .field("index_options", "offsets").field("term_vector", "with_positions_offsets")
                .startObject("fields");
        addField(builder, "whitespace", "whitespace");
        addField(builder, "english", "english");
        addField(builder, "english2", "english");
        builder.endObject().endObject().endObject().endObject();
        assertAcked(prepareCreate("test").addMapping("test", builder));
        ensureYellow();
    }

    private void addField(XContentBuilder builder, String name, String analyzer) throws IOException {
        builder.startObject(name);
        builder.field("type", "string");
        builder.field("analyzer", analyzer);
        builder.field("index_options", "offsets");
        builder.field("term_vector", "with_positions_offsets");
        builder.endObject();
    }
}
