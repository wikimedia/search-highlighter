package com.github.nik9000.expiremental.highlighter.hit;

import java.text.BreakIterator;
import java.util.Locale;

import com.github.nik9000.expiremental.highlighter.HitEnum;
import com.github.nik9000.expiremental.highlighter.hit.BreakIteratorHitEnum;
import com.github.nik9000.expiremental.highlighter.hit.weight.ConstantHitWeigher;

public class BreakIteratorHitEnumTest extends AbstractHitEnumTestBase {
	@Override
	protected HitEnum buildEnum(String str) {
		BreakIterator itr = BreakIterator.getWordInstance(Locale.ENGLISH);
		itr.setText(str);
		return new BreakIteratorHitEnum(itr, new ConstantHitWeigher());
	}
}
