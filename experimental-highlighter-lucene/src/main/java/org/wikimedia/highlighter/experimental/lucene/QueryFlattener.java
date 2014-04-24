package org.wikimedia.highlighter.experimental.lucene;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanNotQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanPositionCheckQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;

/**
 * Flattens {@link Query}s similarly to Lucene's FieldQuery.
 */
public class QueryFlattener {
    private final int maxMultiTermQueryTerms;

    public QueryFlattener(int maxMultiTermQueryTerms) {
        this.maxMultiTermQueryTerms = maxMultiTermQueryTerms;
    }

    public interface Callback {
        void flattened(Term term, float boost, Query rewritten);
    }

    public void flatten(Query query, IndexReader reader, Callback callback) {
        flatten(query, 1f, null, reader, callback);
    }

    protected void flatten(Query query, float pathBoost, Query rewritten, IndexReader reader,
            Callback callback) {
        if (query instanceof TermQuery) {
            flattenQuery((TermQuery) query, pathBoost, rewritten, reader, callback);
        } else if (query instanceof PhraseQuery) {
            flattenQuery((PhraseQuery) query, pathBoost, rewritten, reader, callback);
        } else if (query instanceof BooleanQuery) {
            flattenQuery((BooleanQuery) query, pathBoost, rewritten, reader, callback);
        } else if (query instanceof DisjunctionMaxQuery) {
            flattenQuery((DisjunctionMaxQuery) query, pathBoost, rewritten, reader, callback);
        } else if (query instanceof ConstantScoreQuery) {
            flattenQuery((ConstantScoreQuery) query, pathBoost, rewritten, reader, callback);
        } else if (query instanceof FilteredQuery) {
            flattenQuery((FilteredQuery) query, pathBoost, rewritten, reader, callback);
        } else if (query instanceof MultiPhraseQuery) {
            flattenQuery((MultiPhraseQuery) query, pathBoost, rewritten, reader, callback);
        } else if (query instanceof SpanQuery
                && flattenSpan((SpanQuery) query, pathBoost, rewritten, reader, callback)) {
            // Actually nothing to do here, but it keeps the code lining up to
            // have it.
        } else if (!flattenUnknown(query, pathBoost, rewritten, reader, callback)) {
            if (query instanceof MultiTermQuery) {
                MultiTermQuery copy = (MultiTermQuery) query.clone();
                copy.setRewriteMethod(new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(
                        maxMultiTermQueryTerms));
                query = copy;
            }
            Query newRewritten;
            try {
                newRewritten = query.rewrite(reader);
            } catch (IOException e) {
                throw new WrappedExceptionFromLucene(e);
            }
            if (newRewritten != query) {
                // only rewrite once and then flatten again - the rewritten
                // query could have a special treatment
                flatten(newRewritten, pathBoost, query, reader, callback);
            }
        }
    }

    protected boolean flattenSpan(SpanQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        if (query instanceof SpanTermQuery) {
            flattenQuery((SpanTermQuery) query, pathBoost, rewritten, reader, callback);
            return true;
        } else if (query instanceof SpanPositionCheckQuery) {
            flattenQuery((SpanPositionCheckQuery) query, pathBoost, rewritten, reader, callback);
            return true;
        } else if (query instanceof SpanNearQuery) {
            flattenQuery((SpanNearQuery) query, pathBoost, rewritten, reader, callback);
            return true;
        } else if (query instanceof SpanNotQuery) {
            flattenQuery((SpanNotQuery) query, pathBoost, rewritten, reader, callback);
            return true;
        } else if (query instanceof SpanOrQuery) {
            flattenQuery((SpanOrQuery) query, pathBoost, rewritten, reader, callback);
            return true;
        }
        return false;
    }

    protected boolean flattenUnknown(Query query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        return false;
    }

    protected void flattenQuery(TermQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        callback.flattened(query.getTerm(), pathBoost * query.getBoost(), rewritten);
    }

    protected void flattenQuery(PhraseQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        float boost = pathBoost * query.getBoost();
        for (Term term : query.getTerms()) {
            callback.flattened(term, boost, rewritten);
        }
    }

    protected void flattenQuery(BooleanQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        for (BooleanClause clause : query) {
            if (!clause.isProhibited()) {
                flatten(clause.getQuery(), pathBoost * query.getBoost(), rewritten, reader,
                        callback);
            }
        }
    }

    protected void flattenQuery(DisjunctionMaxQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        float boost = pathBoost * query.getBoost();
        for (Query clause : query) {
            flatten(clause, boost, rewritten, reader, callback);
        }
    }

    protected void flattenQuery(ConstantScoreQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        if (query.getQuery() != null) {
            flatten(query.getQuery(), pathBoost * query.getBoost(), rewritten, reader, callback);
        }
        // TODO maybe flatten filter like Elasticsearch does
    }

    protected void flattenQuery(FilteredQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        if (query.getQuery() != null) {
            flatten(query.getQuery(), pathBoost * query.getBoost(), rewritten, reader, callback);
        }
        // TODO maybe flatten filter like Elasticsearch does
    }

    protected void flattenQuery(MultiPhraseQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        // Elasticsearch uses a more complicated method to preserve the phrase
        // queries. We can't use them so we go with something simpler.
        float boost = pathBoost * query.getBoost();
        for (Term[] terms : query.getTermArrays()) {
            for (Term term : terms) {
                callback.flattened(term, boost, rewritten);
            }
        }
    }

    protected void flattenQuery(SpanTermQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        callback.flattened(query.getTerm(), query.getBoost() * pathBoost, rewritten);
    }

    protected void flattenQuery(SpanPositionCheckQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        flattenSpan(query.getMatch(), pathBoost * query.getBoost(), rewritten, reader, callback);
    }

    protected void flattenQuery(SpanNearQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        pathBoost *= query.getBoost();
        for (SpanQuery clause : query.getClauses()) {
            flattenSpan(clause, pathBoost, rewritten, reader, callback);
        }
    }

    protected void flattenQuery(SpanNotQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        flattenSpan(query.getInclude(), query.getBoost() * pathBoost, rewritten, reader, callback);
    }

    protected void flattenQuery(SpanOrQuery query, float pathBoost, Query rewritten,
            IndexReader reader, Callback callback) {
        pathBoost *= query.getBoost();
        for (SpanQuery clause : query.getClauses()) {
            flattenSpan(clause, pathBoost, rewritten, reader, callback);
        }
    }
}
