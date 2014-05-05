package org.wikimedia.highlighter.experimental.lucene;

/**
 * Wraps mostly IOExceptions throws from Lucene that we shouldn't see because
 * we're entirely in memory.
 */
public class WrappedExceptionFromLucene extends RuntimeException {
    private static final long serialVersionUID = -3838263646006326148L;

    public WrappedExceptionFromLucene(Throwable cause) {
        super(cause);
    }
}
