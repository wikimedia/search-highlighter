package com.github.nik9000.expiremental.highlighter.lucene;

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
 * Flattens {@link Query}s similarly to Lucene's {@link FieldQuery}.
 */
public class QueryFlattener {
    private final int maxMultiTermQueryTerms;

    public QueryFlattener(int maxMultiTermQueryTerms) {
        this.maxMultiTermQueryTerms = maxMultiTermQueryTerms;
    }

    public interface Callback {
        // TODO might be simpler to just accept Term and pathBoost....
        void flattened(TermQuery query, float pathBoost);

        void flattened(PhraseQuery query, float pathBoost);
    }

    public void flatten(Query query, IndexReader reader, Callback callback) {
        flatten(query, 1f, reader, callback);
    }

    protected void flatten(Query query, float pathBoost, IndexReader reader, Callback callback) {
        if (query instanceof TermQuery) {
            callback.flattened((TermQuery) query, pathBoost);
        } else if (query instanceof PhraseQuery) {
            callback.flattened((PhraseQuery) query, pathBoost);
        } else if (query instanceof BooleanQuery) {
            flattenQuery((BooleanQuery) query, pathBoost, reader, callback);
        } else if (query instanceof DisjunctionMaxQuery) {
            flattenQuery((DisjunctionMaxQuery) query, pathBoost, reader, callback);
        } else if (query instanceof ConstantScoreQuery) {
            flattenQuery((ConstantScoreQuery) query, pathBoost, reader, callback);
        } else if (query instanceof FilteredQuery) {
            flattenQuery((FilteredQuery) query, pathBoost, reader, callback);
        } else if (query instanceof MultiPhraseQuery) {
            flattenQuery((MultiPhraseQuery) query, pathBoost, reader, callback);
        } else if (query instanceof SpanQuery
                && flattenSpan((SpanQuery) query, pathBoost, reader, callback)) {
            // Actually nothing to do here, but it keeps the code lining up to
            // have it.
        } else if (!flattenUnknown(query, pathBoost, reader, callback)) {
            if (query instanceof MultiTermQuery) {
                MultiTermQuery copy = (MultiTermQuery) query.clone();
                copy.setRewriteMethod(new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(
                        maxMultiTermQueryTerms));
                query = copy;
            }
            Query rewritten;
            try {
                rewritten = query.rewrite(reader);
            } catch (IOException e) {
                throw new WrappedExceptionFromLucene(e);
            }
            if (rewritten != query) {
                // only rewrite once and then flatten again - the rewritten
                // query could have a special treatment
                flatten(rewritten, pathBoost, reader, callback);
            }
        }
    }

    protected boolean flattenSpan(SpanQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        if (query instanceof SpanTermQuery) {
            flattenQuery((SpanTermQuery) query, pathBoost, reader, callback);
            return true;
        } else if (query instanceof SpanPositionCheckQuery) {
            flattenQuery((SpanPositionCheckQuery) query, pathBoost, reader, callback);
            return true;
        } else if (query instanceof SpanNearQuery) {
            flattenQuery((SpanNearQuery) query, pathBoost, reader, callback);
            return true;
        } else if (query instanceof SpanNotQuery) {
            flattenQuery((SpanNotQuery) query, pathBoost, reader, callback);
            return true;
        } else if (query instanceof SpanOrQuery) {
            flattenQuery((SpanOrQuery) query, pathBoost, reader, callback);
            return true;
        }
        return false;
    }

    protected boolean flattenUnknown(Query query, float pathBoost, IndexReader reader,
            Callback callback) {
        return false;
    }

    protected void flattenQuery(BooleanQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        for (BooleanClause clause : query) {
            if (!clause.isProhibited()) {
                flatten(clause.getQuery(), pathBoost * query.getBoost(), reader, callback);
            }
        }
    }

    protected void flattenQuery(DisjunctionMaxQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        for (Query clause : query) {
            flatten(clause, pathBoost * query.getBoost(), reader, callback);
        }
    }

    protected void flattenQuery(ConstantScoreQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        if (query.getQuery() != null) {
            flatten(query.getQuery(), pathBoost * query.getBoost(), reader, callback);
        }
        // TODO maybe flatten filter like Elasticsearch does
    }

    protected void flattenQuery(FilteredQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        if (query.getQuery() != null) {
            flatten(query.getQuery(), pathBoost * query.getBoost(), reader, callback);
        }
        // TODO maybe flatten filter like Elasticsearch does
    }

    protected void flattenQuery(MultiPhraseQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        // Elasticsearch uses a more complicated method to preserve the phrase
        // queries. We can't use them so we go with something simpler.
        pathBoost *= query.getBoost();
        for (Term[] terms : query.getTermArrays()) {
            for (Term term : terms) {
                callback.flattened(new TermQuery(term), pathBoost);
            }
        }
    }

    protected void flattenQuery(SpanTermQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        callback.flattened(new TermQuery(query.getTerm()), query.getBoost() * pathBoost);
    }

    protected void flattenQuery(SpanPositionCheckQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        flattenSpan(query.getMatch(), pathBoost * query.getBoost(), reader, callback);
    }

    protected void flattenQuery(SpanNearQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        pathBoost *= query.getBoost();
        for (SpanQuery clause : query.getClauses()) {
            flattenSpan(clause, pathBoost, reader, callback);
        }
    }

    protected void flattenQuery(SpanNotQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        flattenSpan(query.getInclude(), query.getBoost() * pathBoost, reader, callback);
    }

    protected void flattenQuery(SpanOrQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        pathBoost *= query.getBoost();
        for (SpanQuery clause : query.getClauses()) {
            flattenSpan(clause, pathBoost, reader, callback);
        }
    }
}
