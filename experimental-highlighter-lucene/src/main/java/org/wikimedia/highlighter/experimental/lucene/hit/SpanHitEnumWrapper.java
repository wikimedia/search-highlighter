package org.wikimedia.highlighter.experimental.lucene.hit;

import org.wikimedia.highlighter.experimental.lucene.spanvalidation.SpanValidator;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.hit.AbstractHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.ReplayingHitEnum;

public class SpanHitEnumWrapper extends AbstractHitEnum {
	private HitEnum wrapped;
	private SpanValidator spanValidator;
	// EVERYTHING goes through toReplay
	private ReplayingHitEnum toReplay = new ReplayingHitEnum();

	public SpanHitEnumWrapper(HitEnum wrapped, SpanValidator spanValidator) {
		this.wrapped = wrapped;
		this.spanValidator = spanValidator;
	}

	@Override
	public boolean next() {
		boolean hasNext = true;
		// if toReplay.next simply return that
		if (!toReplay.next()) {
			hasNext = wrapped.next();
			if (hasNext) {
				// get a hit from wrapped and add it to replay
				toReplay.recordCurrent(wrapped);
				toReplay.next();

				// reset the validator
				spanValidator.reset();

				// send it to the validator
				SpanValidator.PositionalHit hit = new SpanValidator.PositionalHit(
						wrapped.position(), wrapped.startOffset(),
						wrapped.endOffset(), wrapped.queryWeight(),
						wrapped.source());
				spanValidator.next(hit);

				// if issuccessful continue
				// if not successful iterate until successful or not in
				// progress, adding to replay as we go
				if (!spanValidator.isSuccessful()) {
					while (!spanValidator.isSuccessful()
							&& spanValidator.isInProcess() && wrapped.next()) {
						toReplay.recordCurrent(wrapped);
						// send it to the validator
						hit = new SpanValidator.PositionalHit(
								wrapped.position(), wrapped.startOffset(),
								wrapped.endOffset(), wrapped.queryWeight(),
								wrapped.source());
						spanValidator.next(hit);
					}
				}
			}
		}
		return hasNext;
	}

	@Override
	public int position() {
		return toReplay.position();
	}

	@Override
	public float queryWeight() {
		float queryWeight = toReplay.queryWeight();

		if (queryWeight <= 0) {
			if (spanValidator.isSuccessful()) {
				if (spanValidator.isSourceAtPositionValid(source(), position())) {
					queryWeight = 1;
				}
			}
		}

		return queryWeight;
	}

	@Override
	public float corpusWeight() {
		return toReplay.corpusWeight();
	}

	@Override
	public int source() {
		return toReplay.source();
	}

	@Override
	public int startOffset() {
		return toReplay.startOffset();
	}

	@Override
	public int endOffset() {
		return toReplay.endOffset();
	}

}
