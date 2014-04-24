package org.wikimedia.search.highlighter.experimental.snippet;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.Snippet.Hit;

public class ExponentialSnippetWeigherTest {
    @Test
    public void noTerms() {
        assertEquals(0, weigh(1.1f), .00001f);
        assertEquals(0, weigh(2f), .00001f);
        assertEquals(0, weigh(-1f), .00001f);
    }

    @Test
    public void oneTerm() {
        assertEquals(1.1f, weigh(1.1f, hit(1f, 0)), .00001f);
        assertEquals(2f, weigh(2f, hit(1f, 0)), .00001f);
        assertEquals(-1f, weigh(-1f, hit(1f, 0)), .00001f);
    }

    @Test
    public void uniqueSources() {
        assertEquals(3.3, weigh(1.1f, hit(1f, 0), hit(1f, 1), hit(1f, 2)), .00001f);
        assertEquals(1.1 * 13, weigh(1.1f, hit(1f, 0), hit(5f, 1), hit(7f, 2)), .00001f);
    }

    @Test
    public void sameSources() {
        assertEquals(1.1 * 1.1 * 1.1, weigh(1.1f, hit(1f, 0), hit(1f, 0), hit(1f, 0)), .00001f);
    }

    @Test
    public void combo() {
        assertEquals(1.1 * 1.1 + 1.1, weigh(1.1f, hit(1f, 0), hit(1f, 0), hit(1f, 1)), .00001f);
    }

    private float weigh(float base, Hit... hits) {
        return new ExponentialSnippetWeigher(base).weigh(Arrays.asList(hits));
    }

    private Hit hit(float weight, int source) {
        return new Hit(0, 0, weight, source);
    }
}
