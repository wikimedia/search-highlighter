package expiremental.highlighter.hit;

import org.junit.Test;
import static org.hamcrest.Matchers.*;
import static expiremental.highlighter.Matchers.*;
import static org.junit.Assert.*;

public class ReplayingHitEnumTest {
    @Test
    public void empty() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        assertThat(e, isEmpty());
    }

    @Test
    public void single() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        e.record(0, 0, 2);
        assertEquals(e.waiting(), 1);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2)));
        assertThat(e, isEmpty());
    }

    @Test
    public void aFew() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        e.record(0, 0, 2);
        e.record(0, 0, 2);
        e.record(0, 0, 2);
        assertEquals(e.waiting(), 3);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2)));
        assertEquals(e.waiting(), 2);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2)));
        assertEquals(e.waiting(), 1);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2)));
        assertThat(e, isEmpty());
        assertEquals(e.waiting(), 0);
    }
    
    @Test
    public void many() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        for (int i = 0; i < 10000; i++) {
            e.record(i, i, i);
        }
        assertEquals(e.waiting(), 10000);
        for (int i = 0; i < 10000; i++) {
            assertEquals(e.waiting(), 10000 - i);
            assertThat(e, advances());
            assertThat(e, allOf(atPosition(i), atStartOffset(i), atEndOffset(i)));
        }
        assertThat(e, isEmpty());
        assertEquals(e.waiting(), 0);
    }

    @Test
    public void restartable() {
        ReplayingHitEnum e = new ReplayingHitEnum();
        e.record(0, 0, 2);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(0), atStartOffset(0), atEndOffset(2)));
        assertThat(e, isEmpty());
        assertEquals(e.waiting(), 0);
        e.record(10, 4, 20);
        assertEquals(e.waiting(), 1);
        assertThat(e, advances());
        assertThat(e, allOf(atPosition(10), atStartOffset(4), atEndOffset(20)));
        assertThat(e, isEmpty());
    }
}
