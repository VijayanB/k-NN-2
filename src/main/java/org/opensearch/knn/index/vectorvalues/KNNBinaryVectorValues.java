/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.vectorvalues;

import lombok.ToString;
import org.apache.lucene.codecs.KnnFieldVectorsWriter;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.search.VectorScorer;
import org.opensearch.knn.index.SpaceType;

import java.io.IOException;
import java.util.Arrays;

/**
 * Concrete implementation of {@link KNNVectorValues} that returns byte[] as vector where binary vector is stored and
 * provides an abstraction over {@link BinaryDocValues}, {@link ByteVectorValues}, {@link KnnFieldVectorsWriter} etc.
 */
@ToString(callSuper = true)
public class KNNBinaryVectorValues extends KNNVectorValues<byte[]> {
    KNNBinaryVectorValues(KNNVectorValuesIterator vectorValuesIterator) {
        super(vectorValuesIterator);
    }

    @Override
    public byte[] getVector() throws IOException {
        final byte[] vector = VectorValueExtractorStrategy.extractBinaryVector(vectorValuesIterator);
        this.dimension = vector.length * Byte.SIZE;
        this.bytesPerVector = vector.length;
        return vector;
    }

    @Override
    public byte[] conditionalCloneVector() throws IOException {
        byte[] vector = getVector();
        if (vectorValuesIterator.getDocIdSetIterator() instanceof KnnVectorValues.DocIndexIterator) {
            return Arrays.copyOf(vector, vector.length);
        }
        return vector;
    }

    /**
     * Returns a {@link VectorScorer} for the given float query vector using ADC scoring.
     * @param target the float query vector
     * @param spaceType the space type for similarity computation
     * @return {@link VectorScorer}
     * @throws IOException if an I/O error occurs
     */
    public VectorScorer scorer(final float[] target, final SpaceType spaceType) throws IOException {
        return VectorScorers.createScorer(
            (KNNVectorValuesIterator.DocIdsIteratorValues) vectorValuesIterator,
            target,
            ScoreMode.SCORE,
            spaceType
        );
    }

    /**
     * Returns a rescorer {@link VectorScorer} for the given float query vector using ADC scoring.
     * @param target the float query vector
     * @param spaceType the space type for similarity computation
     * @return {@link VectorScorer}
     * @throws IOException if an I/O error occurs
     */
    public VectorScorer rescorer(final float[] target, final SpaceType spaceType) throws IOException {
        return VectorScorers.createScorer(
            (KNNVectorValuesIterator.DocIdsIteratorValues) vectorValuesIterator,
            target,
            ScoreMode.RESCORE,
            spaceType
        );
    }

    /**
     * Returns a {@link VectorScorer} for the given byte query vector.
     * @param target the byte query vector
     * @param spaceType the space type for similarity computation
     * @return {@link VectorScorer}
     * @throws IOException if an I/O error occurs
     */
    public VectorScorer scorer(final byte[] target, final SpaceType spaceType) throws IOException {
        return VectorScorers.createScorer(
            (KNNVectorValuesIterator.DocIdsIteratorValues) vectorValuesIterator,
            target,
            ScoreMode.SCORE,
            spaceType
        );
    }

    /**
     * Returns a rescorer {@link VectorScorer} for the given byte query vector.
     * @param target the byte query vector
     * @param spaceType the space type for similarity computation
     * @return {@link VectorScorer}
     * @throws IOException if an I/O error occurs
     */
    public VectorScorer rescorer(final byte[] target, final SpaceType spaceType) throws IOException {
        return VectorScorers.createScorer(
            (KNNVectorValuesIterator.DocIdsIteratorValues) vectorValuesIterator,
            target,
            ScoreMode.RESCORE,
            spaceType
        );
    }
}
