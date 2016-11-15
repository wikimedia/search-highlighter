package org.wikimedia.highlighter.experimental.lucene.spanvalidation;

import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;

public class SpanMultiTermQueryValidator extends SpanTermValidator {
	/**
	 * 
	 * @param source
	 *            The hash of the source to compare to the incoming hits.
	 */
	public SpanMultiTermQueryValidator(SpanMultiTermQueryWrapper<?> query,
			int source) {
		super(null, source);
		super.query = query;
	}
}
