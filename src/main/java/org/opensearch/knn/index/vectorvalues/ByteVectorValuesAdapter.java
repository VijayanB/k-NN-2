/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.vectorvalues;

import org.apache.lucene.codecs.hnsw.FlatVectorsScorer;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.util.hnsw.RandomVectorScorer;
import org.opensearch.knn.index.KNNVectorSimilarityFunction;
import org.opensearch.knn.memoryoptsearch.faiss.FlatVectorsScorerProvider;

import java.io.IOException;

/**
 * Adapter to convert ByteVectorValues to KNNBinaryVectorValues
 */
public class ByteVectorValuesAdapter {
    
    public static KNNBinaryVectorValues adapt(ByteVectorValues byteVectorValues) throws IOException {
        KNNVectorValuesIterator.DocIdsIteratorValues iterator =
            new KNNVectorValuesIterator.DocIdsIteratorValues(byteVectorValues);
        return new KNNBinaryVectorValues(iterator);
    }
}
