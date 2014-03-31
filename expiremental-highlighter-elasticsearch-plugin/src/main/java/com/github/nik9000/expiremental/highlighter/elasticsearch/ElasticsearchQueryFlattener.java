package com.github.nik9000.expiremental.highlighter.elasticsearch;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.lucene.search.XFilteredQuery;

import com.github.nik9000.expiremental.highlighter.lucene.QueryFlattener;

public class ElasticsearchQueryFlattener extends QueryFlattener {
    public ElasticsearchQueryFlattener(int maxMultiTermQueryTerms) {
        super(maxMultiTermQueryTerms);
    }

    protected boolean flattenUnknown(Query query, float pathBoost, IndexReader reader,
            Callback callback) {
        if (query instanceof XFilteredQuery) {
            flattenQuery((XFilteredQuery)query, pathBoost, reader, callback);
        }
        return false;
    }
    
    protected void flattenQuery(XFilteredQuery query, float pathBoost, IndexReader reader,
            Callback callback) {
        if (query.getQuery() != null) {
            flatten(query.getQuery(), pathBoost * query.getBoost(), reader, callback);
        }
        // TODO maybe flatten filter like Elasticsearch does
    }
}
