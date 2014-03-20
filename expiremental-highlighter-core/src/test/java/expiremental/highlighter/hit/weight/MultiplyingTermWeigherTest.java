package expiremental.highlighter.hit.weight;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import expiremental.highlighter.hit.TermWeigher;

public class MultiplyingTermWeigherTest {
    @Test
    public void multiplies() {
        TermWeigher<Object> weigher = new MultiplyingTermWeigher<Object>(
                new ConstantTermWeigher<Object>(3f), new ConstantTermWeigher<Object>(2f));
        assertEquals(6f, weigher.weigh(new Object()), .0001f);
    }

    @Test
    public void lazy() {
        TermWeigher<Object> weigher = new MultiplyingTermWeigher<Object>(
                new ConstantTermWeigher<Object>(0f), new TermWeigher<Object>() {
                    @Override
                    public float weigh(Object term) {
                        throw new RuntimeException("Blow up now");
                    }
                });
        assertEquals(0f, weigher.weigh(new Object()), .0001f);
    }
}
