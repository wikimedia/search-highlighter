package org.elasticsearch.search.highlight;

import org.apache.lucene.util.automaton.XTooComplexToDeterminizeException;

/**
 * Wraps Lucene's XTooComplexToDeterminizeException to be serializable to be
 * thrown over the wire.
 */
public class RegexTooComplexException extends RuntimeException {
    private static final long serialVersionUID = -41975279199116247L;

    public RegexTooComplexException(XTooComplexToDeterminizeException e) {
        super(e.getMessage());
    }
}
