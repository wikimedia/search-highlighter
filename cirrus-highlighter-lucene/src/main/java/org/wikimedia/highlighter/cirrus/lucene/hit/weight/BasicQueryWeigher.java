package org.wikimedia.highlighter.cirrus.lucene.hit.weight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CollectionUtil;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.wikimedia.highlighter.cirrus.lucene.QueryFlattener;
import org.wikimedia.search.highlighter.cirrus.HitEnum;
import org.wikimedia.search.highlighter.cirrus.hit.PhraseHitEnumWrapper;
import org.wikimedia.search.highlighter.cirrus.hit.TermSourceFinder;
import org.wikimedia.search.highlighter.cirrus.hit.TermWeigher;

/**
 * "Simple" way to extract weights and sources from queries. Matches any terms
 * in queries, and, if any don't match, tries automata from queries. Term matches
 * take the max of the weights of all queries that match.  Automata just take the
 * first matching automata for efficiency's sake.
 */
public class BasicQueryWeigher implements TermWeigher<BytesRef>, TermSourceFinder<BytesRef> {
    private final List<AutomatonSourceInfo> automata = new ArrayList<>();
    private final List<BytesRef> terms = new ArrayList<>();
    private final TermInfos termInfos;
    private final float maxTermWeight;
    private Map<String, List<PhraseInfo>> phrases;
    private Map<PhraseKey, PhraseInfo> allPhrases;
    private CompiledAutomaton acceptable;

    public BasicQueryWeigher(IndexReader reader, Query query) {
        this(new QueryFlattener(1000, false, true), new HashMapTermInfos(), reader, query);
    }

    public BasicQueryWeigher(QueryFlattener flattener, final TermInfos termInfos, IndexReader reader, Query query) {
        this.termInfos = termInfos;
        FlattenerCallback callback = new FlattenerCallback();
        flattener.flatten(query, reader, callback);
        maxTermWeight = callback.maxTermWeight;
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

    /**
     * Wrap the hit enum if required to support things like phrases.
     */
    public HitEnum wrap(String field, HitEnum e) {
        if (phrases == null) {
            return e;
        }
        List<PhraseInfo> phraseList = phrases.get(field);
        if (phraseList == null) {
            return e;
        }
        for (PhraseInfo phrase: phraseList) {
            e = new PhraseHitEnumWrapper(e, phrase.phrase, phrase.weight, phrase.slop);
        }
        return e;
    }

    /**
     * The maximum weight of a single term without phrase queries.
     */
    public float maxTermWeight() {
        return maxTermWeight;
    }

    /**
     * Are there phrases on the provided field?
     */
    public boolean areTherePhrasesOnField(String field) {
        if (phrases == null) {
            return false;
        }
        List<PhraseInfo> phraseList = phrases.get(field);
        return phraseList != null && !phraseList.isEmpty();
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
                return Automata.makeEmpty();
            }
            return buildTermsAutomata();
        }
        if (automata.size() == 1 && terms.isEmpty()) {
            return automata.get(0).automaton;
        }
        List<Automaton> all = new ArrayList<>(automata.size() + 1);
        for (AutomatonSourceInfo info : automata) {
            all.add(info.automaton);
        }
        if (!terms.isEmpty()) {
            all.add(buildTermsAutomata());
        }
        return Operations.union(all);
    }

    private Automaton buildTermsAutomata() {
        // Sort the terms in UTF-8 order.
        CollectionUtil.timSort(terms);
        return Automata.makeStringUnion(terms);
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

        AutomatonSourceInfo(Automaton automaton) {
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
        private final Map<BytesRef, SourceInfo> infos = new HashMap<>();

        @Override
        public SourceInfo get(BytesRef term) {
            return infos.get(term);
        }

        @Override
        public void put(BytesRef term, SourceInfo info) {
            infos.put(BytesRef.deepCopyOf(term), info);
        }
    }

    private static class PhraseInfo {
        private final int[][] phrase;
        private final int slop;
        private float weight;

        PhraseInfo(int[][] phrase, int slop, float weight) {
            this.phrase = phrase;
            this.slop = slop;
            this.weight = weight;

            for (int[] ph : phrase) {
                Arrays.sort(ph);
            }
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            for (int p = 0; p < phrase.length; p++) {
                if (p != 0) {
                    b.append(':');
                }
                b.append(Arrays.toString(phrase[p]));
            }
            return b.toString();
        }
    }

    private final class FlattenerCallback implements QueryFlattener.Callback {
        private float maxTermWeight;
        private int[][] phrase;
        private int phrasePosition;
        private int phraseTerm;
        private boolean inSinglePositionPhraseQuery;
        private float singlePositionPhraseQueryBoost;

        @Override
        public void flattened(BytesRef term, float boost, Object rewritten) {
            boost = inSinglePositionPhraseQuery ? singlePositionPhraseQueryBoost : boost;
            maxTermWeight = Math.max(maxTermWeight, boost);
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
            if (phrase != null) {
                phrase[phrasePosition][phraseTerm++] = info.source;
            }
        }

        @Override
        public void flattened(Automaton automaton, float boost, int source) {
            boost = inSinglePositionPhraseQuery ? singlePositionPhraseQueryBoost : boost;
            maxTermWeight = Math.max(maxTermWeight, boost);
            AutomatonSourceInfo info = new AutomatonSourceInfo(automaton);
            // Automata don't have a hashcode so we always use the source
            info.source = source;
            info.weight = boost;
            automata.add(info);
            if (phrase != null) {
                phrase[phrasePosition][phraseTerm++] = info.source;
            }
        }

        @Override
        public void startPhrase(int positionCount, float boost) {
            if (positionCount < 2) {
                // Single position phrases are just term queries
                inSinglePositionPhraseQuery = true;
                singlePositionPhraseQueryBoost = boost;
                return;
            }
            phrase = new int[positionCount][];
            phrasePosition = -1;
        }

        @Override
        public void startPhrasePosition(int termCount) {
            if (inSinglePositionPhraseQuery) {
                return;
            }
            phrasePosition++;
            phrase[phrasePosition] = new int[termCount];
            phraseTerm = 0;
        }

        @Override
        public void endPhrasePosition() {
        }

        @Override
        public void endPhrase(String field, int slop, float weight) {
            if (inSinglePositionPhraseQuery) {
                /*
                 * We don't record single phrase queries as phrase queries....
                 */
                inSinglePositionPhraseQuery = false;
                return;
            }
            // Because terms get the max weight across all fields phrases must as well.
            PhraseKey key = new PhraseKey(phrase);
            PhraseInfo info;
            if (allPhrases == null) {
                allPhrases = new HashMap<>();
                info = new PhraseInfo(phrase, slop, weight);
                allPhrases.put(key, info);
            } else {
                info = allPhrases.get(key);
                if (info == null) {
                    info = new PhraseInfo(phrase, slop, weight);
                    allPhrases.put(key, info);
                } else {
                    info.weight = Math.max(info.weight, weight);
                }
            }

            List<PhraseInfo> phraseList;
            if (phrases == null) {
                phrases = new HashMap<>();
                phraseList = new ArrayList<>();
                phrases.put(field, phraseList);
            } else {
                phraseList = phrases.computeIfAbsent(field, k -> new ArrayList<>());
            }
            phraseList.add(info);
            phrase = null;
        }
    }

    private static class PhraseKey {
        private final int[][] phrase;

        PhraseKey(int[][] phrase) {
            this.phrase = phrase;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            for (int[] position: phrase)  {
                result = prime * result + Arrays.hashCode(position);
            }
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            PhraseKey other = (PhraseKey) obj;
            if (!Arrays.deepEquals(phrase, other.phrase))
                return false;
            return true;
        }
    }
}
