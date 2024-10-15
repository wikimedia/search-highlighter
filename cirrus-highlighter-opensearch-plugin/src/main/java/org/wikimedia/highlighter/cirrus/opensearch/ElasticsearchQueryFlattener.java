package org.wikimedia.highlighter.cirrus.opensearch;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.opensearch.common.lucene.search.function.FunctionScoreQuery;
import org.wikimedia.highlighter.cirrus.lucene.QueryFlattener;

public class ElasticsearchQueryFlattener extends QueryFlattener {
    /**
     * Default configuration.
     */
    public ElasticsearchQueryFlattener() {
        super();
    }

    public ElasticsearchQueryFlattener(int maxMultiTermQueryTerms, boolean phraseAsTerms, boolean removeHighFrequencyTermsFromCommonTerms) {
        super(maxMultiTermQueryTerms, phraseAsTerms, removeHighFrequencyTermsFromCommonTerms);
    }

    @Override
    protected boolean flattenUnknown(Query query, float pathBoost, Object sourceOverride,
            IndexReader reader, Callback callback) {
        if (query instanceof FunctionScoreQuery) {
            flattenQuery((FunctionScoreQuery) query, pathBoost, sourceOverride, reader,
                    callback);
            return true;
        }
        return false;
    }

    protected void flattenQuery(FunctionScoreQuery query, float pathBoost,
            Object sourceOverride, IndexReader reader, Callback callback) {
        if (query.getSubQuery() != null) {
            flatten(query.getSubQuery(), pathBoost, sourceOverride, reader, callback);
        }
    }
}
