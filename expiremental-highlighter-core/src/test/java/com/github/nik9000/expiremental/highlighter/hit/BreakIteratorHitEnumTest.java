package expiremental.highlighter.hit;

import java.text.BreakIterator;
import java.util.Locale;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.hit.weight.ConstantHitWeigher;

public class BreakIteratorHitEnumTest extends AbstractHitEnumTestBase {
	@Override
	protected HitEnum buildEnum(String str) {
		BreakIterator itr = BreakIterator.getWordInstance(Locale.ENGLISH);
		itr.setText(str);
		return new BreakIteratorHitEnum(itr, new ConstantHitWeigher());
	}
}
