package com.github.nik9000.expiremental.highlighter.source;

import org.junit.Test;

import com.github.nik9000.expiremental.highlighter.source.NonMergingMultiSourceExtracter;
import com.github.nik9000.expiremental.highlighter.source.AbstractMultiSourceExtracter.Builder;

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
