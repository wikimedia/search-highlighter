package expiremental.highlighter.lucene.integration;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.Version;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import expiremental.highlighter.HitEnum;
import expiremental.highlighter.Segmenter;
import expiremental.highlighter.Snippet;
import expiremental.highlighter.SnippetChooser;
import expiremental.highlighter.SnippetFormatter;
import expiremental.highlighter.SourceExtracter;
import expiremental.highlighter.hit.WeightFilteredHitEnumWrapper;
import expiremental.highlighter.lucene.hit.DocsAndPositionsHitEnum;
import expiremental.highlighter.lucene.hit.TokenStreamHitEnum;
import expiremental.highlighter.lucene.hit.weight.BasicQueryWeigher;
import expiremental.highlighter.snippet.BasicSourceOrderSnippetChooser;
import expiremental.highlighter.snippet.CharScanningSegmenter;
import expiremental.highlighter.source.StringSourceExtracter;

public class BasicIntegration {
    public static void main(String[] args) throws IOException {
        File tmpDir = Files.createTempDir();
        Directory dir = new MMapDirectory(tmpDir);
        IndexWriterConfig writerConfig = new IndexWriterConfig(Version.LUCENE_46,
                new EnglishAnalyzer(Version.LUCENE_46, null));
        writerConfig.setOpenMode(OpenMode.CREATE);
        IndexWriter writer = new IndexWriter(dir, writerConfig);

        String data = Resources.toString(Resources.getResource("rashidun_caliphate.txt"),
                Charsets.UTF_8);
        load(writer, "rashidun_caliphate.txt", data);

        System.err.println("Done writing");
        writer.commit();
        writer.forceMerge(1);
        writer.close();

        IndexReader reader = DirectoryReader.open(dir);
        
        System.err.println(extractSnippet(reader, data, new TermQuery(new Term("text", "rashidun"))));
        System.err.println(extractSnippet(reader, data, new TermQuery(new Term("text", "life"))));
        System.err.println(extractSnippet(reader, data, new TermQuery(new Term("text", "and"))));
        PhraseQuery pq = new PhraseQuery();
        pq.add(new Term("text", "throughout"));
        pq.add(new Term("text", "life"));
        System.err.println(extractSnippet(reader, data, pq));
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            extractSnippet(reader, data, pq);
        }
        System.err.println((System.currentTimeMillis() - start) / 10000.0);

        reader.close();
        dir.close();
    }

    private static void load(IndexWriter writer, String name, String data) throws IOException {
        System.err.println("Loading " + name);
        Document doc = new Document();
        FieldType type = new FieldType(TextField.TYPE_NOT_STORED);
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        type.setStoreTermVectors(true);
        type.setStoreTermVectorOffsets(true);
        type.setStoreTermVectorPositions(true);
        type.freeze();
        Field field = new Field("text", data, type);
        doc.add(field);
        writer.addDocument(doc);
    }
    
    private static String extractSnippet(IndexReader reader, String data, Query q) throws IOException {
        BasicQueryWeigher queryWeigher = new BasicQueryWeigher(reader, q);
        HitEnum e = postingsHighlighterHitEnum(queryWeigher, reader);
//        HitEnum e = plainHighlighterHitEnum(queryWeigher, data);
//        HitEnum e = fastVectorHighlighterHitEnum(queryWeigher, reader);
        Segmenter segmenter = new CharScanningSegmenter(data, 150, 20);
        SnippetChooser chooser = new BasicSourceOrderSnippetChooser();
        SourceExtracter<String> extracter = new StringSourceExtracter(data);
        Snippet snippet = chooser.choose(segmenter, e, 1).get(0);
        SnippetFormatter formatter = new SnippetFormatter(extracter, "*", "*");
        return formatter.format(snippet);
    }
    
    private static HitEnum postingsHighlighterHitEnum(BasicQueryWeigher queryWeigher, IndexReader reader) throws IOException {
        return DocsAndPositionsHitEnum.fromPostings(reader, 0, "text",
                queryWeigher.acceptableTerms(), queryWeigher.termWeigher());
    }

    private static HitEnum fastVectorHighlighterHitEnum(BasicQueryWeigher queryWeigher, IndexReader reader) throws IOException {
        return DocsAndPositionsHitEnum.fromTermVectors(reader, 0, "text",
                queryWeigher.acceptableTerms(), queryWeigher.termWeigher());
    }
    
    private static HitEnum plainHighlighterHitEnum(BasicQueryWeigher queryWeigher, String data) throws IOException {
        // Yeah, doesn't close anything....
        Analyzer analyzer = new EnglishAnalyzer(Version.LUCENE_46, null);
        TokenStream t = analyzer.tokenStream("text", data);
        HitEnum e = new TokenStreamHitEnum(t, queryWeigher.termWeigher());
        return new WeightFilteredHitEnumWrapper(e, 0);
    }
}
