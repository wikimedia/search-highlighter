package org.wikimedia.highlighter.experimental.elasticsearch;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.BytesRefHash;
import org.elasticsearch.common.util.ObjectArray;
import org.wikimedia.highlighter.experimental.lucene.hit.weight.BasicQueryWeigher.SourceInfo;
import org.wikimedia.highlighter.experimental.lucene.hit.weight.BasicQueryWeigher.TermInfos;

public class BytesRefHashTermInfos implements TermInfos, Releasable {
    private static final long INITIAL_CAPACITY = 8;

    private final BigArrays bigArrays;
    private final BytesRefHash bytes;
    private ObjectArray<SourceInfo> infos;
    
    public BytesRefHashTermInfos(BigArrays bigArrays) {
        this.bigArrays = bigArrays;
        bytes = new BytesRefHash(INITIAL_CAPACITY, bigArrays);
        infos = bigArrays.newObjectArray(INITIAL_CAPACITY);
        // TODO switching from ObjectArray to something holding the floats and ints would be quickers, surely.
    }
    
    @Override
    public SourceInfo get(BytesRef term) {
        long id = bytes.find(term);
        if (id < 0) {
            return null;
        }
        return infos.get(id);
    }

    @Override
    public void put(BytesRef term, SourceInfo info) {
        long id = bytes.add(term);
        if (id < 0) {
            id = -1 - id;
        }
        if (id >= infos.size()) {
            infos = bigArrays.grow(infos, id + 1);
        }
        infos.set(id, info);
    }

    @Override
    public void close() throws ElasticsearchException {
        Releasables.close(bytes, infos);
    }
}
