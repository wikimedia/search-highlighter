package org.wikimedia.highlighter.experimental.lucene.hit.weight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CollectionUtil;
import org.apache.lucene.util.automaton.BasicAutomata;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener.Callback;
import org.wikimedia.search.highlighter.experimental.hit.TermSourceFinder;
import org.wikimedia.search.highlighter.experimental.hit.TermWeigher;

/**
 * "Simple" way to extract weights from queries. Doesn't try doing anything
 * fancy - just flattens query into terms with weights.
 */
public class BasicQueryWeigher implements TermWeigher<BytesRef>, TermSourceFinder<BytesRef> {
    private final Map<BytesRef, SourceInfo> sourceInfos = new HashMap<BytesRef, SourceInfo>();
    private CompiledAutomaton acceptable;

    public BasicQueryWeigher(IndexReader reader, Query query) {
        this(new QueryFlattener(1000), reader, query);
    }

    public BasicQueryWeigher(QueryFlattener flattener, IndexReader reader, Query query) {
        flattener.flatten(query, reader, new Callback() {
            @Override
            public void flattened(Term term, float boost, Query rewritten) {
                int source = rewritten == null ? term.hashCode() : rewritten.hashCode();
                SourceInfo info = sourceInfos.get(term);
                if (info == null) {
                    info = new SourceInfo();
                    info.source = source;
                    info.weight = boost;
                    sourceInfos.put(BytesRef.deepCopyOf(term.bytes()), info);
                } else {
                    /*
                     * If both terms can't be traced back to the same source we
                     * declare that they are from a new source by merging the
                     * hashes. This might not be ideal, but it has the advantage
                     * of being consistent.
                     */
                    info.source = source * 31 + source;
                    info.weight = Math.max(info.weight, boost);
                }
            }
        });
    }

    public boolean singleTerm() {
        return sourceInfos.size() == 1;
    }

    @Override
    public float weigh(BytesRef term) {
        SourceInfo info = sourceInfos.get(term);
        if (info == null) {
            return 0;
        }
        return info.weight;
    }
    
    @Override
    public int source(BytesRef term) {
        SourceInfo info = sourceInfos.get(term);
        if (info == null) {
            return 0;
        }
        return info.source;
    }

    public CompiledAutomaton acceptableTerms() {
        if (acceptable == null) {
            // Sort the terms in UTF-8 order.
            List<BytesRef> terms = new ArrayList<BytesRef>(sourceInfos.size());
            terms.addAll(sourceInfos.keySet());
            CollectionUtil.timSort(terms);
            // TODO acceptable should grab the queries that can become
            // automatons and merge them rather then blow them out.
            acceptable = new CompiledAutomaton(BasicAutomata.makeStringUnion(terms));
        }
        return acceptable;
    }

    private static class SourceInfo {
        private int source;
        private float weight;
    }
}
