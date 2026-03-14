/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.vectorvalues;

import org.apache.lucene.codecs.KnnFieldVectorsWriter;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.search.VectorScorer;
import org.opensearch.knn.index.SpaceType;

import java.io.IOException;
import java.util.Arrays;

/**
 * Concrete implementation of {@link KNNVectorValues} that returns float[] as vector and provides an abstraction over
 * {@link BinaryDocValues}, {@link FloatVectorValues}, {@link KnnFieldVectorsWriter} etc.
 */
public class KNNFloatVectorValues extends KNNVectorValues<float[]> {
    KNNFloatVectorValues(final KNNVectorValuesIterator vectorValuesIterator) {
        super(vectorValuesIterator);
    }

    @Override
    public float[] getVector() throws IOException {
        final float[] vector = VectorValueExtractorStrategy.extractFloatVector(vectorValuesIterator);
        this.dimension = vector.length;
        this.bytesPerVector = vector.length * 4;
        return vector;
    }

    @Override
    public float[] conditionalCloneVector() throws IOException {
        float[] vector = getVector();
        if (vectorValuesIterator.getDocIdSetIterator() instanceof KnnVectorValues.DocIndexIterator) {
            return Arrays.copyOf(vector, vector.length);
        }
        return vector;
    }

    /**
     * Returns a {@link VectorScorer} for the given float query vector.
     * @param target the float query vector
     * @param spaceType the space type for similarity computation
     * @return {@link VectorScorer}
     * @throws IOException if an I/O error occurs
     */
    public VectorScorer scorer(final float[] target, final SpaceType spaceType) throws IOException {
        return VectorScorerFactory.getScorer(
            (KNNVectorValuesIterator.DocIdsIteratorValues) vectorValuesIterator,
            target,
            ScoreMode.SCORE,
            spaceType
        );
    }

    /**
     * Returns a rescorer {@link VectorScorer} for the given float query vector.
     * @param target the float query vector
     * @param spaceType the space type for similarity computation
     * @return {@link VectorScorer}
     * @throws IOException if an I/O error occurs
     */
    public VectorScorer rescorer(final float[] target, final SpaceType spaceType) throws IOException {
        return VectorScorerFactory.getScorer(
            (KNNVectorValuesIterator.DocIdsIteratorValues) vectorValuesIterator,
            target,
            ScoreMode.RESCORE,
            spaceType
        );
    }
}
