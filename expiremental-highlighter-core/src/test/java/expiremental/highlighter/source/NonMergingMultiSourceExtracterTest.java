package expiremental.highlighter.source;

import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.source.AbstractMultiSourceExtracter.ConstituentExtracter;

public class NonMergingMultiSourceExtracterTest extends AbstractMultiSourceExtracterTestBase {
    @Override
    protected SourceExtracter<String> build(List<ConstituentExtracter<String>> extracters) {
        return new NonMergingMultiSourceExtracter<String>(extracters);
    }

    @Test
    @Override
    public void merge() {
        try {
            build("a", "b").extract(0, 2);
            fail("Expected exception");
        } catch(UnsupportedOperationException e) {
            //expected
        }
    }
}
