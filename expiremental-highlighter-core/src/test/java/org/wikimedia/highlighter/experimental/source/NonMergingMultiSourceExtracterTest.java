package org.wikimedia.highlighter.expiremental.source;

import org.junit.Test;
import org.wikimedia.highlighter.expiremental.source.NonMergingMultiSourceExtracter;
import org.wikimedia.highlighter.expiremental.source.AbstractMultiSourceExtracter.Builder;

public class NonMergingMultiSourceExtracterTest extends AbstractMultiSourceExtracterTestBase {
    @Override
    protected Builder<String, ? extends Builder<String, ?>> builder(int offsetGap) {
        return NonMergingMultiSourceExtracter.builder(offsetGap);
    }

    @Test
    @Override
    public void merge() {
        try {
            build("a", "b").extract(0, 2 + offsetGap);
            fail("Expected exception");
        } catch(UnsupportedOperationException e) {
            //expected
        }
    }
}
