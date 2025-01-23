/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.codec.KNN990Codec;

import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.hnsw.RandomAccessVectorValues;

import java.io.IOException;

public class FaissFloatVectorValues extends FloatVectorValues implements RandomAccessVectorValues.Floats {
    private final int dimension;
    private final int size;
    private final IndexInput byteSlice;
    private final long idOffset;
    private final long vectordataOffset;
    private int lastOrd = -1;
    private final float[] value;
    private final long[] ids;
    private final VectorSimilarityFunction similarityFunction;

    FaissFloatVectorValues(
        int dimension,
        int size,
        long idOffset,
        long vectorDataOffset,
        IndexInput byteSlice,
        VectorSimilarityFunction similarityFunction
    ) {
        this.dimension = dimension;
        this.size = size;
        this.byteSlice = byteSlice;
        this.similarityFunction = similarityFunction;
        this.idOffset = idOffset;
        this.vectordataOffset = vectorDataOffset;
        this.value = new float[dimension];
        this.ids = new long[size];
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public float[] vectorValue() throws IOException {
        return new float[0];
    }

    @Override
    public VectorScorer scorer(float[] floats) throws IOException {
        return null;
    }

    @Override
    public int docID() {
        return 0;
    }

    @Override
    public int nextDoc() throws IOException {
        return 0;
    }

    @Override
    public int advance(int i) throws IOException {
        return 0;
    }

    @Override
    public Floats copy() throws IOException {
        return null;
    }

    @Override
    public float[] vectorValue(int i) throws IOException {
        return new float[0];
    }

    public static FaissFloatVectorValues load(
        VectorSimilarityFunction vectorSimilarityFunction,
        VectorEncoding vectorEncoding,
        int dimension,
        int size,
        long idOffset,
        long vectorDataOffset,
        IndexInput vectorData
    ) throws IOException {
        if (vectorEncoding == VectorEncoding.FLOAT32) {
            return new FaissFloatVectorValues(dimension, size, idOffset, vectorDataOffset, vectorData, vectorSimilarityFunction);
        }
        return new EmptyFaissFloatVectorValues(dimension, vectorSimilarityFunction);
    }

    private static class EmptyFaissFloatVectorValues extends FaissFloatVectorValues {
        private int doc = -1;

        public EmptyFaissFloatVectorValues(int dimension, VectorSimilarityFunction similarityFunction) {
            super(dimension, 0, 0, 0, null, similarityFunction);
        }

        public int dimension() {
            return super.dimension();
        }

        public int size() {
            return 0;
        }

        public float[] vectorValue() throws IOException {
            throw new UnsupportedOperationException();
        }

        public int docID() {
            return this.doc;
        }

        public int nextDoc() throws IOException {
            return this.advance(this.doc + 1);
        }

        public int advance(int target) {
            return this.doc = Integer.MAX_VALUE;
        }

        public FaissFloatVectorValues.EmptyFaissFloatVectorValues copy() {
            throw new UnsupportedOperationException();
        }

        public float[] vectorValue(int targetOrd) {
            throw new UnsupportedOperationException();
        }

        public int ordToDoc(int ord) {
            throw new UnsupportedOperationException();
        }

        public Bits getAcceptOrds(Bits acceptDocs) {
            return null;
        }

        public VectorScorer scorer(float[] query) {
            return null;
        }
    }
}
