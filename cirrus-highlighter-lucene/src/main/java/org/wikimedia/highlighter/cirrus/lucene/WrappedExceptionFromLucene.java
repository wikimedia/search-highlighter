package org.wikimedia.highlighter.cirrus.lucene;

/**
 * Wraps mostly IOExceptions throws from Lucene that we shouldn't see because
 * we're entirely in memory.
 */
public class WrappedExceptionFromLucene extends RuntimeException {
    public WrappedExceptionFromLucene(Throwable cause) {
        super(cause);
    }
}
