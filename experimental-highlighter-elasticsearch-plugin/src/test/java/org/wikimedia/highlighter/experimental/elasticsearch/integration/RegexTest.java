package org.wikimedia.highlighter.experimental.elasticsearch.integration;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.collect.ImmutableList;
import org.junit.Test;
import org.wikimedia.highlighter.experimental.elasticsearch.AbstractExperimentalHighlighterIntegrationTestBase;

/**
 * Tests for regex highlighting.
 */
public class RegexTest extends AbstractExperimentalHighlighterIntegrationTestBase {
    @Test
    public void singleRegex() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "v.ry");
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> simple <em>test</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> simple test"));
        }
    }

    @Test
    public void multipleRegexes() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", ImmutableList.of("v.ry", "simple"));
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> <em>simple</em> <em>test</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> <em>simple</em> test"));
        }
    }

    @Test
    public void multipleRegexesMultipleValues() throws IOException {
        buildIndex();
        indexTestData(ImmutableList.of("tests very simple test", "simple"));

        Map<String, Object> options = new HashMap<>();
        options.put("regex", ImmutableList.of("v.ry", "si.*le"));
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> <em>simple</em> <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("<em>simple</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> <em>simple</em> test"));
            assertHighlight(response, 0, "test", 1, equalTo("<em>simple</em>"));
        }
    }

    @Test
    public void javaRegex() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "v\\wry");
        options.put("regex_flavor", "java");
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> simple <em>test</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            // The hit source shouldn't matter, but just in case
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> simple test"));
        }
    }

    @Test
    public void unboundedRegex() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "v.*");
        options.put("skip_query", true);
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        SearchResponse response = search.get();
        assertHighlight(response, 0, "test", 0, equalTo("tests <em>very simple test</em>"));
    }

    @Test
    public void insenitiveRegex() throws IOException {
        buildIndex();
        indexTestData("TEsts VEry simPLE tesT");

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "verY");
        options.put("skip_query", true);
        options.put("locale", "en_US");
        options.put("regex_case_insensitive", true);
        for (String flavor: new String[] {"java", "lucene"}) {
            options.put("regex_flavor", flavor);
            SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("TEsts <em>VEry</em> simPLE tesT"));
        }
    }

    @Test
    public void insenitiveManyRegex() throws IOException {
        buildIndex();
        indexTestData("TEsts VEry simPLE tesT");

        Map<String, Object> options = new HashMap<>();
        options.put("regex", ImmutableList.of("verY", "teSt.?"));
        options.put("skip_query", true);
        options.put("locale", "en_US");
        options.put("regex_case_insensitive", true);
        for (String flavor: new String[] {"java", "lucene"}) {
            options.put("regex_flavor", flavor);
            SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>TEsts</em> <em>VEry</em> simPLE <em>tesT</em>"));
        }
    }

    @Test
    public void multiByte() throws IOException {
        buildIndex();
        indexTestData("test {{lang|ar|الخلافة الراشدة}}\n|conventional_long_name");

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "long");
        options.put("skip_query", true);
        options.put("locale", "en_US");
        options.put("regex_case_insensitive", true);
        for (String flavor: new String[] {"java", "lucene"}) {
            options.put("regex_flavor", flavor);
            SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("test {{lang|ar|الخلافة الراشدة}}\n|conventional_<em>long</em>_name"));
        }

        options.put("regex", "خلا");
        for (String flavor: new String[] {"java", "lucene"}) {
            options.put("regex_flavor", flavor);
            SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("test {{lang|ar|ال<em>خلا</em>فة الراشدة}}\n|conventional_long_name"));
        }
    }
}
