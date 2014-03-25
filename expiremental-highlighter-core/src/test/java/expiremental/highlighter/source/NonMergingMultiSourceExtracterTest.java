package expiremental.highlighter.source;

import org.junit.Test;

import expiremental.highlighter.source.AbstractMultiSourceExtracter.Builder;

public class NonMergingMultiSourceExtracterTest extends AbstractMultiSourceExtracterTestBase {
    @Override
    protected Builder<String, ? extends Builder<String, ?>> builder(int offsetGap) {
        return NonMergingMultiSourceExtracter.builder(offsetGap);
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
