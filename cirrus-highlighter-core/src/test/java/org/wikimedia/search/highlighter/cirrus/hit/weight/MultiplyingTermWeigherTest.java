package org.wikimedia.search.highlighter.cirrus.hit.weight;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.wikimedia.search.highlighter.cirrus.hit.TermWeigher;

public class MultiplyingTermWeigherTest {
    @Test
    public void multiplies() {
        TermWeigher<Object> weigher = new MultiplyingTermWeigher<Object>(
                new ConstantTermWeigher<>(3f), new ConstantTermWeigher<>(2f));
        assertEquals(6f, weigher.weigh(new Object()), .0001f);
    }

    @Test
    public void lazy() {
        TermWeigher<Object> weigher = new MultiplyingTermWeigher<>(
                new ConstantTermWeigher<>(0f), term -> {
            throw new RuntimeException("Blow up now");
        });
        assertEquals(0f, weigher.weigh(new Object()), .0001f);
    }
}
