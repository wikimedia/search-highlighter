package org.wikimedia.search.highlighter.experimental.source;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;
import org.wikimedia.search.highlighter.experimental.source.AbstractMultiSourceExtracter.Builder;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;

/**
 * Base class for tests for extensions to AbstractMultiSourceExtracter that can
 * extract Strings.
 */
@RunWith(RandomizedRunner.class)
public abstract class AbstractMultiSourceExtracterTestBase extends RandomizedTest {
    protected abstract Builder<String, ? extends Builder<String, ?>> builder(int offsetGap);

    protected SourceExtracter<String> extracter;
    protected int offsetGap;

    /**
     * Must be overridden to test the merge or lack of support for merging.
     */
    @Test
    public abstract void merge();

    protected SourceExtracter<String> build(String... s) {
        return build(rarely() ? between(0, 100) : 1, s);
    }

    protected SourceExtracter<String> build(int offsetGap, String... s) {
        this.offsetGap = offsetGap;
        Builder<String, ? extends Builder<String, ?>> builder = builder(offsetGap);
        for (String string : s) {
            builder.add(new StringSourceExtracter(string), string.length());
        }
        extracter = builder.build();
        return extracter;
    }

    @Test
    public void partOfSingleString() {
        assertEquals("fo", build("foo").extract(0, 2));
    }

    @Test
    public void allOfASingleString() {
        assertEquals("foo", build("foo").extract(0, 3));
    }

    @Test
    public void entirelyWithinOneString() {
        build("foo", "bar", "baz", "cupcakes");
        assertEquals("foo", extracter.extract(0, 3));
        assertEquals("bar", extracter.extract(3 + offsetGap, 6 + offsetGap));
        assertEquals("baz", extracter.extract(6 + offsetGap * 2, 9 + offsetGap * 2));
        assertEquals("cupcakes", extracter.extract(9 + offsetGap * 3, 17 + offsetGap * 3));
        assertEquals("up", extracter.extract(10 + offsetGap * 3, 12 + offsetGap * 3));
    }
    
    @Test
    public void endOfAString() {
        build("foo", "cupcakes");
        assertEquals("", extracter.extract(3, 3));
        assertEquals("", extracter.extract(11 + offsetGap, 11 + offsetGap));
    }

    @Test
    public void insideOffset() {
        build(100, "foo", "cupcakes");
        assertEquals("foo", extracter.extract(0, 3 + offsetGap - 1));
    }

    @Test
    public void outOfBounds() {
        try {
            build("foo").extract(100, 103);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
