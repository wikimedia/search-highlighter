package expiremental.highlighter.hit;

import java.text.BreakIterator;

import expiremental.highlighter.HitEnum;

/**
 * Implements a HitEnum with a BreakIterator and returns terms in the order they are in the text.
 */
public final class BreakIteratorHitEnum implements HitEnum {
	private final BreakIterator itr;
	private int position = -1;
	private int startOffset;
	private int endOffset;

	public BreakIteratorHitEnum(BreakIterator itr) {
		this.itr = itr;
		startOffset = itr.first();
	}

	@Override
	public boolean next() {
	    if (position == -1) {
	        endOffset = itr.next();    
	    } else {
	        startOffset = itr.next();
	        endOffset = itr.next();
	    }
		position++;
		return endOffset != BreakIterator.DONE;
	}

	@Override
	public int position() {
		return position;
	}

	@Override
	public int startOffset() {
		return startOffset;
	}

	@Override
	public int endOffset() {
		return endOffset;
	}
}
