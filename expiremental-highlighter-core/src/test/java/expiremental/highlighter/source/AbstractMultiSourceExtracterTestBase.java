package expiremental.highlighter.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.source.AbstractMultiSourceExtracter.ConstituentExtracter;

/**
 * Base class for tests for extensions to AbstractMultiSourceExtracter that can
 * extract Strings.
 */
public abstract class AbstractMultiSourceExtracterTestBase {
    protected abstract SourceExtracter<String> build(List<ConstituentExtracter<String>> extracters);

    /**
     * Must be overridden to test the merge or lack of support for merging.
     */
    @Test
    public abstract void merge();

    protected SourceExtracter<String> build(String... s) {
        List<ConstituentExtracter<String>> extracters = new ArrayList<ConstituentExtracter<String>>();
        for (String string : s) {
            extracters.add(new ConstituentExtracter<String>(new StringSourceExtracter(string),
                    string.length()));
        }
        return build(extracters);
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
        SourceExtracter<String> extracter = build("foo", "bar", "baz", "cupcakes");
        assertEquals("foo", extracter.extract(0, 3));
        assertEquals("bar", extracter.extract(3, 6));
        assertEquals("baz", extracter.extract(6, 9));
        assertEquals("cupcakes", extracter.extract(9, 17));
        assertEquals("up", extracter.extract(10, 12));
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
