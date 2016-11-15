package org.wikimedia.highlighter.experimental.lucene.spanvalidation;

import org.apache.lucene.search.spans.SpanTermQuery;

public class SpanTermValidator extends SpanValidator {

	private int source;

	/**
	 * 
	 * @param source
	 *            The hash of the source to compare to the incoming hits.
	 */
	public SpanTermValidator(SpanTermQuery query, int source) {
		super(query);
		this.source = source;
	}

	@Override
	public void next(PositionalHit hit) {
		if (hit.source() == source) {
			addValidHit(hit);
		}
	}

	@Override
	public boolean isSuccessful() {
		return getValidHits().size() > 0;
	}

	@Override
	public boolean isInProcess() {
		return false;
	}

	@Override
	public int getStartPosition() {
		int leastPosition = Integer.MIN_VALUE;

		for (PositionalHit validHit : getValidHits()) {
			if (leastPosition == Integer.MIN_VALUE
					|| validHit.getPosition() < leastPosition) {
				leastPosition = validHit.getPosition();
			}
		}

		return leastPosition;
	}

	@Override
	public int getEndPosition() {
		int greatestPosition = Integer.MIN_VALUE;

		for (PositionalHit validHit : getValidHits()) {
			if (validHit.getPosition() > greatestPosition) {
				greatestPosition = validHit.getPosition();
			}
		}

		return greatestPosition;
	}

	@Override
	public void toString(StringBuilder tostring) {
		getStringStart(tostring);

		// members
		appendMember(tostring, "source", ((Integer) source).toString(), true);

		tostring.append("}}");
	}
}
