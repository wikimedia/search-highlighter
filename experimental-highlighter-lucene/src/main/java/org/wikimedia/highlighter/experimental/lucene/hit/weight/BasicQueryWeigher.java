package org.wikimedia.highlighter.experimental.lucene.hit.weight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CollectionUtil;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.BasicAutomata;
import org.apache.lucene.util.automaton.BasicOperations;
import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener.Callback;
import org.wikimedia.search.highlighter.experimental.hit.TermSourceFinder;
import org.wikimedia.search.highlighter.experimental.hit.TermWeigher;

/**
 * "Simple" way to extract weights and sources from queries. Matches any terms
 * in queries, and, if any don't match, tries automata from queries. Term matches
 * take the max of the weights of all queries that match.  Automata just take the
 * first matching automata for efficiency's sake.
 */
public class BasicQueryWeigher implements TermWeigher<BytesRef>, TermSourceFinder<BytesRef> {
    private final List<AutomatonSourceInfo> automata = new ArrayList<AutomatonSourceInfo>();
    private final List<BytesRef> terms = new ArrayList<BytesRef>();
    private final TermInfos termInfos;
    private CompiledAutomaton acceptable;

    public BasicQueryWeigher(IndexReader reader, Query query) {
        this(new QueryFlattener(1000), new HashMapTermInfos(), reader, query);
    }

    public BasicQueryWeigher(QueryFlattener flattener, final TermInfos termInfos, IndexReader reader, Query query) {
        this.termInfos = termInfos;
        flattener.flatten(query, reader, new Callback() {
            @Override
            public void flattened(BytesRef term, float boost, Object rewritten) {
                int source = rewritten == null ? term.hashCode() : rewritten.hashCode();
                SourceInfo info = termInfos.get(term);
                if (info == null) {
                    info = new SourceInfo();
                    info.source = source;
                    info.weight = boost;
                    termInfos.put(term, info);
                    terms.add(BytesRef.deepCopyOf(term));
                } else {
                    /*
                     * If both terms can't be traced back to the same source we
                     * declare that they are from a new source by merging the
                     * hashes. This might not be ideal, but it has the advantage
                     * of being consistent.
                     */
                    if (info.source != source) {
                        info.source = source * 31 + source;
                    }
                    info.weight = Math.max(info.weight, boost);
                }
            }

            @Override
            public void flattened(Automaton automaton, float boost, int source) {
                AutomatonSourceInfo info = new AutomatonSourceInfo(automaton);
                // Automata don't have a hashcode so we always use the source
                info.source = source;
                info.weight = boost;
                automata.add(info);
            }
        });

    }

    public boolean singleTerm() {
        return automata.isEmpty() && terms.size() == 1;
    }

    @Override
    public float weigh(BytesRef term) {
        SourceInfo info = findInfo(term);
        return info == null ? 0 : info.weight;
    }

    @Override
    public int source(BytesRef term) {
        SourceInfo info = findInfo(term);
        return info == null ? 0 : info.source;
    }

    public CompiledAutomaton acceptableTerms() {
        if (acceptable == null) {
            acceptable = new CompiledAutomaton(buildAcceptableTerms());
        }
        return acceptable;
    }

    private Automaton buildAcceptableTerms() {
        if (automata.isEmpty()) {
            if (terms.isEmpty()) {
                return BasicAutomata.makeEmpty();
            }
            return buildTermsAutomata();
        }
        if (automata.size() == 1 && terms.isEmpty()) {
            return automata.get(0).automaton;
        }
        List<Automaton> all = new ArrayList<Automaton>(automata.size() + 1);
        for (AutomatonSourceInfo info : automata) {
            all.add(info.automaton);
        }
        if (!terms.isEmpty()) {
            all.add(buildTermsAutomata());
        }
        return BasicOperations.union(all);
    }

    private Automaton buildTermsAutomata() {
        // Sort the terms in UTF-8 order.
        CollectionUtil.timSort(terms);
        return BasicAutomata.makeStringUnion(terms);
    }

    private SourceInfo findInfo(BytesRef term) {
        SourceInfo info = termInfos.get(term);
        if (info != null) {
            return info;
        }
        for (AutomatonSourceInfo automatonInfo : automata) {
            if (automatonInfo.matches(term)) {
                termInfos.put(term, automatonInfo);
                return automatonInfo;
            }
        }
        return null;
    }

    public static class SourceInfo {
        public int source;
        public float weight;
    }

    private class AutomatonSourceInfo extends SourceInfo {
        public final Automaton automaton;
        public ByteRunAutomaton compiled;

        public AutomatonSourceInfo(Automaton automaton) {
            this.automaton = automaton;
        }

        public boolean matches(BytesRef term) {
            if (compiled == null) {
                compiled = new ByteRunAutomaton(automaton);
            }
            return compiled.run(term.bytes, term.offset, term.length);
        }
    }

    public interface TermInfos {
        SourceInfo get(BytesRef term);
        void put(BytesRef term, SourceInfo info);
    }

    public static class HashMapTermInfos implements TermInfos {
        private final Map<BytesRef, SourceInfo> infos = new HashMap<BytesRef, SourceInfo>();

        @Override
        public SourceInfo get(BytesRef term) {
            return infos.get(term);
        }

        @Override
        public void put(BytesRef term, SourceInfo info) {
            infos.put(BytesRef.deepCopyOf(term), info);
        }
    }
}
