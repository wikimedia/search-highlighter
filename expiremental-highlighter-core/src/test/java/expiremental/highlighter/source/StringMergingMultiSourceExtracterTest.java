package expiremental.highlighter.source;

import static org.junit.Assert.assertEquals;

import java.util.List;

import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.source.AbstractMultiSourceExtracter.ConstituentExtracter;

public class StringMergingMultiSourceExtracterTest extends AbstractMultiSourceExtracterTestBase {
    @Override
    protected SourceExtracter<String> build(List<ConstituentExtracter<String>> extracters) {
        return new StringMergingMultiSourceExtracter(extracters, " ");
    }

    @Override
    public void merge() {
        SourceExtracter<String> extracter = build("foo", "bar", "baz", "cupcakes");
        assertEquals("foo bar", extracter.extract(0, 6));
        assertEquals("foo bar baz cupcakes", extracter.extract(0, 17));
        assertEquals("z cupcake", extracter.extract(8, 16));
    }
}
