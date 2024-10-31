package org.wikimedia.search.highlighter.cirrus.hit;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.LessThan;
import org.wikimedia.search.highlighter.cirrus.extern.PriorityQueue;
import org.wikimedia.search.highlighter.cirrus.tools.GraphvizHitEnumGenerator;

/**
 * Merges multiple HitEnums.  They must all be sorted by the provided comparator or the results will be wrong.
 */
public class MergingHitEnum extends AbstractHitEnum {
    private final HitEnumPriorityQueue queue;
    private HitEnum top;

    public MergingHitEnum(Collection<? extends HitEnum> enums, LessThan<HitEnum> comparator) {
        queue = new HitEnumPriorityQueue(enums, comparator);
    }

    @Override
    public boolean next() {
        if (top == null) {
            top = queue.top();
        } else {
            if (top.next()) {
                top = queue.updateTop();
            } else {
                queue.pop();
                top = queue.top();
            }
        }

        return top != null;
    }

    @Override
    public int position() {
        return top.position();
    }

    @Override
    public int startOffset() {
        return top.startOffset();
    }

    @Override
    public int endOffset() {
        return top.endOffset();
    }

    @Override
    public float queryWeight() {
        return top.queryWeight();
    }

    @Override
    public float corpusWeight() {
        return top.corpusWeight();
    }

    @Override
    public int source() {
        return top.source();
    }

    @Override
    public String toString() {
        return queue.toString();
    }


    @Override
    public void toGraph(GraphvizHitEnumGenerator generator) {
        Map<String, Object> params = new HashMap<>();
        params.put("comparator", queue.lessThan);
        params.put("queueSize", queue.size());
        generator.addNode(this, params);
        Iterator<HitEnum> ite = queue.iterator();
        // Prints only 5 children
        for (int i = 0; i < 5 && ite.hasNext(); i++) {
            generator.addChild(this, ite.next());
        }
    }

    private static final class HitEnumPriorityQueue extends PriorityQueue<HitEnum> implements Iterable<HitEnum> {
        private final LessThan<HitEnum> lessThan;

        private HitEnumPriorityQueue(Collection<? extends HitEnum> hitEnums, LessThan<HitEnum> lessThan) {
            super(hitEnums.size());
            this.lessThan = lessThan;

            // Now that we've set that comparator we can add everything to the queue.
            for (HitEnum e: hitEnums) {
                if (e.next()) {
                    this.add(e);
                }
            }
        }

        @Override
        protected boolean lessThan(HitEnum a, HitEnum b) {
            return lessThan.lessThan(a, b);
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder(1000).append('[');
            Object[] objs = getHeapArray();
            boolean comma = false;
            for (Object obj : objs) {
                if (obj == null) {
                    continue;
                }
                if (comma) {
                    b.append(',');
                } else {
                    comma = true;
                }
                b.append(obj);
            }
            b.append(']');
            return b.toString();
        }

        public Iterator<HitEnum> iterator() {
            return new Iterator<HitEnum>() {
                int cur = 1;

                @Override
                public boolean hasNext() {
                    return cur <= size();
                }

                @Override
                public HitEnum next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return (HitEnum) getHeapArray()[cur++];
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("read-only iterator");
                }
            };
        }
    }
}
