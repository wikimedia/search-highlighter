package org.wikimedia.highlighter.experimental.elasticsearch;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.lucene.search.XFilteredQuery;
import org.wikimedia.highlighter.experimental.lucene.QueryFlattener;

public class ElasticsearchQueryFlattener extends QueryFlattener {
    public ElasticsearchQueryFlattener(int maxMultiTermQueryTerms) {
        super(maxMultiTermQueryTerms);
    }

    @Override
    protected boolean flattenUnknown(Query query, float pathBoost, Query rewritten, IndexReader reader,
            Callback callback) {
        if (query instanceof XFilteredQuery) {
            flattenQuery((XFilteredQuery)query, pathBoost, rewritten, reader, callback);
        }
        return false;
    }
    
    protected void flattenQuery(XFilteredQuery query, float pathBoost, Query rewritten, IndexReader reader,
            Callback callback) {
        if (query.getQuery() != null) {
            flatten(query.getQuery(), pathBoost * query.getBoost(), rewritten, reader, callback);
        }
        // TODO maybe flatten filter like Elasticsearch does
    }
}
