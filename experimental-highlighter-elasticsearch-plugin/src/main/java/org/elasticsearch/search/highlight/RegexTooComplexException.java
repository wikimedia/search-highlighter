package org.elasticsearch.search.highlight;

import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;

/**
 * Wraps Lucene's XTooComplexToDeterminizeException to be serializable to be
 * thrown over the wire.
 */
public class RegexTooComplexException extends RuntimeException {
    private static final long serialVersionUID = -41975279199116247L;

    public RegexTooComplexException(TooComplexToDeterminizeException e) {
        super(e.getMessage());
    }
}
