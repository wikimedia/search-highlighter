package org.wikimedia.highlighter.cirrus.opensearch;

import org.apache.lucene.util.BytesRef;
import org.opensearch.common.util.BigArrays;
import org.opensearch.common.util.BytesRefHash;
import org.opensearch.common.util.FloatArray;
import org.wikimedia.search.highlighter.cirrus.hit.weight.CachingTermWeigher;

/**
 * Implementation of CachingTermWeighter.Cache using Elasticsearch's BytesRef
 * hashing infrastructure.
 */
public class BytesRefTermWeigherCache implements CachingTermWeigher.Cache<BytesRef> {
    private static final long INITIAL_CAPACITY = 8;

    private final BigArrays bigArrays;
    private final BytesRefHash bytes;
    private FloatArray weights;

    public BytesRefTermWeigherCache(BigArrays bigArrays) {
        this.bigArrays = bigArrays;
        bytes = new BytesRefHash(INITIAL_CAPACITY, bigArrays);
        weights = bigArrays.newFloatArray(INITIAL_CAPACITY);
    }

    @Override
    public float get(BytesRef term) {
        long id = bytes.find(term);
        if (id < 0) {
            return -1;
        }
        return weights.get(id);
    }

    @Override
    public void put(BytesRef term, float weight) {
        long id = bytes.add(term);
        if (id < 0) {
            // Already seen it.  Odd.
            id = -1 - id;
        }
        if (id >= weights.size()) {
            weights = bigArrays.grow(weights, id + 1);
        }
        weights.set(id, weight);
    }

}
