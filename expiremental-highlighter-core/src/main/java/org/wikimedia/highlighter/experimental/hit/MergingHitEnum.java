package org.wikimedia.highlighter.expiremental.hit;

import java.util.Collection;

import org.wikimedia.highlighter.expiremental.HitEnum;
import org.wikimedia.highlighter.expiremental.LessThan;
import org.wikimedia.highlighter.expiremental.extern.PriorityQueue;

/**
 * Merges multiple HitEnums.  They must all be sorted by the provided comparator or the results will be wrong.
 */
public class MergingHitEnum implements HitEnum {
    private final PriorityQueue<HitEnum> queue;
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
    public float weight() {
        return top.weight();
    }
    
    private static class HitEnumPriorityQueue extends PriorityQueue<HitEnum> {
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
    }
}
