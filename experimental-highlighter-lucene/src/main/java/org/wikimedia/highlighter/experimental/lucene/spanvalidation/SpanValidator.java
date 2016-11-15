package org.wikimedia.highlighter.experimental.lucene.spanvalidation;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.lucene.search.spans.SpanQuery;
import org.wikimedia.search.highlighter.experimental.Snippet.Hit;

/**
 * Provides a contract for a hierarchy of span validation.
 * 
 * @author Michael D. Krauklis
 */
public abstract class SpanValidator {
	private Set<PositionalHit> validHits = new HashSet<PositionalHit>();
	protected SpanQuery query;

	public SpanValidator(SpanQuery query) {
		this.query = query;
	}

	public void reset() {
		validHits.clear();
	}

	public boolean isSourceAtPositionValid(int source, int position) {
		boolean isSourceAtPositionValid = false;

		if (isSuccessful()) {
			// iterate over the valid hits looking for a hit matching the given
			// source and position
			Iterator<PositionalHit> validHitsIterator = validHits.iterator();
			while (!isSourceAtPositionValid && validHitsIterator.hasNext()) {
				PositionalHit validHit = validHitsIterator.next();
				isSourceAtPositionValid = source == validHit.source()
						&& position == validHit.getPosition();
			}
		}

		return isSourceAtPositionValid;
	}

	public void addValidHit(PositionalHit hit) {
		validHits.add(hit);
	}

	public Set<PositionalHit> getValidHits() {
		return validHits;
	}

	public abstract void next(PositionalHit hit);

	public abstract boolean isSuccessful();

	public abstract boolean isInProcess();

	public abstract int getStartPosition();

	public abstract int getEndPosition();

	public static class PositionalHit extends Hit {

		private int position;

		public PositionalHit(int position, int startOffset, int endOffset,
				float weight, int source) {
			super(startOffset, endOffset, weight, source);
			this.position = position;
		}

		public int getPosition() {
			return position;
		}
	}

	protected void getStringStart(StringBuilder tostring) {
		tostring.append("{\"");
		tostring.append(getClass().getName());
		tostring.append("\":{");
		
		appendMember(tostring, "query", query.toString(), false);

		appendMember(tostring, "validHits", validHits.toString(), true);
	}

	protected void appendStringEnd(StringBuilder tostring) {
		tostring.append("}}");
	}

	protected void appendMember(StringBuilder tostring, String name,
			String value, boolean prependComma) {
		if (prependComma) {
			tostring.append(',');
		}
		tostring.append('"');
		tostring.append(name);
		tostring.append("\":\"");
		tostring.append(value);
		tostring.append('"');
	}

	protected void toString(StringBuilder tostring) {
		getStringStart(new StringBuilder());

		appendStringEnd(tostring);
	}

	@Override
	public String toString() {
		StringBuilder tostring = new StringBuilder();
		toString(tostring);
		return tostring.toString();
	}
}
