package expiremental.highlighter.lucene;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * Flattens {@link Query}s similarly to Lucene's {@link FieldQuery}.
 */
public class QueryFlattener {
    private final int maxMultiTermQueryTerms;

    public QueryFlattener(int maxMultiTermQueryTerms) {
        this.maxMultiTermQueryTerms = maxMultiTermQueryTerms;
    }

    public interface Callback {
        void flattened(TermQuery query, float pathBoost);

        void flattened(PhraseQuery query, float pathBoost);
    }

    public void flatten(Query query, IndexReader reader, Callback callback) {
        flatten(query, 1f, reader, callback);
    }

    private void flatten(Query query, float pathBoost, IndexReader reader, Callback callback) {
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
        } else if (!flattenUnknown(query, pathBoost, reader, callback)) {
            if (query instanceof MultiTermQuery) {
                MultiTermQuery copy = (MultiTermQuery) query.clone();
                copy.setRewriteMethod(new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(maxMultiTermQueryTerms));
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
}
