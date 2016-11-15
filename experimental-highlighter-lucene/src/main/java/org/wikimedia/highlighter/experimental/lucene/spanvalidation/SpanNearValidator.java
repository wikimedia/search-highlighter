package org.wikimedia.highlighter.experimental.lucene.spanvalidation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.search.spans.SpanNearQuery;

public class SpanNearValidator extends SpanWithClausesValidator {
	private SpanNearQuery queryToValidate;
	private LinkedList<PositionalHit> processedHits = new LinkedList<PositionalHit>();

	public SpanNearValidator(SpanNearQuery queryToValidate,
			List<SpanValidator> clauseValidators) {
		super(queryToValidate, clauseValidators);
		this.queryToValidate = queryToValidate;
	}

	@Override
	public void next(PositionalHit hit) {
		processedHits.add(hit);
		if (!isSuccessful()) {
			if (isHitWithinSlop(hit)) {
				// if in order broadcast to the first non-successful clause
				// validator
				if (queryToValidate.isInOrder()) {
					SpanValidator firstNonSuccessfulClauseValidator = null;
					for (SpanValidator clauseValidator : clauseValidators) {
						if (!clauseValidator.isSuccessful()) {
							firstNonSuccessfulClauseValidator = clauseValidator;
							break;
						}
					}

					if (firstNonSuccessfulClauseValidator != null) {
						firstNonSuccessfulClauseValidator.next(hit);
					} else {
						// TODO: figure out what to do here. this should never
						// happen unless
						// there are no clauses (either all are successful and
						// the outer-most if
						// should fail or we should find an unsuccessful one).
					}
				} else {
					// if not in order broadcast to all non-successful clause
					// validators
					for (SpanValidator clauseValidator : clauseValidators) {
						if (!clauseValidator.isSuccessful()) {
							clauseValidator.next(hit);
						}
					}
				}
			} else {
				// we are not within slop. reset all clause span validators,
				// chop off the head of the processed hits, and replay
				for (SpanValidator clauseValidator : clauseValidators) {
					clauseValidator.reset();
				}

				processedHits.removeFirst();

				reprocessHits();
			}
		}
	}

	/***
	 * Check to see if this hit is within the slop of any successful clause.
	 * 
	 * @param hit
	 *            The hit to check for continuity.
	 * @return True if this hit is within the slop of any successful clause.
	 */
	private boolean isHitWithinSlop(PositionalHit hit) {
		int maximumEndingPosition = Integer.MIN_VALUE;

		// find the greatest position of all the clauses
		for (SpanValidator clauseValidator : clauseValidators) {
			if (clauseValidator.isSuccessful()) {
				maximumEndingPosition = Math
						.max(clauseValidator.getEndPosition(),
								maximumEndingPosition);
			}
		}

		return maximumEndingPosition == Integer.MIN_VALUE
				|| hit.getPosition() - maximumEndingPosition - 1 <= queryToValidate
						.getSlop();
	}

	private void reprocessHits() {
		List<PositionalHit> processedHitsCopy = new ArrayList<PositionalHit>(
				processedHits);
		processedHits = new LinkedList<PositionalHit>();
		for (PositionalHit hit : processedHitsCopy) {
			next(hit);
		}
	}

	@Override
	public boolean isSuccessful() {
		// successful if all children are successful
		boolean retval = true;

		Iterator<SpanValidator> clauseIterator = clauseValidators.iterator();
		while (clauseIterator.hasNext() && retval) {
			retval = clauseIterator.next().isSuccessful();
		}

		return retval;
	}

	@Override
	public void reset() {
		super.reset();

		processedHits.clear();
	}

	@Override
	protected void toStringMembers(StringBuilder tostring) {
		appendMember(tostring, "slop",
				((Integer) queryToValidate.getSlop()).toString(), true);
	}
}
