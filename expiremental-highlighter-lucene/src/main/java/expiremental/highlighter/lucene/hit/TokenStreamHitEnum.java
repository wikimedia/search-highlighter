package expiremental.highlighter.lucene.hit;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.lucene.WrappedExceptionFromLucene;

public class TokenStreamHitEnum implements HitEnum {
    private final TokenStream tokenStream;
    private final PositionIncrementAttribute positionIncr;
    private final OffsetAttribute offsets;
    private int position = -1;

    public TokenStreamHitEnum(TokenStream tokenStream) {
        this.tokenStream = tokenStream;
        positionIncr = tokenStream.addAttribute(PositionIncrementAttribute.class);
        offsets = tokenStream.addAttribute(OffsetAttribute.class);
        try {
            tokenStream.reset();
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }

    @Override
    public boolean next() {
        try {
            if (!tokenStream.incrementToken()) {
                return false;
            }
            position += positionIncr.getPositionIncrement();
            return true;
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public int startOffset() {
        return offsets.startOffset();
    }

    @Override
    public int endOffset() {
        return offsets.endOffset();
    }
}
