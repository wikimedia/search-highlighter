package org.wikimedia.highlighter.cirrus.opensearch.integration;

import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.fuzzyQuery;
import static org.opensearch.index.query.QueryBuilders.idsQuery;
import static org.opensearch.index.query.QueryBuilders.matchPhrasePrefixQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.prefixQuery;
import static org.opensearch.index.query.QueryBuilders.queryStringQuery;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.opensearch.index.query.QueryBuilders.regexpQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.wildcardQuery;
import static org.opensearch.index.query.QueryBuilders.wrapperQuery;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHighlight;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHitCount;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertNoFailures;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertNotHighlighted;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.action.admin.indices.forcemerge.ForceMergeResponse;
import org.opensearch.action.bulk.BulkRequestBuilder;
import org.opensearch.action.index.IndexRequestBuilder;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.StopWatch;
import org.opensearch.common.Strings;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.junit.Test;
import org.wikimedia.highlighter.cirrus.opensearch.AbstractCirrusHighlighterIntegrationTestBase;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

/**
 * Miscellaneous integration test that don't really have a good home.
 */
public class MiscellaneousTest extends AbstractCirrusHighlighterIntegrationTestBase {
    @Test
    public void mixOfAutomataAndNotQueries() throws IOException {
        buildIndex();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(boolQuery().should(fuzzyQuery("test", "test"))
                    .should(fuzzyQuery("test", "simpl")), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very <em>simple</em> <em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(boolQuery()
                        .should(matchQuery("test", "test").fuzziness(Fuzziness.TWO))
                        .should(matchQuery("test", "simpl").fuzziness(Fuzziness.TWO)),
                    hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very <em>simple</em> <em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(boolQuery().should(fuzzyQuery("test", "test"))
                    .should(termQuery("test", "simple")), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very <em>simple</em> <em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(boolQuery()
                        .should(matchQuery("test", "test").fuzziness(Fuzziness.TWO))
                        .should(matchQuery("test", "simple").fuzziness(Fuzziness.TWO)),
                    hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very <em>simple</em> <em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(boolQuery().should(fuzzyQuery("test", "test"))
                    .should(termQuery("test", "simple")).should(termQuery("test", "very")), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> <em>very</em> <em>simple</em> <em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(boolQuery()
                        .should(matchQuery("test", "test").fuzziness(Fuzziness.TWO))
                        .should(termQuery("test", "simple"))
                        .should(termQuery("test", "very")),
                    hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> <em>very</em> <em>simple</em> <em>test</em>"));
        }
    }

    @Test
    public void multiValued() throws IOException {
        buildIndex();
        indexTestData(new String[] {"tests very simple test", "with two fields to test"});
        client().prepareIndex("test", "_doc", "2")
            .setSource("test", new String[] {"no match here", "this one"}, "fetched", new Integer[] {0, 1}).get();
        client().prepareIndex("test", "_doc", "3")
            .setSource("test", new String[] {"sentences.", "two sentences."}, "fetched", new Integer[] {0, 1}).get();
        refresh();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "test"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("with two fields to <em>test</em>"));
        }


        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "test"), hitSource(hitSource))
                    .highlighter(newHLBuilder().field(
                            new HighlightBuilder.Field("test").matchedFields("test.english"))
            ).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("with two fields to <em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    hitSource(hitSource)
                        .andThen(x -> x.field(new HighlightBuilder.Field("test").matchedFields("test.english")))
                        .andThen(x -> x.order("score"))
                    ).get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("<em>tests</em> very simple <em>test</em>"));
            assertHighlight(response, 0, "test", 1, equalTo("with two fields to <em>test</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "one"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("this <em>one</em>"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "this"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>this</em> one"));
        }

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("test", "sentences"), hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("<em>sentences</em>."));
            assertHighlight(response, 0, "test", 1, equalTo("two <em>sentences</em>."));
        }
    }



    @Test
    public void dataInOtherFields() throws IOException {
        buildIndex();
        client().prepareIndex("test", "_doc", "1")
                .setSource("test", "tests very simple test", "other",
                        "break me maybe?  lets make this pretty long tests").get();
        refresh();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void dataInOtherDocuments() throws IOException {
        buildIndex();
        client().prepareIndex("test", "_doc", "2")
                .setSource("test", "break me maybe?  lets make this pretty long tests").get();
        indexTestData();

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(hitSource(hitSource)).get();
            assertHighlight(response, 0, "test", 0, equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void noMatchesThisDocButMatchesOthers() throws IOException, InterruptedException {
        buildIndex();
        // This is the doc we're looking for and it doesn't have a match in the
        // column we're highlighting
        client().prepareIndex("test", "_doc", "1")
                .setSource("test", "no match here", "find_me", "test").get();
        // These docs have a match in the column we're highlighting. We need a
        // bunch of them to make sure some end up in the same segment as what
        // we're looking for.
        List<IndexRequestBuilder> extra = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            extra.add(client().prepareIndex("test", "_doc", "other " + i).setSource("test", "test"));
        }
        indexRandom(true, extra);

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(termQuery("find_me", "test"), hitSource(hitSource)).get();
            assertNotHighlighted(response, 0, "test");
        }
    }

    @Test
    public void manyTerms() throws IOException {
        buildIndex();
        indexTestData("a b c d e f g h i j");

        assertNoFailures(testSearch(
                boolQuery().should(termQuery("test", "a")).should(termQuery("test", "b"))
                        .should(termQuery("test", "c")).should(termQuery("test", "d"))
                        .should(termQuery("test", "e")).should(termQuery("test", "f"))
                        .should(termQuery("test", "g")).should(termQuery("test", "h"))
                        .should(termQuery("test", "i"))).get());
    }

    /**
     * This will catch really busted automatons like you can get with allow_mutate.
     */
    @Test
    @SuppressWarnings("checkstyle:LineLength") // yes, this is a test that requires a very, very long line
    public void whatIsLove() throws IOException {
        buildIndex();
        indexTestData("What-a-Mess is a series of children's books written by British comedy writer Frank Muir and illustrated by Joseph Wright. It was later made into an animated series in the UK in 1990 and again in 1995 by DIC Entertainment and aired on ABC in the United States. It aired on YTV from 1995 to 1999 in Canada. The title character is a disheveled (hence his nickname), accident-prone Afghan Hound puppy, whose real name was Prince Amir of Kinjan. Central Independent Television, the Independent Television contractor for the Midlands, created following the restructuring of ATV and commencing broadcast on 1 January 1982, Link Licensing & Bevanfield Films produced the first series and DIC Entertainment produced the second series. Both of them were narrated by Frank Muir.   What-a-Mess - A scruffy Afghan puppy in which is the main character of the entire franchise. His Breed name is Prince Amir of Kinjan, and has a yellow duck sitting on top of his head. In the US animated version, the duck was coloured blue, as if his character was merged with the blue bird in the UK animated version and books, and was also given a name by What-A-Mess called Baldwin. In the US animated version, What-A-Mess is voiced by Ryan O'Donohue. What-a-Mess's Mother - Also known as The Duchess of Kinjan is a beautiful pedigree Afghan Hound mother to What-a-Mess, and is voiced by Miriam Flynn in the US version. Archbishop of Canterbury - A scruffy dark blue dog with brown patches which What-A-Mess met and befriended in What-A-Mess Goes to the Seaside. He's named this way because when What-A-Mess introduces himself with his breed name he sarcastically replies \"Sure, and I'm the Archbishop of Canterbury!\", which the naive pup takes as his actual name. His name was changed to Norton in the US Animated Version, and he was voiced by Dana Hill. The Cat Next Door - Also known as Felicia in the US animated version, is a brown Siamese Cat that loves to tease What-A-Mess at times. In the US animated version, she was coloured blue and she was voiced by Jo Ann Harris Belson. Cynthia - A Hedgehog which What-A-Mess befriended in What-A-Mess Goes to School. Her character was redesigned to become a mole named Ramona in the US animated version, due to the fact that Hedgehogs aren't native to America. In the US animated version, she is voiced by Candi Milo. Trash - Only in the US animated version, Trash is a Bull Terrier who is a real trouble maker to What-A-Mess. His real name is actually Francis He is voiced by Joe Nipote. Frank - An Old English Sheepdog that narrates the US animated version of What-A-Mess, voiced by Frank Muir himself!   What-a-Mess What-a-Mess The Good What-a-Mess at the Seaside What-a-Mess Goes to School Prince What-a-Mess Super What-a-Mess What-a-Mess and the Cat Next Door What-a-Mess and the Hairy Monster  Four Seasons What-a-Mess in Spring What-a-Mess in Summer What-a-Mess in Autumn What-a-Mess in Winter Four Square Meals What-a-Mess has Breakfast What-a-Mess has Lunch What-a-Mess has Tea What-a-Mess has Supper Mini Books What-a-Mess has a Brain Wave What-a-Mess and Little Poppet What-a-Mess and a trip to the Vet What-a-Mess the Beautiful What-a-Mess Goes to Town What-a-Mess Goes on Television What-a-Mess Goes Camping   What-a-Mess Goes to the Seaside / 1990.03.26 What-a-Mess Goes to School / 1990.04.02 Prince What-a-Mess / 1990.04.09 Super What-a-Mess / 1990.04.16 What-a-Mess Keeps Cool / 1990.04.30 What-a-Mess and Cynthia the Hedgehog / 1990.05.14 What-a-Mess Has a Brain Wave! / 1990.05.21 What-a-Mess and the Cat Next Door / 1990.06.04 What-a-Mess and Little Poppet / 1990.06.18 What-a-Mess Goes Camping / 1990.07.02 What-a-Mess The Beautiful / 1990.07.09 What-a-Mess Goes to Town / 1990.07.16 What-a-Mess Goes to the Vet / 1990.07.23   Talkin' Trash (September 16, 1995) A Bone to Pick Midnight Snack Schoolin' Around The Legend of Junkyard Jones It's Raining Cats and Dogs Home Alone...Almost Super What-A-Mess The Recliner Afghan Holiday The Bone Tree Just Four More Left The Ropes What-A-Mess Has Breakfast Prize Puppy The Great Eascape The Scarecrow and Prince Amir Shampooed Show and Tail I Spy, I Cry, I Try What-A-Mess and the Hairy Monster Trick Or Treat My Teatime with Frank Out With the Garbage Dr. What-A-Mess Ultimate What-A-Mess This Hydrant Is Mine His Majesty, Prince What-A-Mess Trash's Wonderful Life Snowbound The Thanksgiving Turkey Santa What-A-Mess Here Comes Santa Paws All Around the Mallberry Bush What-A-Mess At the Movies His Royal Highness, Prince What-A-Mess Party at Poppet's Take Me Out to the Dog Park The Watch Out Dog Molenapped! Pound Pals Taste Test Slobber on a Stick Scout's Honor Seein' Double Luck on His Side What-A-Mess Keeps the Doctor Away There's No Business like Shoe Business Joy Rider Baldwin's Family Reunion Do the Mess Around On Vacation Messy Encounters Dog Days of Summer Fetch! Real Puppies Don't Meow Invasion of the Puppy Snatchers The Ballad of El Pero What-a-Mess Has Lunch Walking the Boy    Russell Williams, Imogen (4 July 2007). \"Whatever happened to What-a-mess?\". London: The Guardian. Retrieved 3 January 2011.   \"IMDB What-a-mess\". Retrieved 3 January 2011.   1990 series episode guide at the Big Cartoon DataBase");

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(queryStringQuery("what love?"), hitSource(hitSource)).get();
            assertNoFailures(response);
            assertHitCount(response, 1);
        }
    }

    @Test
    public void largeText() throws IOException {
        buildIndex();
        indexTestData(Resources.toString(Resources.getResource(this.getClass(), "large_text.txt"), Charsets.UTF_8));

        for (String hitSource : HIT_SOURCES) {
            SearchResponse response = testSearch(
                    termQuery("test", "browser"),
                    hitSource(hitSource)
                        .andThen(x -> x.field("test", 100).order("score"))
            ).get();
            assertHighlight(response, 0, "test", 0, equalTo("json (bug 61659) git #4d2209e " +
                    "- [<em>Browser</em> test] Headless <em>browser</em> test(s) (bug 53691) git #6a238d2 -"));
        }
    }

    @Test
    public void testSurrogate() throws IOException {
        // Regression test for https://github.com/wikimedia/search-highlighter/issues/32
        buildIndex();
        String testData = "Lorem ipsum dolor sit amet, consectetur, adipisci velit \uD83D\uDC4A" +
                "\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A" +
                "\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A" +
                "\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A" +
                "\uD83D\uDC4A\uD83D\uDC4A";
        indexTestData(testData);

        for (int i = 49; i < 51; i++) { // Test that fragmentSize of 49 and 50 brings same snippet (we do not cut surrogate pairs)
            for (String hitSource : HIT_SOURCES) {
                int fragmentSize = i;
                SearchResponse response = testSearch(
                        matchQuery("test", "velit"),
                        hitSource(hitSource)
                                .andThen(x -> x.field("test", fragmentSize).order("score"))
                ).get();
                assertHighlight(response, 0, "test", 0,
                        equalTo("consectetur, adipisci <em>velit</em> " +
                                "\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A" +
                                "\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A"));
            }
        }
    }

    @Test
    public void testSurrogateOnNoMatch() throws IOException {
        // Regression test for https://github.com/wikimedia/search-highlighter/issues/32
        buildIndex();
        String testData = "Lorem ipsum dolor sit amet, consectetur, adipisci velit \uD83D\uDC4A" +
                "\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A" +
                "\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A" +
                "\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A" +
                "\uD83D\uDC4A\uD83D\uDC4A";
        indexTestData(testData);

        for (int i = 76; i < 78; i++) { // Test that fragmentSize of 49 and 50 brings same snippet (we do not cut surrogate pairs)
            for (String hitSource : HIT_SOURCES) {
                int fragmentSize = i;
                SearchResponse response = testSearch(
                        matchQuery("test", "lorem"),
                        hitSource(hitSource)
                                .andThen(x -> x.field("test", fragmentSize)
                                        .highlightQuery(matchQuery("test", "notfound"))
                                        .noMatchSize(fragmentSize).order("score"))
                ).get();
                assertHighlight(response, 0, "test", 0,
                        equalTo("Lorem ipsum dolor sit amet, consectetur, adipisci velit " +
                                "\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A" +
                                "\uD83D\uDC4A\uD83D\uDC4A\uD83D\uDC4A"));
            }
        }
    }

    /**
     * Skipped until we have a way to verify something. It is useful for
     * spitting out performance information though.
     */
//    @Test
    public void lotsOfTerms() throws IOException {
        StopWatch watch = new StopWatch();
        watch.start("load");
        buildIndex(true, true, 1);
        for (char l1 = 'a'; l1 <= 'z'; l1++) {
            BulkRequestBuilder request = client().prepareBulk();
            for (char l2 = 'a'; l2 <= 'z'; l2++) {
                for (char l3 = 'a'; l3 <= 'z'; l3++) {
                    StringBuilder b = new StringBuilder();
                    for (char l4 = 'a'; l4 <= 'z'; l4++) {
                        b.append('z').append(l1).append(l2).append(l3).append(l4).append(' ');
                    }
                    request.add(client().prepareIndex("test", "_doc").setSource("test", b.toString()));
                }
            }
            request.get();
            logger.info("Sending for {}", l1);
        }
        refresh();
        // Optimizing to one segment makes the timing more consistent
        waitForRelocation();
        ForceMergeResponse actionGet = client().admin().indices().prepareForceMerge().setMaxNumSegments(1).execute().actionGet();
        assertNoFailures(actionGet);
        watch.stop();

        lotsOfTermsTestCase(watch, "warmup", fuzzyQuery("test", "zooom"));
        lotsOfTermsTestCase(watch, "single fuzzy", fuzzyQuery("test", "zooom"));
        lotsOfTermsTestCase(watch, "multiple fuzzy", boolQuery().should(fuzzyQuery("test", "zooom"))
                .should(fuzzyQuery("test", "zats")).should(fuzzyQuery("test", "zouni")));
        lotsOfTermsTestCase(watch, "multiple term", boolQuery().should(termQuery("test", "zooma"))
                .should(termQuery("test", "zats")).should(termQuery("test", "zouna")));
        lotsOfTermsTestCase(watch, "single term", termQuery("test", "zooma"));
        lotsOfTermsTestCase(watch, "fuzzy and term", boolQuery().should(fuzzyQuery("test", "zooma"))
                .should(termQuery("test", "zouna")));
        lotsOfTermsTestCase(watch, "two and two",
                boolQuery().should(fuzzyQuery("test", "zooms")).should(fuzzyQuery("test", "zaums"))
                        .should(termQuery("test", "zeesa")).should(termQuery("test", "zouqn")));
        lotsOfTermsTestCase(watch, "regexp", regexpQuery("test", "zo[om]mt"));
        lotsOfTermsTestCase(watch, "regexp and term", boolQuery().should(regexpQuery("test", "zo[azxo]my"))
                .should(termQuery("test", "zouny")));
        // Postings are really slow for stuff like "z*"
        lotsOfTermsTestCase(watch, "wildcard", wildcardQuery("test", "zap*"));
        lotsOfTermsTestCase(watch, "wildcard and term", boolQuery().should(wildcardQuery("test", "zap*"))
                .should(termQuery("test", "zouny")));
        lotsOfTermsTestCase(watch, "wildcard", prefixQuery("test", "zap"));
        lotsOfTermsTestCase(watch, "wildcard and term", boolQuery().should(prefixQuery("test", "zap"))
                .should(termQuery("test", "zouny")));
        // The boolQuery here is required because the test data doesn't contain
        // a single document that'll match the phrasePrefix
        lotsOfTermsTestCase(watch, "phrase prefix and term", boolQuery()
                .should(matchPhrasePrefixQuery("test", "zooma zoomb zoo"))
                .should(termQuery("test", "zooma")));
        lotsOfTermsTestCase(watch, "phrase prefix and term", queryStringQuery("test:\"zoooo\" OR test2:\"zaaap\""));

        logger.info(watch.prettyPrint());
    }

    private void lotsOfTermsTestCase(StopWatch watch, String name, QueryBuilder query) throws IOException {
        logger.info("starting {}", name);
        watch.start(name);
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(query, hitSource(hitSource));
            for (int i = 0; i < 10; i++) {
                SearchResponse response = search.get();
                assertHighlight(response, 0, "test", 0,
                        both(containsString("<em>z")).and(containsString("</em>")));
            }
        }
        watch.stop();

        logger.info("starting {} many highlighted fields", name);
        watch.start(String.format(Locale.ENGLISH, "%s many highlighted fields", name));
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(query, hitSource(hitSource)
                    .andThen(x -> x.field("test.english").field("test.english2").field("test2")));
            for (int i = 0; i < 10; i++) {
                SearchResponse response = search.get();
                assertHighlight(response, 0, "test", 0,
                        both(containsString("<em>z")).and(containsString("</em>")));
            }
        }
        watch.stop();

        logger.info("starting {} many queried fields", name);
        BoolQueryBuilder many = boolQuery();
        many.should(query);
        for (String field: new String[] {"test.english", "test.english2", "test2", "test2.english"}) {
            XContentBuilder builder = XContentBuilder.builder(JsonXContent.jsonXContent);
            query.toXContent(builder, null);
            many.should(wrapperQuery(
                    Strings.toString(builder).replaceAll("test", field)));
        }

        watch.start(String.format(Locale.ENGLISH, "%s many queried fields", name));
        for (String hitSource : HIT_SOURCES) {
            SearchRequestBuilder search = testSearch(many, hitSource(hitSource));
            for (int i = 0; i < 10; i++) {
                SearchResponse response = search.get();
                assertHighlight(response, 0, "test", 0,
                        both(containsString("<em>z")).and(containsString("</em>")));
            }
        }
        watch.stop();
    }

    /**
     * Asking for a matched_field that doesn't exist used to npe.
     */
    @Test
    public void missingMatchedField() throws IOException {
        buildIndex();
        indexTestData();

        SearchResponse response = client().prepareSearch("test")
                .setQuery(matchQuery("test", "very"))
                .highlighter(new HighlightBuilder().highlighterType("cirrus")
                        .field(new HighlightBuilder.Field("test").matchedFields("test", "doesntexist"))).get();
        assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> simple test"));
    }

    /**
     * There was a time when highlighting * would blow up because of _size being an empty numeric field.
     */
    @Test
    public void highlightStar() throws IOException {
        buildIndex();
        indexTestData();

        SearchResponse response = client().prepareSearch("test")
                .setQuery(matchQuery("custom_all", "very")).highlighter(new HighlightBuilder().highlighterType("cirrus")
                .field("*")).get();
        assertHighlight(response, 0, "test", 0, equalTo("tests <em>very</em> simple test"));
    }

    /**
     * max_expanded_terms should control how many terms we expand multi term
     * queries into when we expand multi term queries.
     */
    @Test
    public void singleRangeQueryWithSmallRewrites() throws IOException {
        buildIndex(true, true, 1);
        client().prepareIndex("test", "_doc", "2").setSource("test", "test").get();
        indexTestData();

        Map<String, Object> options = new HashMap<>();
        options.put("max_expanded_terms", 1);
        SearchRequestBuilder search = testSearch(boolQuery().must(rangeQuery("test").from("teso").to("tesz")).filter(idsQuery().addIds("1")),
                x -> x.options(options));
        for (String hitSource : HIT_SOURCES) {
            options.put("hit_source", hitSource);
            SearchResponse response = search.get();
            assertHighlight(response, 0, "test", 0,
                    equalTo("tests very simple <em>test</em>"));
        }
    }

    @Test
    public void returnOffsets() throws IOException {
        buildIndex();
        indexTestData();
        Map<String, Object> options = new HashMap<>();
        options.put("return_offsets", true);
        SearchResponse response = testSearch(matchQuery("test.english", "test"),
            x -> x.options(options).field("test.english")).get();
        assertHighlight(response, 0, "test.english", 0, equalTo("0:0-5,18-22:22"));
    }

    @Test
    public void offsetsAugmenter() throws IOException {
        buildIndex();
        indexTestData();
        Map<String, Object> options = new HashMap<>();
        options.put("return_snippets_and_offsets", true);
        SearchResponse response = testSearch(matchQuery("test.english", "test"),
            x -> x.options(options).field("test.english")).get();
        assertHighlight(response, 0, "test.english", 0, equalTo("0:0-5,18-22:22|<em>tests</em> very simple <em>test</em>"));
    }

    @Test
    public void offsetsAugmenterWithEmptyArray() throws IOException {
        buildIndex();
        indexTestData(Arrays.asList("", "after_empty_array"));
        Map<String, Object> options = new HashMap<>();
        options.put("return_snippets_and_offsets", true);
        SearchResponse response = testSearch(matchQuery("test.english", "after_empty_array"),
            x -> x.options(options).field("test.english")).get();
        assertHighlight(response, 0, "test.english", 0, equalTo("1:1-18:18|<em>after_empty_array</em>"));
    }

    @Test
    public void returnOffsetsMultiValued() throws IOException {
        buildIndex();
        indexTestData(ImmutableList.of("tests very simple test", "with more test"));
        Map<String, Object> options = new HashMap<>();
        options.put("return_offsets", true);
        SearchResponse response = testSearch(matchQuery("test.english", "test"),
            x -> x.options(options).field("test.english")).get();
        assertHighlight(response, 0, "test.english", 0, equalTo("0:0-5,18-22:22"));
        assertHighlight(response, 0, "test.english", 1, equalTo("23:33-37:37"));
    }

    public void testKeywordNormalizer() throws IOException {
        buildIndex();
        client().prepareIndex("test", "_doc", "1").setSource("keyword_field", "Héllö").get();
        refresh();
        SearchResponse response = testSearch(matchQuery("keyword_field", "Hèllô"),
                x -> x.field("keyword_field")).get();
        assertHighlight(response, 0, "keyword_field", 0, equalTo("<em>Héllö</em>"));
    }

    public void testPosIncGap() throws IOException {
        buildIndex();
        List<String> data = ImmutableList.of("one gap one", "gap");
        Map<String, Object> doc = new HashMap<>();
        doc.put("pos_gap_big", data);
        doc.put("pos_gap_small", data);
        client().prepareIndex("test", "_doc", "1").setSource(doc).get();
        refresh();
        SearchResponse resp = client().prepareSearch().setQuery(QueryBuilders.matchPhraseQuery("pos_gap_big", "one gap"))
                .highlighter(new HighlightBuilder()
                .highlighterType("cirrus")
                .field("pos_gap_big"))
            .get();
        assertEquals(1, resp.getHits().getHits().length);
        assertEquals(1, resp.getHits().getHits()[0].getHighlightFields().size());
        // The second "one gap" is not highlighted because of pos inc offset
        assertEquals(1, resp.getHits().getHits()[0].getHighlightFields().get("pos_gap_big").getFragments().length);

        resp = client().prepareSearch().setQuery(QueryBuilders.matchPhraseQuery("pos_gap_small", "one gap"))
                .highlighter(new HighlightBuilder()
                        .highlighterType("cirrus")
                        .field("pos_gap_small"))
                .get();
        assertEquals(1, resp.getHits().getHits().length);
        assertEquals(1, resp.getHits().getHits()[0].getHighlightFields().size());
        // The second "one gap" *is* highlighted because of pos inc offset set to 1 on the pos_gap_small field
        assertEquals(2, resp.getHits().getHits()[0].getHighlightFields().get("pos_gap_small").getFragments().length);
    }

    // TODO matched_fields with different hit source
    // TODO infer proper hit source
}
