package org.wikimedia.highlighter.cirrus.opensearch.integration;

import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertFailures;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHighlight;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHitCount;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertNotHighlighted;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.rest.RestStatus;
import org.junit.Test;
import org.opensearch.search.fetch.subphase.highlight.HighlightField;
import org.wikimedia.highlighter.cirrus.opensearch.AbstractCirrusHighlighterIntegrationTestBase;

import com.google.common.collect.ImmutableList;

/**
 * Tests for regex highlighting.
 */
public class RegexTest extends AbstractCirrusHighlighterIntegrationTestBase {
    @Test
    public void singleRegex() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "v.ry");
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource).andThen(options(options)));
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> simple <em>test</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource).andThen(options(options)));
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> simple test"));
        }
    }

    @Test
    public void extendedLuceneRegex() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "\\w+");
        options.put("skip_query", true);
        options.put("regex_flavor", "lucene_extended");
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource).andThen(options(options)));
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo(
                "<em>tests</em> <em>very</em> <em>simple</em> <em>test</em>"));
        }

        // no anchor support, no highlight
        options.put("regex", "^\\w+");
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource).andThen(options(options)));
            SearchResponse response = search.get();
            assertNotHighlighted(response, 0, "test");
        }
    }

    @Test
    public void anchoredLuceneRegex() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "^t[^\\s]+");
        options.put("skip_query", true);
        options.put("regex_flavor", "lucene_anchored");
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource).andThen(options(options)));
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>tests</em> very simple test"));
        }

        options.put("regex", "t[^\\s]+$");
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource).andThen(options(options)));
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void anchoredLuceneRegexOffsets() throws IOException {
        buildIndex();
        indexTestData(new String[]{"test data", "also test data"});
        Map<String, Object> options = new HashMap<>();
        options.put("skip_query", true);
        options.put("return_snippets_and_offsets", true);

        String field = "test";
        int hit = 0;

        String[] regexes = new String[]{"test", "^test", "^also", "data$", "t\\w+$", "t[^\\s]+$"};
        for (String regex : regexes) {
            options.put("regex", regex);
            for (String hitSource : HIT_SOURCES) {
                options.put("regex_flavor", "java");
                SearchRequestBuilder searchJava = testSearch(hitSource(hitSource).andThen(options(options)));
                SearchResponse responseJava = searchJava.get();
                HighlightField javaHighlight = responseJava.getHits().getAt(hit).getHighlightFields().get(field);

                options.put("regex_flavor", "lucene_anchored");
                SearchRequestBuilder searchExtended = testSearch(hitSource(hitSource).andThen(options(options)));
                SearchResponse responseExtended = searchExtended.get();
                HighlightField extendedHighlight = responseExtended.getHits().getAt(hit).getHighlightFields().get(field);

                assertEquals(regex, javaHighlight, extendedHighlight);
            }
        }
    }

    @Test
    public void multipleRegexes() throws IOException {
        buildIndex();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("regex", ImmutableList.of("v.ry", "simple"));
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource).andThen(options(options)));
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> <em>simple</em> <em>test</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource).andThen(options(options)));
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
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource).andThen(options(options)));
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> <em>simple</em> <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("<em>simple</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource).andThen(options(options)));
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
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(options(options).andThen(hitSource(hitSource)));
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests <em>very</em> simple <em>test</em>"));
        }

        // Now try regex without the rest of the query
        options.put("skip_query", true);
        for (String hitSource : HIT_SOURCES) {
            // The hit source shouldn't matter, but just in case
            SearchRequestBuilder search = testSearch(options(options).andThen(hitSource(hitSource)));
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
        SearchRequestBuilder search = testSearch(options(options));
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
            SearchRequestBuilder search = testSearch(options(options));
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
            SearchRequestBuilder search = testSearch(options(options));
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
            SearchRequestBuilder search = testSearch(options(options));
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0, equalTo("test {{lang|ar|الخلافة الراشدة}}\n|conventional_<em>long</em>_name"));
        }

        options.put("regex", "خلا");
        for (String flavor: new String[] {"java", "lucene"}) {
            options.put("regex_flavor", flavor);
            SearchRequestBuilder search = testSearch(options(options));
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
        assertFailures(testSearch(options(options)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Determinizing [^]]*alt=[^]\\|}]{80,} would require more than"));
        // Some regexes with explosive state growth still run because they
        // don't explode into too many states.
        options.put("regex", ".*te*s[tabclse]{1,16}.*");
        SearchResponse response = testSearch(options(options)).get();
        assertHitCount(response, 1);
        // But you can stop them by lowering max_determinized_states
        options.put("regex", ".*te*s[tabcse]{1,16}.*");
        options.put("max_determinized_states", 100);
        assertFailures(testSearch(options(options)),
                RestStatus.INTERNAL_SERVER_ERROR, containsString("Determinizing .*te*s[tabcse]{1,16}.* would require more than 100"));
        // Its unfortunate that this comes back as an INTERNAL_SERVER_ERROR but
        // I can't find any way from here to mark it otherwise.
    }

    @Test
    public void overlapMerge() throws IOException {
        buildIndex();
        indexTestData("This test has overlapping highlights.");

        Map<String, Object> options = new HashMap<>();
        options.put("regex", "T.+\\.");
        SearchRequestBuilder search = testSearch(options(options));
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
        SearchRequestBuilder search = testSearch(options(options).andThen(fragmentSize(30)));
        SearchResponse response = search.get();
        assertNotHighlighted(response, 0, "test");
    }

    @Test
    public void multiByteMultiCodePoint() throws IOException {
        buildIndex(); // only need to build the index once
        multiByteMultiCodePointHelper("ABCDEXYZ"); // plain ASCII (1 byte)
        multiByteMultiCodePointHelper("ĂƁĆĐĖ"); // extended ASCII (2 bytes)
        multiByteMultiCodePointHelper("αбγдε"); // Greek & Cyrillic (2 bytes)
        multiByteMultiCodePointHelper("ႠႡႢႣႤ"); // Georgian (3 bytes)
        multiByteMultiCodePointHelper("ᴁᴃᴄᴆᴈ"); // Phonetic Extensions (3 bytes)
        multiByteMultiCodePointHelper("𐌀𐌁𐌂𐌃𐌄𐌅𐌆"); // Old Italic (4 bytes / surrogates)
        multiByteMultiCodePointHelper("𐌰𐌱𐌲𐌳𐌴"); // Gothic (4 bytes / surrogates)
        multiByteMultiCodePointHelper("\udbff\udffa\udbff\udffb\udbff\udffc\udbff\udffd\udbff\udffe");
            // Private Use Area B, U+10FFFA - U+10FFFE (4 bytes / surrogates)
        multiByteMultiCodePointHelper("AБᴄ𐌃𐌴"); // Mix-n-match (1, 2, 3, 4, 4 bytes)
        multiByteMultiCodePointHelper("𐌰ᴃγde"); // Mix-n-match (4, 3, 2, 1, 1 bytes)
    }

    protected void multiByteMultiCodePointHelper(String myStr) throws IOException {
        int[] letters = myStr.codePoints().toArray();
        assert letters.length > 4; // should be at least 5 characters, with no repeats

        // add the first two letters to the end of the string, in reverse order,
        // and reinit letters codepoints array
        myStr = myStr + new String(letters, 1, 1) + new String(letters, 0, 1); // "ABCDEBA"
        letters = myStr.codePoints().toArray();

        String testStr = "test";
        String openEm = "<em>";
        String closeEm = "</em>";
        String testData = testStr + " " + myStr; // "test ABCDEBA"
        indexTestData(testData);

        // general regex search settings
        Map<String, Object> options = new HashMap<>();
        options.put("skip_query", true);
        options.put("locale", "en_US");
        options.put("regex_case_insensitive", false);
        String[] flavors = new String[] {"java", "lucene"};

        // test matching BA, AB, BC, and CD
        // BA (offset == -1) is a single match at the end of the string
        // AB is a single match with a partial match at the end of the string
        // BC is a single match with a partial match in the middle of the string
        // CD is a single match
        for (int i = -1; i < 3; i++) {
            // i indexes the nth bigram, with negative numbers counting from the end
            int offset = (i < 0) ? letters.length + i - 1 : i;
            String preMatch = new String(letters, 0, offset);
            String regex = new String(letters, offset, 2);
            String postMatch = new String(letters, offset + 2, letters.length - offset - 2);
            String result = testStr + " " + preMatch + openEm + regex + closeEm + postMatch;

            options.put("regex", regex);
            for (String flavor: flavors) {
                options.put("regex_flavor", flavor);
                SearchRequestBuilder search = testSearch(options(options));
                SearchResponse response = search.get();
                assertHighlight(response, 0, testStr, 0, equalTo(result));
            }
        }
    }
}
