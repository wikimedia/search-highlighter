package expiremental.highlighter.hit;

import expiremental.highlighter.HitEnum;

public abstract class AbstractHitEnumWrapper implements HitEnum {
    private final HitEnum wrapped;

    public AbstractHitEnumWrapper(HitEnum wrapped) {
        this.wrapped = wrapped;
    }

    protected HitEnum wrapped() {
        return wrapped;
    }

    @Override
    public boolean next() {
        return wrapped.next();
    }

    @Override
    public int position() {
        return wrapped.position();
    }

    @Override
    public int startOffset() {
        return wrapped.startOffset();
    }

    @Override
    public int endOffset() {
        return wrapped.endOffset();
    }
}