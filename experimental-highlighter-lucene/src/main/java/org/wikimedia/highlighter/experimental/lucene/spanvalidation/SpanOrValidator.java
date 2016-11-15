package org.wikimedia.highlighter.experimental.lucene.spanvalidation;

import java.util.Iterator;
import java.util.List;

import org.apache.lucene.search.spans.SpanOrQuery;

public class SpanOrValidator extends SpanWithClausesValidator {

	public SpanOrValidator(SpanOrQuery query,
			List<SpanValidator> clauseValidators) {
		super(query, clauseValidators);
	}

	@Override
	public void next(PositionalHit hit) {
		if (!isSuccessful()) {
			// broadcast to all non-successful clause validators
			for (SpanValidator clauseValidator : clauseValidators) {
				if (!clauseValidator.isSuccessful()) {
					clauseValidator.next(hit);
				}
			}
		}
	}

	@Override
	public boolean isSuccessful() {
		// successful if any children are successful
		boolean retval = false;

		Iterator<SpanValidator> clauseIterator = clauseValidators.iterator();
		while (clauseIterator.hasNext() && !retval) {
			retval = clauseIterator.next().isSuccessful();
		}

		return retval;
	}

	@Override
	protected void toStringMembers(StringBuilder tostring) {
		// no members other than clauses which are handled by super class
	}
}
