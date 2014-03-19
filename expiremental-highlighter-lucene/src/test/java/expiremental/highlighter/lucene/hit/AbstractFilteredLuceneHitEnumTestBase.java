package expiremental.highlighter.lucene.hit;

import static expiremental.highlighter.Matchers.advances;
import static expiremental.highlighter.Matchers.hit;
import static expiremental.highlighter.Matchers.isEmpty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefHash;
import org.apache.lucene.util.Version;
import org.junit.Test;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.lucene.WrappedExceptionFromLucene;
import expiremental.highlighter.source.StringSourceExtracter;

public abstract class AbstractFilteredLuceneHitEnumTestBase extends AbstractLuceneHitEnumTestBase {
    protected abstract HitEnum buildEnum(String source, Analyzer analyzer, List<String> acceptableTerms);
    
    protected HitEnum buildEnum(String source, Analyzer analyzer, String... acceptableTerms) {
        return buildEnum(source, analyzer, Arrays.asList(acceptableTerms));
    }

    @Override
    protected HitEnum buildEnum(String source) {
        BytesRefHash hash = new BytesRefHash();
        TokenStream stream = buildTokenStream(source, mockAnalyzer());
        TermToBytesRefAttribute termRef = stream.addAttribute(TermToBytesRefAttribute.class);
        BytesRef term = termRef.getBytesRef();
        try {
            stream.reset();
            while (stream.incrementToken()) {
                int hashcode = termRef.fillBytesRef();
                hash.add(term, hashcode);
            }
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
        return new FilteredTokenStreamHitEnum(buildTokenStream(source, mockAnalyzer()), hash);
    }

    @Test
    public void aCoupleWordsFiltered() {
        String source = "hero of legend";
        SourceExtracter<String> extracter = new StringSourceExtracter(source);
        HitEnum e = buildEnum(source, mockAnalyzer(), "hero", "legend");
        assertThat(e, advances());
        assertThat(e, hit(0, extracter, equalTo("hero")));
        // "of" is skipped.  Yay.
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
        // Note that we asked for "hero" and we found "heros" because it stems to "hero" which is perfect.
        assertThat(e, hit(0, extracter, equalTo("heros")));
        assertThat(e, advances());
        assertThat(e, hit(2, extracter, equalTo("legend")));
        assertThat(e, isEmpty());
    }
    
    protected Analyzer englishStemmingAnalyzer() {
        return trackAnalyzer(new EnglishAnalyzer(Version.LUCENE_47, CharArraySet.EMPTY_SET));
    }
}
