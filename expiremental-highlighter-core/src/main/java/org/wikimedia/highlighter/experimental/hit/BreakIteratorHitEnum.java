package org.wikimedia.highlighter.expiremental.hit;

import java.text.BreakIterator;

import org.wikimedia.highlighter.expiremental.HitEnum;

/**
 * Implements a HitEnum with a BreakIterator and returns terms in the order they are in the text.
 */
public final class BreakIteratorHitEnum implements HitEnum {
	private final BreakIterator itr;
	private final HitWeigher weigher;
	private int position = -1;
	private int startOffset;
	private int endOffset;
	private float weight;

	public BreakIteratorHitEnum(BreakIterator itr, HitWeigher weigher) {
		this.itr = itr;
		this.weigher = weigher;
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
		if (endOffset == BreakIterator.DONE) {
		    return false;
		} else {
		    weight = weigher.weight(position, startOffset, endOffset);
		    return true;
		}
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
	
	@Override
	public float weight() {
	    return weight;
	}
}
