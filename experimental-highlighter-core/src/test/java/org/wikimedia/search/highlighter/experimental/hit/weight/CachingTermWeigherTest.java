package org.wikimedia.search.highlighter.experimental.hit.weight;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.hit.TermWeigher;
import org.wikimedia.search.highlighter.experimental.hit.weight.CachingTermWeigher;


public class CachingTermWeigherTest {
    @Test
    public void caches() {
        TermWeigher<Object> weigher = new CachingTermWeigher<Object>(
                new TermWeigher<Object>() {
                    private int callCount = 0;
                    @Override
                    public float weigh(Object term) {
                        if (callCount > 0) {
                            throw new RuntimeException("Blow up now");
                        }
                        callCount++;
                        return 6f;
                    }
                });
        assertEquals(6f, weigher.weigh(new Object()), .0001f);
    }
}
