package org.wikimedia.search.highlighter.experimental;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class Matchers {
    public static <T> Matcher<Segment> extracted(SourceExtracter<T> extracter, T t) {
        return extracted(extracter, equalTo(t));
    }

    public static <T> Matcher<Segment> extracted(SourceExtracter<T> extracter,
            Matcher<T> extractedMatcher) {
        return new ExtractedMatcher<T>(extracter, extractedMatcher);
    }

    public static <T> Matcher<HitEnum> hit(int position, SourceExtracter<T> extracter,
            Matcher<T> extractedMatcher) {
        return allOf(extracted(extracter, extractedMatcher), atPosition(position));
    }

    public static Matcher<HitEnum> isEmpty() {
        return new AdvancesMatcher(false);
    }

    public static Matcher<HitEnum> advances() {
        return new AdvancesMatcher(true);
    }

    public static Matcher<HitEnum> atPosition(int position) {
        return new PositionMatcher(position);
    }

    public static Matcher<HitEnum> atStartOffset(int startOffset) {
        return new StartOffsetMatcher(startOffset);
    }

    public static Matcher<HitEnum> atEndOffset(int endOffset) {
        return new EndOffsetMatcher(endOffset);
    }

    public static Matcher<HitEnum> atWeight(float score) {
        return new WeightMatcher(closeTo(score, 0.0001));
    }

    public static Matcher<HitEnum> atQueryWeight(float score) {
        return new QueryWeightMatcher(closeTo(score, 0.0001));
    }

    public static Matcher<HitEnum> atCorpusWeight(float score) {
        return new CorpusWeightMatcher(closeTo(score, 0.0001));
    }

    public static Matcher<HitEnum> atSource(int source) {
        return new SourceMatcher(source);
    }

    private static class AdvancesMatcher extends TypeSafeMatcher<HitEnum> {
        private final boolean advances;

        public AdvancesMatcher(boolean advances) {
            this.advances = advances;
        }

        @Override
        public void describeTo(Description description) {
            if (advances) {
                description.appendText("advances to the next term");
            } else {
                description.appendText("is now empty");
            }
        }

        @Override
        protected void describeMismatchSafely(HitEnum e, Description description) {
            if (advances) {
                description.appendText("but was empty");
            } else {
                description.appendText("but had another term at position")
                        .appendValue(e.position());
            }
        }

        @Override
        protected boolean matchesSafely(HitEnum e) {
            return e.next() == advances;
        }
    }

    private static class PositionMatcher extends TypeSafeMatcher<HitEnum> {
        private final int position;

        public PositionMatcher(int position) {
            this.position = position;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is at position ").appendValue(position);
        }

        @Override
        protected void describeMismatchSafely(HitEnum e, Description description) {
            description.appendText("but was at ").appendValue(e.position());
        }

        @Override
        protected boolean matchesSafely(HitEnum e) {
            return e.position() == position;
        }
    }

    private static class StartOffsetMatcher extends TypeSafeMatcher<HitEnum> {
        private final int startOffset;

        public StartOffsetMatcher(int startOffset) {
            this.startOffset = startOffset;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is at start offset ").appendValue(startOffset);
        }

        @Override
        protected void describeMismatchSafely(HitEnum e, Description description) {
            description.appendText("but was at ").appendValue(e.startOffset());
        }

        @Override
        protected boolean matchesSafely(HitEnum e) {
            return e.startOffset() == startOffset;
        }
    }

    private static class EndOffsetMatcher extends TypeSafeMatcher<HitEnum> {
        private final int endOffset;

        public EndOffsetMatcher(int endOffset) {
            this.endOffset = endOffset;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is at end offset ").appendValue(endOffset);
        }

        @Override
        protected void describeMismatchSafely(HitEnum e, Description description) {
            description.appendText("but was at ").appendValue(e.endOffset());
        }

        @Override
        protected boolean matchesSafely(HitEnum e) {
            return e.endOffset() == endOffset;
        }
    }

    private static class SourceMatcher extends TypeSafeMatcher<HitEnum> {
        private final int source;

        public SourceMatcher(int source) {
            this.source = source;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is at source ").appendValue(source);
        }

        @Override
        protected void describeMismatchSafely(HitEnum e, Description description) {
            description.appendText("but was at ").appendValue(e.source());
        }

        @Override
        protected boolean matchesSafely(HitEnum e) {
            return e.source() == source;
        }
    }

    private static class ExtractedMatcher<T> extends TypeSafeMatcher<Segment> {
        private final SourceExtracter<T> extracter;
        private final Matcher<T> extractedMatcher;

        public ExtractedMatcher(SourceExtracter<T> extracter, Matcher<T> extractedMatcher) {
            this.extracter = extracter;
            this.extractedMatcher = extractedMatcher;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("extract ");
            extractedMatcher.describeTo(description);
        }

        @Override
        protected void describeMismatchSafely(Segment segment, Description description) {
            description.appendText("but extracted ");
            extractedMatcher.describeMismatch(
                    extracter.extract(segment.startOffset(), segment.endOffset()), description);
        }

        @Override
        protected boolean matchesSafely(Segment segment) {
            return extractedMatcher.matches(extracter.extract(segment.startOffset(),
                    segment.endOffset()));
        }
    }

    private static class WeightMatcher extends TypeSafeMatcher<HitEnum> {
        private final Matcher<Double> weightMatcher;

        public WeightMatcher(Matcher<Double> weightMatcher) {
            this.weightMatcher = weightMatcher;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("at weight ");
            weightMatcher.describeTo(description);
        }

        @Override
        protected void describeMismatchSafely(HitEnum e, Description description) {
            description.appendText("but at weight ");
            weightMatcher.describeMismatch(e.queryWeight() * e.corpusWeight(), description);
        }

        @Override
        protected boolean matchesSafely(HitEnum e) {
            // It is easier to use the Double Matcher then make new float ones
            return weightMatcher.matches((double) e.queryWeight() * e.corpusWeight());
        }
    }

    private static class QueryWeightMatcher extends TypeSafeMatcher<HitEnum> {
        private final Matcher<Double> weightMatcher;

        public QueryWeightMatcher(Matcher<Double> weightMatcher) {
            this.weightMatcher = weightMatcher;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("at query weight ");
            weightMatcher.describeTo(description);
        }

        @Override
        protected void describeMismatchSafely(HitEnum e, Description description) {
            description.appendText("but at query weight ");
            weightMatcher.describeMismatch(e.queryWeight(), description);
        }

        @Override
        protected boolean matchesSafely(HitEnum e) {
            // It is easier to use the Double Matcher then make new float ones
            return weightMatcher.matches((double) e.queryWeight());
        }
    }

    private static class CorpusWeightMatcher extends TypeSafeMatcher<HitEnum> {
        private final Matcher<Double> weightMatcher;

        public CorpusWeightMatcher(Matcher<Double> weightMatcher) {
            this.weightMatcher = weightMatcher;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("at corpus weight ");
            weightMatcher.describeTo(description);
        }

        @Override
        protected void describeMismatchSafely(HitEnum e, Description description) {
            description.appendText("but at corpus weight ");
            weightMatcher.describeMismatch(e.corpusWeight(), description);
        }

        @Override
        protected boolean matchesSafely(HitEnum e) {
            // It is easier to use the Double Matcher then make new float ones
            return weightMatcher.matches((double) e.corpusWeight());
        }
    }
}
