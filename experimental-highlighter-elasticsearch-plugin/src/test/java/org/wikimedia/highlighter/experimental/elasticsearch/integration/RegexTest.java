package org.wikimedia.highlighter.experimental.elasticsearch.integration;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNotHighlighted;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.rest.RestStatus;
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

    @Test
    public void maxDeterminizedStatesLimitsComplexityOfLuceneRegex() throws IOException {
        buildIndex();
        indexTestData("test");
        // The default is good enough to prevent craziness

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "[^]]*alt=[^]\\|}]{80,}");
        options.put("skip_query", true);
        options.put("locale", "en_US");
        options.put("regex_case_sensitive", true);
        options.put("regex_flavor", "lucene");
        assertFailures(testSearch().setHighlighterOptions(options),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Determinizing [^]]*alt=[^]\\|}]{80,} would result in more than"));
        // Some regexes with explosive state growth still run because they
        // don't explode into too many states.
        options.put("regex", ".*te*s[tabclse]{1,16}.*");
        SearchResponse response = testSearch().setHighlighterOptions(options).get();
        assertHitCount(response, 1);
        // But you can stop them by lowering max_determinized_states
        options.put("regex", ".*te*s[tabcse]{1,16}.*");
        options.put("max_determinized_states", 100);
        assertFailures(testSearch().setHighlighterOptions(options),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Determinizing .*te*s[tabcse]{1,16}.* would result in more than 100"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.
    }

    @Test
    public void overlapMerge() throws IOException {
        buildIndex();
        indexTestData("This test has overlapping highlights.");

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "T.+\\.");
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options);
        SearchResponse response = search.get();
        assertHighlight(response, 0, "test", 0,
                equalTo("<em>This test has overlapping highlights.</em>"));
    }

    @Test
    public void longResultAreNotReturned() throws IOException {
        buildIndex();
        indexTestData("This test is much longer than the window but we match all of it. Isn't that a shame?");

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "T.+shame\\?");
        SearchRequestBuilder search = testSearch().setHighlighterOptions(options).setHighlighterFragmentSize(30);
        SearchResponse response = search.get();
        assertNotHighlighted(response, 0, "test");
    }
}
