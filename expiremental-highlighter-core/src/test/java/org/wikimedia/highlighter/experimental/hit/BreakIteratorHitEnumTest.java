package org.wikimedia.highlighter.expiremental.hit;

import java.text.BreakIterator;
import java.util.Locale;

import org.wikimedia.highlighter.expiremental.HitEnum;
import org.wikimedia.highlighter.expiremental.hit.BreakIteratorHitEnum;
import org.wikimedia.highlighter.expiremental.hit.weight.ConstantHitWeigher;

public class BreakIteratorHitEnumTest extends AbstractHitEnumTestBase {
	@Override
	protected HitEnum buildEnum(String str) {
		BreakIterator itr = BreakIterator.getWordInstance(Locale.ENGLISH);
		itr.setText(str);
		return new BreakIteratorHitEnum(itr, new ConstantHitWeigher());
	}
}
