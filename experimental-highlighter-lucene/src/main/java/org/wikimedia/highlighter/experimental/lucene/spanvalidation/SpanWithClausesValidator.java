package org.wikimedia.highlighter.experimental.lucene.spanvalidation;

import java.util.Iterator;
import java.util.List;

import org.apache.lucene.search.spans.SpanQuery;

public abstract class SpanWithClausesValidator extends SpanValidator {
	protected List<SpanValidator> clauseValidators;

	public SpanWithClausesValidator(SpanQuery query,
			List<SpanValidator> clauseValidators) {
		super(query);
		this.clauseValidators = clauseValidators;
	}

	/**
	 * Returns true if any of the clause validators consider this source at this
	 * position to be valid.
	 */
	@Override
	public boolean isSourceAtPositionValid(int source, int position) {
		boolean isSourceValid = super.isSourceAtPositionValid(source, position);

		if (isSuccessful()) {
			// iterate over all the clause validators asking if any of them
			// think the given source at the given position are valid
			Iterator<SpanValidator> clauseValidatorIterator = clauseValidators
					.iterator();
			while (!isSourceValid && clauseValidatorIterator.hasNext()) {
				isSourceValid = clauseValidatorIterator.next()
						.isSourceAtPositionValid(source, position);
			}
		}

		return isSourceValid;
	}

	/**
	 * Returns true if any of the clause validators are in process.
	 */
	@Override
	public boolean isInProcess() {
		boolean isInProcess = false;

		if (!isSuccessful()) {
			for (SpanValidator clauseValidator : clauseValidators) {
				if (clauseValidator.isSuccessful()
						|| clauseValidator.isInProcess()) {
					isInProcess = true;
					break;
				}
			}
		}

		return isInProcess;
	}

	/**
	 * Returns the smallest end position. Integer.MIN_VALUE if no valid
	 * positions are found.
	 */
	@Override
	public int getStartPosition() {
		int leastPosition = Integer.MIN_VALUE;

		for (SpanValidator clauseValidator : clauseValidators) {
			if (clauseValidator.isSuccessful()
					&& (leastPosition == Integer.MIN_VALUE || clauseValidator
							.getStartPosition() < leastPosition)) {
				leastPosition = clauseValidator.getStartPosition();
			}
		}

		return leastPosition;
	}

	/**
	 * Returns the greatest end position. Integer.MIN_VALUE if no valid
	 * positions are found.
	 */
	@Override
	public int getEndPosition() {
		int greatestPosition = Integer.MIN_VALUE;

		for (SpanValidator clauseValidator : clauseValidators) {
			if (clauseValidator.getEndPosition() > greatestPosition) {
				greatestPosition = clauseValidator.getStartPosition();
			}
		}

		return greatestPosition;
	}

	@Override
	public void reset() {
		super.reset();

		for (SpanValidator clauseValidator : clauseValidators) {
			clauseValidator.reset();
		}
	}

	@Override
	protected void toString(StringBuilder tostring) {
		getStringStart(tostring);

		toStringMembers(tostring);

		// clauses
		tostring.append(",\"clauses\":[");
		boolean isFirst = true;
		for (SpanValidator clauseValidator : clauseValidators) {
			if (isFirst) {
				isFirst = false;
			} else {
				tostring.append(',');
			}
			clauseValidator.toString(tostring);
		}
		tostring.append("]");

		tostring.append("}}");
	}

	protected abstract void toStringMembers(StringBuilder tostring);
}
