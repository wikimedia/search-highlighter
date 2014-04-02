package org.wikimedia.highlighter.experimental.lucene.hit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.wikimedia.search.highlighter.experimental.Matchers.advances;
import static org.wikimedia.search.highlighter.experimental.Matchers.hit;
import static org.wikimedia.search.highlighter.experimental.Matchers.isEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.apache.lucene.util.automaton.BasicAutomata;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.junit.Test;
import org.wikimedia.search.highlighter.experimental.HitEnum;
import org.wikimedia.search.highlighter.experimental.SourceExtracter;
import org.wikimedia.search.highlighter.experimental.source.StringSourceExtracter;

public abstract class AbstractDocsAndPositionsHitEnumTestBase extends AbstractLuceneHitEnumTestBase {
    protected abstract HitEnum buildEnum(String source, Analyzer analyzer,
            CompiledAutomaton acceptable);

    protected HitEnum buildEnum(String source, Analyzer analyzer, String... acceptableTerms) {
        return buildEnum(source, analyzer, Arrays.asList(acceptableTerms));
    }

    protected HitEnum buildEnum(String source, Analyzer analyzer, List<String> acceptableTerms) {
        List<BytesRef> refs = new ArrayList<BytesRef>();
        for (String acceptable : acceptableTerms) {
            refs.add(new BytesRef(acceptable));
        }
        return buildEnum(source, analyzer,
                new CompiledAutomaton(BasicAutomata.makeStringUnion(refs)));
    }

    protected IndexReader buildIndexReader(String source, Analyzer analyzer) {
        MemoryIndex index = new MemoryIndex(true);
        index.addField("field", source, analyzer);

        return index.createSearcher().getIndexReader();
    }

    @Override
    protected HitEnum buildEnum(String source) {
        return buildEnum(source, mockAnalyzer(),
                new CompiledAutomaton(BasicAutomata.makeAnyString()));
    }

    @Test
    public void aCoupleWordsFiltered() {
        String source = "hero of legend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = buildEnum(source, mockAnalyzer(), "hero", "legend");
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        // "of" is skipped. Yay.
        assertThat(e, advances());
        assertThat(e, hit(2, extracter, equalTo("legend")));
        assertThat(e, isEmpty());
    }

    @Test
    public void stemming() {
        String source = "heros of legend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = buildEnum(source, englishStemmingAnalyzer(), "hero", "legend");
        assertThat(e, advances());
        // Note that we asked for "hero" and we found "heros" because it stems
        // to "hero" which is perfect.
        assertThat(e, hit(0, extracter, equalTo("heros")));
        assertThat(e, advances());
        assertThat(e, hit(2, extracter, equalTo("legend")));
        assertThat(e, isEmpty());
    }

    protected Analyzer englishStemmingAnalyzer() {
        return trackAnalyzer(new EnglishAnalyzer(Version.LUCENE_47, CharArraySet.EMPTY_SET));
    }
}
