/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.codec.KNN990Codec;

import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.hnsw.RandomAccessVectorValues;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class FaissBinaryVectorValues extends ByteVectorValues implements RandomAccessVectorValues.Bytes {
    private static final int SIZET_SIZE = RamUsageEstimator.primitiveSizes.get(Long.TYPE);
    private final int dimension;
    private final int size;
    private final IndexInput byteSlice;
    private final long idOffset;
    private final long vectordataOffset;
    private int lastOrd = -1;
    private int doc = -1;
    private final long[] ids;
    private final int byteSize;
    private final VectorSimilarityFunction similarityFunction;
    protected final byte[] binaryValue;
    protected final ByteBuffer byteBuffer;

    FaissBinaryVectorValues(
        int dimension,
        int size,
        long idOffset,
        long vectorDataOffset,
        int byteSize,
        IndexInput byteSlice,
        VectorSimilarityFunction similarityFunction
    ) throws IOException {
        this.dimension = dimension;
        this.size = size;
        this.byteSlice = byteSlice;
        this.similarityFunction = similarityFunction;
        this.idOffset = idOffset;
        this.vectordataOffset = vectorDataOffset;
        this.byteSize = byteSize;
        this.ids = new long[size];
        this.byteBuffer = ByteBuffer.allocate(byteSize);
        this.binaryValue = this.byteBuffer.array();
        readIds();
    }

    private void readIds() throws IOException {
        byteSlice.seek(idOffset);
        long size = byteSlice.readLong();
        assert size == this.size;
        byteSlice.readLongs(ids, 0, this.size);
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
    public byte[] vectorValue() throws IOException {
        return this.vectorValue(this.doc);
    }

    @Override
    public VectorScorer scorer(byte[] bytes) throws IOException {
        return null;
    }

    public static FaissBinaryVectorValues load(
        VectorSimilarityFunction vectorSimilarityFunction,
        VectorEncoding vectorEncoding,
        int dimension,
        int size,
        long idOffset,
        long vectorDataOffset,
        IndexInput vectorData
    ) throws IOException {
        if (vectorEncoding == VectorEncoding.BYTE) {
            return new FaissBinaryVectorValues(
                dimension,
                size,
                idOffset,
                vectorDataOffset,
                dimension,
                vectorData,
                vectorSimilarityFunction
            );
        }
        return new EmptyFaissBinaryVectorValues(dimension, vectorSimilarityFunction);
    }

    @Override
    public int docID() {
        return doc;
    }

    @Override
    public int nextDoc() throws IOException {
        return advance(doc + 1);
    }

    @Override
    public int advance(int target) throws IOException {
        assert this.docID() < target;
        int index = Arrays.binarySearch(ids, doc + 1, ids.length, target);
        // If index is negative, it means the target was not found,
        // and we have to move to next id in the array
        if (index < 0) {
            index = -(index + 1);
        }
        return index >= this.size ? (this.doc = NO_MORE_DOCS) : (this.doc = (int) ids[index]);
    }

    @Override
    public FaissBinaryVectorValues copy() throws IOException {
        return new FaissBinaryVectorValues(
            this.dimension,
            this.size,
            this.idOffset,
            this.vectordataOffset,
            this.byteSize,
            this.byteSlice.clone(),
            this.similarityFunction
        );
    }

    @Override
    public byte[] vectorValue(int targetOrd) throws IOException {
        if (this.lastOrd != targetOrd) {
            this.readValue(targetOrd);
            this.lastOrd = targetOrd;
        }
        return this.binaryValue;
    }

    private void readValue(int targetOrd) throws IOException {
        byteSlice.seek(vectordataOffset + (long) SIZET_SIZE * (long) targetOrd * this.byteSize);
        byteSlice.readBytes(this.byteBuffer.array(), this.byteBuffer.arrayOffset(), this.byteSize);
    }

    private static class EmptyFaissBinaryVectorValues extends FaissBinaryVectorValues {
        private int doc = -1;

        public EmptyFaissBinaryVectorValues(int dimension, VectorSimilarityFunction similarityFunction) throws IOException {
            super(dimension, 0, 0, 0, 0, null, similarityFunction);
        }

        public int dimension() {
            return super.dimension();
        }

        public int size() {
            return 0;
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

        public FaissBinaryVectorValues.EmptyFaissBinaryVectorValues copy() {
            throw new UnsupportedOperationException();
        }
    }
}
