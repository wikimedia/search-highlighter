package org.wikimedia.search.highlighter.experimental.hit.weight;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.hit.TermWeigher;
import org.wikimedia.search.highlighter.experimental.hit.weight.ExactMatchTermWeigher;

import static org.junit.Assert.*;

public class ExactMatchTermWeigherTest {
    @Test
    public void exactMatch() {
        Map<String, Float> matches = new HashMap<String, Float>();
        matches.put("one", 1f);
        matches.put("two", 2f);
        matches.put("three", 3f);
        TermWeigher<String> weigher = new ExactMatchTermWeigher<String>(matches, .5f);
        assertEquals(1f, weigher.weigh("one"), .001f);
        assertEquals(3f, weigher.weigh("three"), .001f);
        assertEquals(2f, weigher.weigh("two"), .001f);
        assertEquals(1, weigher.weigh("one"), .001f);
        assertEquals(.5f, weigher.weigh("notfound"), .001f);
    }
}
