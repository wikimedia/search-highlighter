package org.wikimedia.search.highlighter.experimental.hit;

import java.text.BreakIterator;
import java.util.Locale;

import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.hit.BreakIteratorHitEnum;
import org.wikimedia.search.highlighter.experimental.hit.weight.ConstantHitWeigher;

public class BreakIteratorHitEnumTest extends AbstractHitEnumTestBase {
	@Override
	protected HitEnum buildEnum(String str) {
		BreakIterator itr = BreakIterator.getWordInstance(Locale.ENGLISH);
		itr.setText(str);
		return new BreakIteratorHitEnum(itr, new ConstantHitWeigher());
	}
}
