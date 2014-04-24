package org.elasticsearch.highlight;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.fuzzyQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchPhrasePrefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.regexpQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanFirstQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanNearQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanNotQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanOrQuery;
import static org.elasticsearch.index.query.QueryBuilders.spanTermQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHighlight;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNotHighlighted;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

public class ExperimentalHighlighterTest extends ElasticsearchIntegrationTest {
    private static final List<String> HIT_SOURCES = ImmutableList.of("postings", "vectors",
            "analyze");

    @Test
    public void singleTermQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch();
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void boolOfTermQueries() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(boolQuery().must(termQuery("test", "test")).must(
                termQuery("test", "simple")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests very <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void singlePhraseQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(matchPhraseQuery("test", "simple test"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests very <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void singlePhrasePrefixQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(matchPhrasePrefixQuery("test", "simple te"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            // You can see right here that we aren't careful with phrase queries
            // like the FVH is.
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void singleFuzzyQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(fuzzyQuery("test", "test"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleRangeQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(rangeQuery("test").from("teso").to("tesz"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleWildcardQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(wildcardQuery("test", "te?t"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }

        search = testSearch(wildcardQuery("test", "te*"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleRegexpQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(regexpQuery("test", "tests?"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanTermQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(spanTermQuery("test", "test"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanFirstQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(spanFirstQuery(spanTermQuery("test", "test"), 5));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanNearQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(spanNearQuery().slop(5)
                .clause(spanTermQuery("test", "tests")).clause(spanTermQuery("test", "test")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanNotQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(spanNotQuery().include(
                spanTermQuery("test", "test")).exclude(spanTermQuery("test", "tests")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void singleSpanOrQuery() throws IOException {
        buildIndex();
        indexTestData();

        SearchRequestBuilder search = testSearch(spanOrQuery()
                .clause(spanTermQuery("test", "test")).clause(spanTermQuery("test", "tests")));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            // Note that we really don't respect the spans - we basically just
            // convert it into a term query
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
        }
    }

    @Test
    public void multiValued() throws IOException {
        buildIndex();
        indexTestData(new String[] { "tests very simple test", "with two fields to test" });
        client().prepareIndex("test", "test", "2")
            .setSource("test", new String[] {"no match here", "this one"}, "fetched", new Integer[] {0, 1}).get();
        client().prepareIndex("test", "test", "3")
            .setSource("test", new String[] {"sentences.", "two sentences."}, "fetched", new Integer[] {0, 1}).get();
        refresh();

        SearchRequestBuilder search = testSearch();
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

        search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test.english").order("score"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("with two fields to <em>test</em>"));
        }

        search = testSearch(termQuery("test", "one"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("this <em>one</em>"));
        }

        search = testSearch(termQuery("test", "this"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>this</em> one"));
        }

        search = testSearch(termQuery("test", "sentences"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>sentences</em>."));
            assertHighlight(response, 0, "test", 1, equalTo("two <em>sentences</em>."));
        }
    }

    @Test
    public void sentenceFragmenter() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test sentence.");

        SearchRequestBuilder search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(3));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("And some other <em>test</em> sentence."));
        }
    }

    @Test
    public void noneFragmenter() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test sentence.");

        SearchRequestBuilder search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").fragmenter("none").numOfFragments(3));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
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

        SearchRequestBuilder search = testSearch(
                boolQuery().should(termQuery("test", "test")).should(termQuery("test", "foo")))
                .addHighlightedField(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                .setHighlighterOrder("score");
        Map<String, Object> options = new HashMap<String, Object>();
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("Junk junk junk junk junk junk junk "
                    + "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
            assertHighlight(response, 0, "test", 1,
                    equalTo("And some other <em>test</em> <em>test</em>.  "));
        }

        search.setHighlighterOrder("source");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1,
                    equalTo("And some other <em>test</em> <em>test</em>.  "));
        }

        options.put("top_scoring", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
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

        SearchRequestBuilder search = testSearch(
                boolQuery().should(termQuery("test", "test")).should(termQuery("test", "foo")))
                .addHighlightedField(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                .setHighlighterOrder("score");
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("boost_before", ImmutableMap.of("10", 4, "20", 2f));
        options.put("fragment_weigher", "sum");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("Junk junk junk junk junk junk junk " +
                    "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
        }

        // Should also apply when sorting by source using top_scoring
        search.setHighlighterOrder("source");
        options.put("top_scoring", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("Junk junk junk junk junk junk junk " +
                    "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
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

        SearchRequestBuilder search = testSearch(
                boolQuery().should(termQuery("test", "test")).should(termQuery("test", "foo")))
                .setHighlighterOrder("score");
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }
        
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("default_similarity", false);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>foo</em> <em>foo</em>"));
        }
        
        options.put("default_similarity", true);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }

        // And it still works when using top_scoring
        options.put("default_similarity", true);
        options.put("top_scoring", true);
        search.setHighlighterOrder("source");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>test</em>"));
        }
    }

    @Test
    public void matchedFields() throws IOException {
        buildIndex();
        indexTestData();

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
        indexTestData();
        SearchRequestBuilder search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test.english", "test.english2"));

        for (String hitSource : HIT_SOURCES) {
            setHitSource(search, hitSource);
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

        SearchRequestBuilder search = testSearch();
        for (String hitSource : HIT_SOURCES) {
            setHitSource(search, hitSource);
            if (hitSource.equals("analyze")) {
                SearchResponse response = search.get();
                assertNoFailures(response);
            } else {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(search, RestStatus.INTERNAL_SERVER_ERROR,
                        containsString("as a hit source without setting"));
            }
        }

        // Now with matched fields!
        search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").matchedFields("test", "test.english"));
        for (String hitSource : HIT_SOURCES) {
            setHitSource(search, hitSource);
            if (hitSource.equals("analyze")) {
                SearchResponse response = search.get();
                assertNoFailures(response);
            } else {
                // I wish I could throw an HTTP 400 here but I don't believe I
                // can.
                assertFailures(search, RestStatus.INTERNAL_SERVER_ERROR,
                        containsString("as a hit source without setting"));
            }
        }
    }
    
    @Test
    public void highlightWithoutOptionsDoesntBlowUp() throws IOException {
        buildIndex();
        indexTestData();

        assertNoFailures(testSearch().get());
        assertNoFailures(testSearch().setHighlighterOrder("score").get());
    }

    @Test
    public void dataInOtherFields() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test", "1")
                .setSource("test", "tests very simple test", "other",
                        "break me maybe?  lets make this pretty long tests").get();
        refresh();

        SearchRequestBuilder search = testSearch();
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void dataInOtherDocuments() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test", "2")
                .setSource("test", "break me maybe?  lets make this pretty long tests").get();
        indexTestData();

        SearchRequestBuilder search = testSearch();
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void noMatchesThisDocButMatchesOthers() throws IOException, InterruptedException,
            ExecutionException {
        buildIndex();
        // This is the doc we're looking for and it doesn't have a match in the
        // column we're highlighting
        client().prepareIndex("test", "test", "1")
                .setSource("test", "no match here", "find_me", "test").get();
        // These docs have a match in the column we're highlighting. We need a
        // bunch of them to make sure some end up in the same segment as what
        // we're looking for.
        List<IndexRequestBuilder> extra = new ArrayList<IndexRequestBuilder>();
        for (int i = 0; i < 100; i++) {
            extra.add(client().prepareIndex("test", "test", "other " + i).setSource("test", "test"));
        }
        indexRandom(true, extra);

        SearchRequestBuilder search = testSearch(termQuery("find_me", "test"));
        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertNotHighlighted(response, 0, "test");
        }
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
        SearchRequestBuilder search = testSearch(termQuery("find_me", "shortstring")).addHighlightedField(
                field);
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment"));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment this thing.  "));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));

        // No match on a longer one
        search = testSearch(termQuery("find_me", "longstring")).addHighlightedField(
                field);
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment"));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo("Lets segment a much longer "));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(longString));

        // No match size > string size
        field.noMatchSize(1000);
        search = testSearch(termQuery("find_me", "shortstring")).addHighlightedField(
                field);
        field.fragmenter("scan");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));
        field.fragmenter("sentence");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));
        field.fragmenter("none");
        assertHighlight(search.get(), 0, "test", 0, equalTo(shortString));

        // boundaryMaxScan + size > string size but size < string size
        field.noMatchSize(10).boundaryMaxScan(10000);
        search = testSearch(termQuery("find_me", "shortstring")).addHighlightedField(
                field);
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

        SearchRequestBuilder search = testSearch().addHighlightedField(
                new HighlightBuilder.Field("test").numOfFragments(0).fragmentSize(10));
        assertHighlight(search.get(), 0, "test", 0, equalTo("This <em>test</em> is "
                + "long enough to demonstrate that we switched to the whole field segmenter."));
    }

    @Test
    public void maxFragmentsScored() throws IOException {
        buildIndex();
        indexTestData("The quick brown fox jumped over the lazy test.  And some other test.  " +
                "Junk junk junk junk junk junk junk junk junk junk junk test test test.");

        SearchRequestBuilder search = testSearch(termQuery("test", "test"))
                .addHighlightedField(new HighlightBuilder.Field("test").fragmenter("sentence").numOfFragments(2))
                .setHighlighterOrder("score");
        Map<String, Object> options = new HashMap<String, Object>();

        // We find the top scoring fragment if it is within max_fragments_scored
        options.put("max_fragments_scored", 10);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("Junk junk junk junk junk junk junk " +
                    "junk junk junk junk <em>test</em> <em>test</em> <em>test</em>."));
            assertHighlight(response, 0, "test", 1, equalTo("And some other <em>test</em>.  "));
        }

        // We don't if it isn't
        options.put("max_fragments_scored", 2);
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("The quick brown fox jumped over the lazy <em>test</em>.  "));
            assertHighlight(response, 0, "test", 1, equalTo("And some other <em>test</em>.  "));
        }
    }

    @Test
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
        for (int i = 0 ; i < 200; i++) {
            nested.startObject().field("test").value("nested" + Integer.toString(i));
            if (i < 100) {
                nested.field("fetched").value(Integer.toString(i));
                nested.field("fetched2").value(Integer.toString(1000+i));
            }
            nested.endObject();
        }
        nested.endArray().endObject();
        client().prepareIndex("test", "test", "4").setSource(nested).get();
        refresh();

        SearchRequestBuilder search = testSearch(termQuery("test", "one"));
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("fetch_fields", new String[] {"fetched"});
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("this <em>one</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("1"));
        }

        search = testSearch(termQuery("test", "firstplace"));
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>firstplace</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("0"));
        }

        search = testSearch(termQuery("test", "nobuddy"));
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>nobuddy</em>"));
            assertHighlight(response, 0, "test", 1, equalTo(""));
        }

        search = testSearch(termQuery("foo.test", "nested99")).addHighlightedField("foo.test");
        options.put("fetch_fields", new String[] {"foo.fetched"});
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested99</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("99"));
        }

        search = testSearch(boolQuery().should(termQuery("foo.test", "nested99"))
                .should(termQuery("foo.test", "nested54"))).addHighlightedField("foo.test");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            // Score Ordered
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested54</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("54"));
            assertHighlight(response, 0, "foo.test", 2, equalTo("<em>nested99</em>"));
            assertHighlight(response, 0, "foo.test", 3, equalTo("99"));
        }

        search = testSearch(boolQuery().should(termQuery("foo.test", "nested54"))
                .should(termQuery("foo.test", "nested123"))).addHighlightedField("foo.test");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            // Score Ordered
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested54</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("54"));
            assertHighlight(response, 0, "foo.test", 2, equalTo("<em>nested123</em>"));
            assertHighlight(response, 0, "foo.test", 3, equalTo(""));
        }

        search = testSearch(termQuery("foo.test", "nested99")).addHighlightedField("foo.test");
        options.put("fetch_fields", new String[] {"foo.fetched", "foo.fetched2"});
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "foo.test", 0, equalTo("<em>nested99</em>"));
            assertHighlight(response, 0, "foo.test", 1, equalTo("99"));
            assertHighlight(response, 0, "foo.test", 2, equalTo("1099"));
        }
    }

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

    @Test
    public void highlightQuery() throws IOException {
        buildIndex();
        client().prepareIndex("test", "test").setSource("test", "foo", "test2", "bar").get();
        refresh();

        SearchRequestBuilder search = testSearch(termQuery("test", "foo")).addHighlightedField(
                new HighlightBuilder.Field("test2").highlightQuery(termQuery("test2", "bar")));

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = setHitSource(search, hitSource).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>foo</em>"));
            assertHighlight(response, 0, "test2", 0, equalTo("<em>bar</em>"));
        }
    }

    // TODO matched_fields with different hit source
    // TODO infer proper hit source
    
    /**
     * A simple search for the term "test".
     */
    private SearchRequestBuilder testSearch() {
        return testSearch(termQuery("test", "test"));
    }

    /**
     * A simple search for the term test.
     */
    private SearchRequestBuilder testSearch(QueryBuilder builder) {
        return client().prepareSearch("test").setTypes("test").setQuery(builder)
                .setHighlighterType("experimental").addHighlightedField("test")
                .setSize(1);
    }

    private SearchRequestBuilder setHitSource(SearchRequestBuilder search, String hitSource) {
        return search.setHighlighterOptions(ImmutableMap.<String, Object> of("hit_source",
                hitSource));
    }

    private void buildIndex() throws IOException {
        buildIndex(true, true, between(1, 5));
    }

    private void buildIndex(boolean offsetsInPostings, boolean fvhLikeTermVectors, int shards)
            throws IOException {
        XContentBuilder builder = jsonBuilder().startObject();
        builder.startObject("test").startObject("properties");
        addField(builder, "test", offsetsInPostings, fvhLikeTermVectors);
        addField(builder, "test2", offsetsInPostings, fvhLikeTermVectors);
        builder.startObject("foo").field("type").value("object").startObject("properties");
        addField(builder, "test", offsetsInPostings, fvhLikeTermVectors);
        builder.endObject().endObject().endObject().endObject();
        assertAcked(prepareCreate("test").setSettings(
                ImmutableMap.<String, Object> of("number_of_shards", shards)).addMapping("test",
                builder));
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

    private void indexTestData() {
        indexTestData("tests very simple test");
    }

    private void indexTestData(Object contents) {
        client().prepareIndex("test", "test", "1").setSource("test", contents).get();
        refresh();
    }

    private void checkFragmentWeigher(QueryBuilder query, String sumMatch, String exponentialMatch) throws IOException {
        buildIndex();
        indexTestData(new String[] {
                sumMatch.replaceAll("<em>", "").replaceAll("</em>", ""),
                exponentialMatch.replaceAll("<em>", "").replaceAll("</em>", ""),
        });

        SearchRequestBuilder search = testSearch(query)
                .addHighlightedField(new HighlightBuilder.Field("test").numOfFragments(2))
                .setHighlighterOrder("score");
        Map<String, Object> options = new HashMap<String, Object>();

        options.put("fragment_weigher", "sum");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo(sumMatch));
            assertHighlight(response, 0, "test", 1, equalTo(exponentialMatch));
        }

        options.put("fragment_weigher", "exponential");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo(exponentialMatch));
        }

        // Exponential is the default
        options.remove("fragment_weigher");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo(exponentialMatch));
        }

        // Adding a type doesn't break it.
        search.setTypes("test");
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.setHighlighterOptions(options).get();
            assertHighlight(response, 0, "test", 0, equalTo(exponentialMatch));
        }
    }
}
