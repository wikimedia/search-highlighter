package org.wikimedia.highlighter.experimental.lucene.hit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.RegExp;
import org.junit.After;
import org.wikimedia.highlighter.experimental.lucene.WrappedExceptionFromLucene;
import org.wikimedia.search.highlighter.experimental.hit.AbstractHitEnumTestBase;

/**
 * Base class for tests for Lucene based HitEnums.
 */
public abstract class AbstractLuceneHitEnumTestBase extends AbstractHitEnumTestBase {
    private final List<TokenStream> builtStreams = new ArrayList<TokenStream>();
    private final List<Analyzer> builtAnalyzers = new ArrayList<Analyzer>();
    
    protected Analyzer mockAnalyzer() {
        // Pretty much MockTokenizer.WHITESPACE with a "." added in to make sentences a bit more sane.
        CharacterRunAutomaton tokenizer = new CharacterRunAutomaton(
                new RegExp("[^ \t\r\n\\.]+").toAutomaton());
        return trackAnalyzer(new MockAnalyzer(new Random(), tokenizer, true));
    }
    
    protected Analyzer trackAnalyzer(Analyzer analyzer) {
        builtAnalyzers.add(analyzer);
        return analyzer;
    }

    protected TokenStream buildTokenStream(String source, Analyzer analyzer) {
        try {
            TokenStream stream = analyzer.tokenStream("doesn'tmatter", source);
            builtStreams.add(stream);
            return stream;
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }

    @After
    public void cleanupTokenStreams() throws IOException {
        for (TokenStream stream : builtStreams) {
            try {
                stream.end();
            } finally {
                stream.close();
            }
        }
        builtStreams.clear();
        for (Analyzer analayzer: builtAnalyzers) {
            analayzer.close();
        }
    }
}
