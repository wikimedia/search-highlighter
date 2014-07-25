package org.wikimedia.highlighter.experimental.elasticsearch;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

import java.io.IOException;
import java.util.List;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.plugins.PluginsService;
import org.elasticsearch.test.ElasticsearchIntegrationTest;

@ElasticsearchIntegrationTest.ClusterScope(
        scope = ElasticsearchIntegrationTest.Scope.SUITE, transportClientRatio = 0.0)
public abstract class AbstractExperimentalHighlighterIntegrationTestBase extends ElasticsearchIntegrationTest {
    protected static final List<String> HIT_SOURCES = ImmutableList.of("postings", "vectors",
            "analyze");

    /**
     * A simple search for the term "test".
     */
    protected SearchRequestBuilder testSearch() {
        return testSearch(termQuery("test", "test"));
    }

    /**
     * A simple search for the term test.
     */
    protected SearchRequestBuilder testSearch(QueryBuilder builder) {
        return client().prepareSearch("test").setTypes("test").setQuery(builder)
                .setHighlighterType("experimental").addHighlightedField("test")
                .setSize(1);
    }

    protected SearchRequestBuilder setHitSource(SearchRequestBuilder search, String hitSource) {
        return search.setHighlighterOptions(ImmutableMap.<String, Object> of("hit_source",
                hitSource));
    }

    protected void buildIndex() throws IOException {
        buildIndex(true, true, between(1, 5));
    }

    protected void buildIndex(boolean offsetsInPostings, boolean fvhLikeTermVectors, int shards)
            throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject();
        mapping.startObject("test").startObject("properties");
        addField(mapping, "test", offsetsInPostings, fvhLikeTermVectors);
        addField(mapping, "test2", offsetsInPostings, fvhLikeTermVectors);
        mapping.startObject("foo").field("type").value("object").startObject("properties");
        addField(mapping, "test", offsetsInPostings, fvhLikeTermVectors);
        mapping.endObject().endObject().endObject().endObject();

        XContentBuilder settings = jsonBuilder().startObject().startObject("index");
        settings.field("number_of_shards", shards);
        settings.startObject("analysis");
        settings.startObject("analyzer");
        settings.startObject("chars").field("tokenizer", "chars").endObject();
        settings.endObject();
        settings.startObject("tokenizer");
        settings.startObject("chars").field("type", "pattern").field("pattern", "(.)")
                .field("group", 0).endObject();
        settings.endObject();
        settings.endObject().endObject();
        assertAcked(prepareCreate("test").setSettings(settings).addMapping("test", mapping));
        ensureYellow();
    }

    private void addField(XContentBuilder builder, String name, boolean offsetsInPostings,
            boolean fvhLikeTermVectors) throws IOException {
        builder.startObject(name).field("type", "string");
        addProperties(builder, offsetsInPostings, fvhLikeTermVectors);
        builder.startObject("fields");
        addSubField(builder, "whitespace", "whitespace", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "english", "english", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "english2", "english", offsetsInPostings, fvhLikeTermVectors);
        addSubField(builder, "chars", "chars", offsetsInPostings, fvhLikeTermVectors);
        builder.endObject().endObject();
    }

    private void addSubField(XContentBuilder builder, String name, String analyzer,
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

    protected void indexTestData() {
        indexTestData("tests very simple test");
    }

    protected void indexTestData(Object contents) {
        client().prepareIndex("test", "test", "1").setSource("test", contents).get();
        refresh();
    }

    /**
     * Enable plugin loading.
     */
    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return ImmutableSettings.builder().put(super.nodeSettings(nodeOrdinal))
                .put("plugins." + PluginsService.LOAD_PLUGIN_FROM_CLASSPATH, true).build();
    }

}
