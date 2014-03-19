package expiremental.highlighter.hit;

import java.util.Collection;

import expiremental.highlighter.LessThan;
import expiremental.highlighter.WeightedHitEnum;
import expiremental.highlighter.extern.PriorityQueue;

/**
 * Merges multiple WeightedHitEnums.  They must all be sorted by the provided comparator.
 */
public class MergingHitEnum implements WeightedHitEnum {
    private final PriorityQueue<WeightedHitEnum> queue;   // Introduces Lucene dependency in the core.  Might should just copy that one locally.
    private WeightedHitEnum top;
    
    /**
     * Build me.
     * @param enums enums to merge
     * @param comparator comparators that compares all the WeightedHitEnum
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public MergingHitEnum(Collection<WeightedHitEnum> enums, LessThan comparator) {
        queue = new HitEnumPriorityQueue(enums, (LessThan<WeightedHitEnum>) comparator);
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
    
    private static class HitEnumPriorityQueue extends PriorityQueue<WeightedHitEnum> {
        private final LessThan<WeightedHitEnum> lessThan;
        
        private HitEnumPriorityQueue(Collection<WeightedHitEnum> hitEnums, LessThan<WeightedHitEnum> lessThan) {
            super(hitEnums.size());
            this.lessThan = lessThan;
            
            // Now that we've set that comparator we can add everything to the queue.
            for (WeightedHitEnum e: hitEnums) {
                if (e.next()) {
                    this.add(e);
                }
            }
        }

        @Override
        protected boolean lessThan(WeightedHitEnum a, WeightedHitEnum b) {
            return lessThan.lessThan(a, b);
        }
    }
}
