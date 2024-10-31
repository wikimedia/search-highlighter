package org.wikimedia.search.highlighter.cirrus.source;

import static org.junit.Assert.assertEquals;

import org.wikimedia.search.highlighter.cirrus.SourceExtracter;
import org.wikimedia.search.highlighter.cirrus.source.AbstractMultiSourceExtracter.Builder;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;

public class StringMergingMultiSourceExtracterTest extends AbstractMultiSourceExtracterTestBase {
    @Override
    protected Builder<String, ? extends Builder<String, ?>> builder(int offsetGap) {
        return StringMergingMultiSourceExtracter.builder(Strings.repeat(" ", offsetGap));
    }

    @Override
    public void merge() {
        SourceExtracter<String> extracter = build("foo", "bar", "baz", "cupcakes");
        Joiner joiner = Joiner.on(Strings.repeat(" ", offsetGap));
        assertEquals(joiner.join("foo", "bar"), extracter.extract(0, 6 + offsetGap));
        assertEquals(joiner.join("foo", "bar", "baz", "cupcakes"), extracter.extract(0, 17 + offsetGap * 3));
        assertEquals(joiner.join("z", "cupcake"), extracter.extract(8 + offsetGap * 2, 16 + offsetGap * 3));
    }
}
