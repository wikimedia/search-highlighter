package org.wikimedia.highlighter.cirrus.lucene;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanNotQuery;
import org.apache.lucene.queries.spans.SpanOrQuery;
import org.apache.lucene.queries.spans.SpanPositionCheckQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.MultiTermQuery.TopTermsScoringBooleanQueryRewrite;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.SynonymQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.LevenshteinAutomata;
import org.apache.lucene.util.automaton.Operations;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Flattens {@link Query}s similarly to Lucene's FieldQuery.
 */
@SuppressWarnings("checkstyle:classfanoutcomplexity") // should be fixed at some point
@SuppressFBWarnings(value = "UCC_UNRELATED_COLLECTION_CONTENTS", justification = "sentAutomata is used to check different kinds objects")
public class QueryFlattener {
    /**
     * Some queries are inefficient to rebuild multiple times so we store some
     * information about them here and check if we've already seen them.
     */
    private final Set<Object> sentAutomata = new HashSet<>();
    private final int maxMultiTermQueryTerms;
    private final boolean phraseAsTerms;
    private final boolean removeHighFrequencyTermsFromCommonTerms;

    /**
     * Default configuration.
     */
    public QueryFlattener() {
        this(1000, false, true);
    }

    public QueryFlattener(int maxMultiTermQueryTerms, boolean phraseAsTerms, boolean removeHighFrequencyTermsFromCommonTerms) {
        this.maxMultiTermQueryTerms = maxMultiTermQueryTerms;
        this.phraseAsTerms = phraseAsTerms;
        this.removeHighFrequencyTermsFromCommonTerms = removeHighFrequencyTermsFromCommonTerms;
    }

    public interface Callback {
        /**
         * Called once per query containing the term.
         *
         * @param term the term
         * @param boost weight of the term
         * @param sourceOverride null if the source of the term is the query
         *            containing it, not null if the term query came from some
         *            rewritten query
         */
        void flattened(BytesRef term, float boost, Object sourceOverride);

        /**
         * Called with each new automaton. QueryFlattener makes an effort to
         * only let the first copy of any duplicate automata through.
         *
         * @param automaton automaton from the query
         * @param boost weight of terms matchign the automaton
         * @param source hashcode of the source. Automata don't have a hashcode
         *            so this will always provide the source.
         */
        void flattened(Automaton automaton, float boost, int source);

        /**
         * Called to mark the start of a phrase.
         */
        void startPhrase(int positionCount, float boost);

        void startPhrasePosition(int termCount);

        void endPhrasePosition();

        /**
         * Called to mark the end of a phrase.
         */
        void endPhrase(String field, int slop, float boost);
    }

    public void flatten(Query query, IndexSearcher searcher, Callback callback) {
        flatten(query, 1f, null, searcher, callback);
    }

    /**
     * Should phrase queries be returned as terms?
     *
     * @return true mean skip startPhrase and endPhrase and give the terms in a
     *         phrase the weight of the whole phrase
     */
    protected boolean phraseAsTerms() {
        return phraseAsTerms;
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity") // cyclomatic complexity is high, but the code is simple to read
    protected void flatten(Query query, float pathBoost, Object sourceOverride, IndexSearcher searcher,
            Callback callback) {
        if (query instanceof TermQuery) {
            flattenQuery((TermQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof BoostQuery) {
            flattenQuery((BoostQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof PhraseQuery) {
            flattenQuery((PhraseQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof BooleanQuery) {
            flattenQuery((BooleanQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof DisjunctionMaxQuery) {
            flattenQuery((DisjunctionMaxQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof ConstantScoreQuery) {
            flattenQuery((ConstantScoreQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof MultiPhraseQuery) {
            flattenQuery((MultiPhraseQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof SpanQuery
                && flattenSpan((SpanQuery) query, pathBoost, sourceOverride, searcher, callback)) {
            // Actually nothing to do here, but it keeps the code lining up to
            // have it.
        } else if (query instanceof FuzzyQuery) {
            flattenQuery((FuzzyQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof RegexpQuery) {
            flattenQuery((RegexpQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof WildcardQuery) {
            flattenQuery((WildcardQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof PrefixQuery) {
            flattenQuery((PrefixQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof CommonTermsQuery) {
            flattenQuery((CommonTermsQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (query instanceof SynonymQuery) {
            flattenQuery((SynonymQuery) query, pathBoost, sourceOverride, searcher, callback);
        } else if (!flattenUnknown(query, pathBoost, sourceOverride, searcher, callback)) {
            Query newRewritten = rewriteQuery(query, pathBoost, sourceOverride, searcher);
            if (newRewritten != query) {
                // only rewrite once and then flatten again - the rewritten
                // query could have a special treatment
                flatten(newRewritten, pathBoost, query, searcher, callback);
            }
        }
    }

    protected boolean flattenSpan(SpanQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        if (query instanceof SpanTermQuery) {
            flattenQuery((SpanTermQuery) query, pathBoost, sourceOverride, searcher, callback);
            return true;
        } else if (query instanceof SpanPositionCheckQuery) {
            flattenQuery((SpanPositionCheckQuery) query, pathBoost, sourceOverride, searcher,
                    callback);
            return true;
        } else if (query instanceof SpanNearQuery) {
            flattenQuery((SpanNearQuery) query, pathBoost, sourceOverride, searcher, callback);
            return true;
        } else if (query instanceof SpanNotQuery) {
            flattenQuery((SpanNotQuery) query, pathBoost, sourceOverride, searcher, callback);
            return true;
        } else if (query instanceof SpanOrQuery) {
            flattenQuery((SpanOrQuery) query, pathBoost, sourceOverride, searcher, callback);
            return true;
        }
        return false;
    }

    protected boolean flattenUnknown(Query query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        return false;
    }

    protected void flattenQuery(TermQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        callback.flattened(query.getTerm().bytes(), pathBoost, sourceOverride);
    }

    protected void flattenQuery(BoostQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        flatten(query.getQuery(), query.getBoost() * pathBoost, sourceOverride, searcher, callback);
    }

    protected void flattenQuery(PhraseQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        Term[] terms = query.getTerms();
        if (terms.length == 0) {
            return;
        }
        if (phraseAsTerms) {
            for (Term term : terms) {
                callback.flattened(term.bytes(), pathBoost, sourceOverride);
            }
        } else {
            callback.startPhrase(terms.length, pathBoost);
            for (Term term : terms) {
                callback.startPhrasePosition(1);
                callback.flattened(term.bytes(), 0, sourceOverride);
                callback.endPhrasePosition();
            }
            callback.endPhrase(terms[0].field(), query.getSlop(), pathBoost);
        }
    }

    protected void flattenQuery(BooleanQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        for (BooleanClause clause : query) {
            // Exclude FILTER clauses with isScoring(), before lucene 5 most of
            // these queries were wrapped inside a FitleredQuery
            // but now the prefered way is to add a boolean clause with
            // Occur.FILTER
            // e.g. the _type filter with opensearch now uses this type of
            // construct.
            if (!clause.isProhibited() && clause.isScoring()) {
                flatten(clause.query(), pathBoost, sourceOverride, searcher,
                        callback);
            }
        }
    }

    protected void flattenQuery(DisjunctionMaxQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        for (Query clause : query) {
            flatten(clause, pathBoost, sourceOverride, searcher, callback);
        }
    }

    protected void flattenQuery(ConstantScoreQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        if (query.getQuery() != null) {
            flatten(query.getQuery(), pathBoost, sourceOverride, searcher,
                    callback);
        }
        // TODO maybe flatten filter like Elasticsearch does
    }

    protected void flattenQuery(MultiPhraseQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        // Elasticsearch uses a more complicated method to preserve the phrase
        // queries.
        Term[][] termArrays = query.getTermArrays();

        if (phraseAsTerms) {
            for (Term[] terms : termArrays) {
                for (Term term : terms) {
                    callback.flattened(term.bytes(), pathBoost, sourceOverride);
                }
            }
        } else {
            callback.startPhrase(termArrays.length, pathBoost);
            String field = null;
            for (Term[] terms : termArrays) {
                callback.startPhrasePosition(terms.length);
                for (Term term : terms) {
                    callback.flattened(term.bytes(), 0, sourceOverride);
                    field = term.field();
                }
                callback.endPhrasePosition();
            }
            // field will be null if there are no terms in the phrase which
            // would be weird
            if (field != null) {
                callback.endPhrase(field, query.getSlop(), pathBoost);
            }
        }
    }

    protected void flattenQuery(SpanTermQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        callback.flattened(query.getTerm().bytes(), pathBoost, sourceOverride);
    }

    protected void flattenQuery(SpanPositionCheckQuery query, float pathBoost,
            Object sourceOverride, IndexSearcher searcher, Callback callback) {
        flattenSpan(query.getMatch(), pathBoost, sourceOverride, searcher,
                callback);
    }

    protected void flattenQuery(SpanNearQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        for (SpanQuery clause : query.getClauses()) {
            flattenSpan(clause, pathBoost, sourceOverride, searcher, callback);
        }
    }

    protected void flattenQuery(SpanNotQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        flattenSpan(query.getInclude(), pathBoost, sourceOverride, searcher,
                callback);
    }

    protected void flattenQuery(SpanOrQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        for (SpanQuery clause : query.getClauses()) {
            flattenSpan(clause, pathBoost, sourceOverride, searcher, callback);
        }
    }

    protected void flattenQuery(RegexpQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        // This isn't a great "source" because it contains the term's field but
        // its the best we can do here
        if (!sentAutomata.add(query)) {
            return;
        }
        int source = sourceOverride == null ? query.hashCode() : sourceOverride.hashCode();
        callback.flattened(query.getAutomaton(), pathBoost, source);
    }

    protected void flattenQuery(WildcardQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        // Should be safe not to copy this because it is fixed...
        if (!sentAutomata.add(query.getTerm().bytes())) {
            return;
        }
        Object source = sourceOverride == null ? query.getTerm().bytes() : sourceOverride;
        callback.flattened(query.getAutomaton(), pathBoost, source.hashCode());
    }

    protected void flattenQuery(SynonymQuery query, float pathBoost, Object sourceOverride,
                                IndexSearcher searcher, Callback callback) {
        for (Term t : query.getTerms()) {
            callback.flattened(t.bytes(), pathBoost, sourceOverride);
        }
    }

    protected void flattenQuery(PrefixQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        flattenPrefixQuery(query.getPrefix().bytes(), pathBoost, sourceOverride,
                callback);
    }

    protected void flattenPrefixQuery(BytesRef bytes, float boost, Object sourceOverride,
            Callback callback) {
        // Should be safe not to copy this because it is fixed...
        if (!sentAutomata.add(bytes)) {
            return;
        }
        Object source = sourceOverride == null ? bytes : sourceOverride;
        Automaton automaton = Automata.makeString(bytes.utf8ToString());
        automaton = Operations.concatenate(automaton, Automata.makeAnyString());
        callback.flattened(automaton, boost, source.hashCode());
    }

    protected void flattenQuery(FuzzyQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        float boost = pathBoost;
        if (query.getMaxEdits() == 0) {
            callback.flattened(query.getTerm().bytes(), boost, sourceOverride);
        }
        String term = query.getTerm().bytes().utf8ToString();
        if (query.getPrefixLength() >= term.length()) {
            callback.flattened(query.getTerm().bytes(), boost, sourceOverride);
            return;
        }

        FuzzyQueryInfo key = new FuzzyQueryInfo(term, query);
        if (!sentAutomata.add(key)) {
            return;
        }
        // Make an effort to resolve the fuzzy query to an automata
        Automaton automaton = getFuzzyAutomata(query, term);
        Object source = sourceOverride == null ? key : sourceOverride;
        callback.flattened(automaton, boost, source.hashCode());
    }

    private Automaton getFuzzyAutomata(FuzzyQuery query, String term) {
        int termLength = term.length();
        int[] termText = new int[term.codePointCount(0, termLength)];
        for (int cp, i = 0, j = 0; i < termLength; i += Character.charCount(cp)) {
            cp = term.codePointAt(i);
            termText[j++] = cp;
        }
        int prefixLen = query.getPrefixLength() > termText.length ? termText.length : query.getPrefixLength();
        int editDistance = query.getMaxEdits();
        if (editDistance > LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE) {
            editDistance = LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE;
        }
        LevenshteinAutomata automata = new LevenshteinAutomata(UnicodeUtil.newString(termText, prefixLen, termText.length - prefixLen),
                query.getTranspositions());
        Automaton automaton;
        if (prefixLen > 0) {
            automaton = automata.toAutomaton(editDistance, UnicodeUtil.newString(termText, 0, prefixLen));
        } else {
            automaton = automata.toAutomaton(editDistance);
        }
        return automaton;
    }

    @SuppressWarnings({"checkstyle:CyclomaticComplexity", "checkstyle:NPathComplexity"})
    protected void flattenQuery(CommonTermsQuery query, float pathBoost, Object sourceOverride,
            IndexSearcher searcher, Callback callback) {
        Query rewritten = rewriteQuery(query, pathBoost, sourceOverride, searcher);
        if (!removeHighFrequencyTermsFromCommonTerms) {
            flatten(rewritten, pathBoost, sourceOverride, searcher, callback);
            return;
        }
        /*
         * Try to figure out if the query was rewritten into a list of low and
         * high frequency terms. If it was, remove the high frequency terms.
         *
         * Note that this only works if high frequency terms are set to
         * Occur.SHOULD and low frequency terms are set to Occur.MUST. That is
         * usually the way it is done though.
         */
        if (!(rewritten instanceof BooleanQuery)) {
            // Nope - its a term query or something more exotic
            flatten(rewritten, pathBoost, sourceOverride, searcher, callback);
            return;
        }
        BooleanQuery bq = (BooleanQuery) rewritten;
        List<BooleanClause> clauses = bq.clauses();
        if (clauses.size() != 2) {
            // Nope - its just a list of terms.
            flattenQuery(bq, pathBoost, sourceOverride, searcher, callback);
            return;
        }
        BooleanClause first = clauses.get(0);
        BooleanClause second = clauses.get(1);
        if ((first.occur() != Occur.SHOULD || second.occur() != Occur.MUST)
                && (first.occur() != Occur.MUST || second.occur() != Occur.SHOULD)) {
            // Nope - just a two term query
            flattenQuery(bq, pathBoost, sourceOverride, searcher, callback);
            return;
        }

        Query firstQ = first.query();
        Query secondQ = second.query();

        // The query can be wrapped inside a BoostQuery
        if (firstQ instanceof BoostQuery && secondQ instanceof BoostQuery) {
            firstQ = ((BoostQuery)firstQ).getQuery();
            secondQ = ((BoostQuery)secondQ).getQuery();
        }

        if (!(firstQ instanceof BooleanQuery && secondQ instanceof BooleanQuery)) {
            // Nope - terms of the wrong type. not sure how that happened.
            flattenQuery(bq, pathBoost, sourceOverride, searcher, callback);
            return;
        }

        final Query lowFrequency;
        if (first.occur() == Occur.MUST) {
            lowFrequency = first.query();
        } else {
            lowFrequency = second.query();
        }
        flatten(lowFrequency, pathBoost, sourceOverride, searcher, callback);
    }

    protected Query rewriteQuery(MultiTermQuery query, float pathBoost, Object sourceOverride, IndexSearcher searcher) {
        TopTermsScoringBooleanQueryRewrite method = new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(
                maxMultiTermQueryTerms);
        try {
            return method.rewrite(searcher, query);
        } catch (IOException ioe) {
            throw new WrappedExceptionFromLucene(ioe);
        }
    }

    protected Query rewriteQuery(Query query, float pathBoost, Object sourceOverride, IndexSearcher searcher) {
        if (query instanceof MultiTermQuery) {
            return rewriteQuery((MultiTermQuery) query, pathBoost, sourceOverride, searcher);
        }
        return rewritePreparedQuery(query, pathBoost, sourceOverride, searcher);
    }

    /**
     * Rewrites a query that's already ready for rewriting.
     */
    protected Query rewritePreparedQuery(Query query, float pathBoost, Object sourceOverride, IndexSearcher searcher) {
        try {
            return query.rewrite(searcher);
        } catch (IOException e) {
            throw new WrappedExceptionFromLucene(e);
        }
    }

    private static class FuzzyQueryInfo {
        private final String term;
        private final int maxEdits;
        private final boolean transpositions;
        private final int prefixLength;

        FuzzyQueryInfo(String term, FuzzyQuery query) {
            this.term = term;
            this.maxEdits = query.getMaxEdits();
            this.transpositions = query.getTranspositions();
            this.prefixLength = query.getPrefixLength();
        }

        // Eclipse made these:
        @Override
        public int hashCode() {
            return Objects.hash(maxEdits, prefixLength, term, transpositions);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            FuzzyQueryInfo other = (FuzzyQueryInfo) obj;
            return Objects.equals(maxEdits, other.maxEdits)
                    && Objects.equals(prefixLength, other.prefixLength)
                    && Objects.equals(term, other.term)
                    && Objects.equals(transpositions, other.transpositions);
        }
    }
}
