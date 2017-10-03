package org.wikimedia.highlighter.experimental.elasticsearch.integration;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.junit.Test;
import org.wikimedia.highlighter.experimental.elasticsearch.AbstractExperimentalHighlighterIntegrationTestBase;

import com.google.common.collect.ImmutableMap;

/**
 * Tests for the query options that don't have their own test class.
 */
public class OptionsTest extends AbstractExperimentalHighlighterIntegrationTestBase {
    @Test
    public void sentenceFragmenter() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test sentence.");

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    field(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(3))
                    .andThen(hitSource(hitSource))).get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("And some other <em>test</em> sentence."));
        }
    }

    @Test
    public void noneFragmenter() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test sentence.");

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    field(new HighlightBuilder.Field("test").fragmenter("none").numOfFragments(3))
                    .andThen(hitSource(hitSource))).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "
                            + "And some other <em>test</em> sentence."));
        }
    }

    @Test
    public void ordering() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test test.  " +
                "Junk junk junk junk junk junk junk junk junk junk junk test test test.");

        QueryBuilder q = boolQuery().should(termQuery("test", "test")).should(termQuery("test", "foo"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    q,
                    field(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                    .andThen(order("score"))
                    .andThen(hitSource(hitSource))).get();
            assertHighlight(response, 0, "test", 0, equalTo("Junk junk junk junk junk junk junk "
                    + "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
            assertHighlight(response, 0, "test", 1,
                    equalTo("And some other <em>test</em> <em>test</em>.  "));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    q,
                    field(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                    .andThen(order("source"))
                    .andThen(hitSource(hitSource))).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1,
                    equalTo("And some other <em>test</em> <em>test</em>.  "));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    q,
                    field(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                    .andThen(option("top_scoring", true))
                    .andThen(hitSource(hitSource))).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("And some other <em>test</em> <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("Junk junk junk junk junk junk junk "
                    + "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
        }
    }

    @Test
    public void boostBefore() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test.  " +
                "Junk junk junk junk junk junk junk junk junk junk junk test test test.");

        QueryBuilder qb = boolQuery().should(termQuery("test", "test")).should(termQuery("test", "foo"));
        for (String hitSource : HIT_SOURCES) {

            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(option("boost_before", ImmutableMap.of("10", 4, "20", 2f)))
                        .andThen(option("fragment_weigher", "sum"))
                        .andThen(field(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2)))
                        .andThen(order("score")))
                    .get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("Junk junk junk junk junk junk junk " +
                    "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
        }

        // Should also apply when sorting by source using top_scoring
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(option("boost_before", ImmutableMap.of("10", 4, "20", 2f)))
                        .andThen(option("fragment_weigher", "sum"))
                        .andThen(option("top_scoring", true))
                        .andThen(field(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2)))
                        .andThen(order("source")))
                    .get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("Junk junk junk junk junk junk junk " +
                    "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(option("boost_before", ImmutableMap.of("10", 4, Integer.toString(Integer.MAX_VALUE), 0f)))
                        .andThen(option("fragment_weigher", "sum"))
                        .andThen(option("top_scoring", true))
                        .andThen(field(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2)))
                        .andThen(order("source")))
                    .get();
            assertHighlight(response, 0, "test", 0, /* total fragments */1,
                    equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
        }
    }

    @Test
    public void useDefaultSimilarity() throws IOException, InterruptedException, ExecutionException {
        buildIndex(true, true, 1);
        client().prepareIndex("test", "test").setSource("test", new String[] {"test", "foo foo"}).get();
        // We need enough "foo" so that a whole bunch end up on the shard with the above entry.
        List<IndexRequestBuilder> indexes = new ArrayList<IndexRequestBuilder>();
        for (int i = 0; i < 1000; i++) {
            indexes.add(client().prepareIndex("test", "test").setSource("test", "foo"));
        }
        indexRandom(true, indexes);

        QueryBuilder qb = boolQuery().should(termQuery("test", "test")).should(termQuery("test", "foo"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb, hitSource(hitSource).andThen(order("score"))).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(order("score"))
                        .andThen(option("default_similarity", false)))
                    .get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>foo</em> <em>foo</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(order("score"))
                        .andThen(option("default_similarity", true)))
                    .get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }

        // And it still works when using top_scoring
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(order("source"))
                        .andThen(option("top_scoring", true))
                        .andThen(option("default_similarity", true)))
                    .get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }
    }

    @Test
    public void matchedFields() throws IOException {
        buildIndex();
        indexTestData();

        // One matched field
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    hitSource(hitSource)
                        .andThen(field(new HighlightBuilder.Field("test").matchedFields("test.english"))))
                    .get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }

        // Two matched fields
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    hitSource(hitSource)
                        .andThen(field(new HighlightBuilder.Field("test").matchedFields("test", "test.english"))))
                    .get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    hitSource(hitSource)
                        .andThen(field(new HighlightBuilder.Field("test").matchedFields("test", "test.english",
                                "test.whitespace"))))
                    .get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void matchedFieldsSameAnalyzer() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(
                    hitSource(hitSource)
                        .andThen(field(new HighlightBuilder.Field("test").matchedFields("test.english", "test.english2"))));
            if (hitSource.equals("analyze")) {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(search, RestStatus.INTERNAL_SERVER_ERROR,
                        containsString("unique analyzer"));
            } else {
                SearchResponse response = search.get();
                assertHighlight(response, 0, "test", 0,
                        equalTo("<em>tests</em> very simple <em>test</em>"));
            }
        }
    }

    @Test
    public void settingHitSourceWithoutDataIsAnError() throws IOException {
        buildIndex(false, false, between(1, 5));
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(hitSource(hitSource));
            if (hitSource.equals("analyze")) {
                SearchResponse response = search.get();
                assertNoFailures(response);
            } else {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(search, RestStatus.BAD_REQUEST,
                        containsString("as a hit source without setting"));
            }
        }

        // Now with matched fields!
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(
                    hitSource(hitSource)
                    .andThen(field(new HighlightBuilder.Field("test").matchedFields("test", "test.english"))));
            if (hitSource.equals("analyze")) {
                SearchResponse response = search.get();
                assertNoFailures(response);
            } else {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(search, RestStatus.BAD_REQUEST,
                        containsString("as a hit source without setting"));
            }
        }
    }

    @Test
    public void highlightWithoutOptionsDoesntBlowUp() throws IOException {
        buildIndex();
        indexTestData();

        assertNoFailures(testSearch().get());
        assertNoFailures(testSearch(order("score")).get());
    }

    @Test
    public void noMatch() throws IOException {
        String shortString = "Lets segment this thing.  Yay.";
        String longString = "Lets segment a much longer sentence because "
                + "we really like long sentences but we shouldn't return "
                + "everything because that'd be too much don't you think?  Yes.";

        buildIndex();
        client().prepareIndex("test", "test", "short")
                .setSource("test", shortString, "find_me", "shortstring").get();
        client().prepareIndex("test", "test", "long")
                .setSource("test", longString, "find_me", "longstring").get();
        refresh();

        // No match on a short string
        HighlightBuilder.Field field = new HighlightBuilder.Field("test").noMatchSize(10);
        SearchRequestBuilder search = testSearch(termQuery("find_me", "shortstring"), field(field));
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment"));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment this thing.  "));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));

        // No match on a longer one
        search = testSearch(termQuery("find_me", "longstring"), field(field));
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment"));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment a much longer "));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(longString));

        // No match size > string size
        field.noMatchSize(1000);
        search = testSearch(termQuery("find_me", "shortstring"), field(field));
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));

        // boundaryMaxScan + size > string size but size < string size
        field.noMatchSize(10).boundaryMaxScan(10000);
        search = testSearch(termQuery("find_me", "shortstring"), field(field));
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment"));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment this thing.  "));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));
    }

    @Test
    public void zeroFragmentsReturnsWholeField() throws IOException {
        buildIndex();
        indexTestData("This test is long enough to demonstrate that we switched to the whole field segmenter.");

        SearchRequestBuilder search = testSearch(field(
                new HighlightBuilder.Field("test").numOfFragments(0).fragmentSize(10)));
        assertHighlight(search.get(), 0, "test", 0, equalTo("This <em>test</em> is "
                + "long enough to demonstrate that we switched to the whole field segmenter."));
    }

    @Test
    public void maxFragmentsScored() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test.  " +
                "Junk junk junk junk junk junk junk junk junk junk junk test test test.");


        // We find the top scoring fragment if it is within max_fragments_scored
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "test"),
                    field(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                        .andThen(order("score"))
                        .andThen(option("max_fragments_scored", 10))
                        .andThen(hitSource(hitSource))
                    ).get();
            assertHighlight(response, 0, "test", 0, equalTo("Junk junk junk junk junk junk junk " +
                    "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
            assertHighlight(response, 0, "test", 1, equalTo("And some other <em>test</em>.  "));
        }

        // We don't if it isn't
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "test"),
                    field(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                        .andThen(order("score"))
                        .andThen(option("max_fragments_scored", 2))
                        .andThen(hitSource(hitSource))
                    ).get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("And some other <em>test</em>.  "));
        }
    }

    @Test
    @SuppressWarnings("checkstyle:NPathComplexity")
    // This test should be split if we revisit it at some time. At this point
    // it tests multiple things and if the test is failing, it is not clear
    // what caused the failure. That being said, it is not worth refactoring
    // unless we actually have changes to make to that area of the code.
    public void fetchedFields() throws IOException, InterruptedException,
            ExecutionException {
        buildIndex();
        // This is the doc we're looking for and it doesn't have a match in the
        // column we're highlighting
        client().prepareIndex("test", "test", "1")
                .setSource("test", new String[] {"no match here", "this one"}, "fetched", new Integer[] {0, 1}).get();
        client().prepareIndex("test", "test", "2")
                .setSource("test", new String[] {"firstplace", "no match here"}, "fetched", new Integer[] {0, 1, 2}).get();
        client().prepareIndex("test", "test", "3")
                .setSource("test", new String[] {"no match here", "nobuddy"}, "fetched", new Integer[] {0}).get();
        XContentBuilder nested = jsonBuilder().startObject().startArray("foo");
        for (int i = 0; i < 200; i++) {
            nested.startObject().field("test").value("nested" + Integer.toString(i));
            if (i < 100) {
                nested.field("fetched").value(Integer.toString(i));
                nested.field("fetched2").value(Integer.toString(1000 + i));
            }
            nested.endObject();
        }
        nested.endArray().endObject();
        client().prepareIndex("test", "test", "4").setSource(nested).get();
        refresh();
        QueryBuilder qb = termQuery("test", "one");
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(option("fetch_fields", Collections.singletonList("fetched"))))
                    .get();
            assertHighlight(response, 0, "test", 0, equalTo("this <em>one</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("1"));
        }

        qb = termQuery("test", "firstplace");
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(option("fetch_fields", Collections.singletonList("fetched"))))
                    .get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>firstplace</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("0"));
        }

        qb = termQuery("test", "nobuddy");
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(option("fetch_fields", Collections.singletonList("fetched"))))
                    .get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>nobuddy</em>"));
            assertHighlight(response, 0, "test", 1, equalTo(""));
        }

        qb = termQuery("foo.test", "nested99");
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(option("fetch_fields", Collections.singletonList("foo.fetched")))
                        .andThen(field("foo.test")))
                    .get();
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested99</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("99"));
        }

        qb = boolQuery().should(termQuery("foo.test", "nested99"))
                .should(termQuery("foo.test", "nested54"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(field("foo.test"))
                        .andThen(option("fetch_fields", Collections.singletonList("foo.fetched"))))
                    .get();
            // Score Ordered
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested54</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("54"));
            assertHighlight(response, 0, "foo.test", 2, equalTo("<em>nested99</em>"));
            assertHighlight(response, 0, "foo.test", 3, equalTo("99"));
        }

        qb = boolQuery().should(termQuery("foo.test", "nested54"))
                .should(termQuery("foo.test", "nested123"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(field("foo.test"))
                        .andThen(option("fetch_fields", Collections.singletonList("foo.fetched"))))
                    .get();
            // Score Ordered
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested54</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("54"));
            assertHighlight(response, 0, "foo.test", 2, equalTo("<em>nested123</em>"));
            assertHighlight(response, 0, "foo.test", 3, equalTo(""));
        }

        qb = termQuery("foo.test", "nested99");
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(qb,
                    hitSource(hitSource)
                        .andThen(field("foo.test"))
                        .andThen(option("fetch_fields", Arrays.asList(new String[] {"foo.fetched", "foo.fetched2"}))))
                    .get();
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested99</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("99"));
            assertHighlight(response, 0, "foo.test", 2, equalTo("1099"));
        }
    }

    @Test
    public void highlightQuery() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test").setSource("test", "foo", "test2", "bar").get();
        refresh();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "foo"),
                    hitSource(hitSource)
                        .andThen(field(new HighlightBuilder.Field("test2")
                                .highlightQuery(termQuery("test2", "bar")))))
                    .get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>foo</em>"));
            assertHighlight(response, 0, "test2", 0, equalTo("<em>bar</em>"));
        }
    }

    @Test
    public void testDebugGraph() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test").setSource("test", "foo", "test2", "bar").get();
        refresh();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "foo"),
                    hitSource(hitSource)
                        .andThen(field(new HighlightBuilder.Field("test2").highlightQuery(termQuery("test2", "bar"))))
                        .andThen(option("return_debug_graph", true)))
                    .get();
            assertHighlight(response, 0, "test2", 0, containsString("digraph HitEnums"));
        }
    }

}
