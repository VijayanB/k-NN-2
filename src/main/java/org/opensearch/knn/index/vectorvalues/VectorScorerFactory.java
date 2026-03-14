/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.vectorvalues;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.util.ArrayUtil;
import org.opensearch.knn.index.SpaceType;
import org.opensearch.knn.index.codec.util.KNNVectorAsCollectionOfFloatsSerializer;
import org.opensearch.knn.plugin.script.KNNScoringUtil;

import java.io.IOException;

/**
 * Factory to obtain a {@link VectorScorer} from {@link KNNVectorValuesIterator.DocIdsIteratorValues}
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VectorScorerFactory {

    /**
     * Returns a {@link VectorScorer} for the given float query vector based on the {@link ScoreMode}.
     * If the underlying vector values are {@link ByteVectorValues}, an ADC scorer is created using the provided {@link SpaceType}.
     * If the underlying iterator is {@link BinaryDocValues}, a scorer is created by deserializing vectors from the doc values.
     * @param docIdsIteratorValues {@link KNNVectorValuesIterator.DocIdsIteratorValues}
     * @param target the float query vector
     * @param scoreMode {@link ScoreMode} to determine scorer or rescorer
     * @param spaceType {@link SpaceType} used for ADC scoring when vector values are byte-quantized, or for BinaryDocValues scoring
     * @return {@link VectorScorer}
     * @throws IOException if an I/O error occurs
     */
    public static VectorScorer getScorer(
        final KNNVectorValuesIterator.DocIdsIteratorValues docIdsIteratorValues,
        final float[] target,
        final ScoreMode scoreMode,
        final SpaceType spaceType
    ) throws IOException {
        final DocIdSetIterator docIdSetIterator = docIdsIteratorValues.getDocIdSetIterator();

        if (docIdSetIterator instanceof BinaryDocValues binaryDocValues) {
            return createBinaryDocValuesScorer(binaryDocValues, target, spaceType);
        }

        final KnnVectorValues knnVectorValues = docIdsIteratorValues.getKnnVectorValues();
        if (knnVectorValues instanceof FloatVectorValues floatVectorValues) {
            return scoreMode.getScorer(floatVectorValues, target);
        }
        if (knnVectorValues instanceof ByteVectorValues byteVectorValues) {
            return createADCScorer(byteVectorValues, target, spaceType);
        }
        throw new IllegalArgumentException("Unsupported KnnVectorValues type: " + knnVectorValues.getClass().getSimpleName());
    }

    /**
     * Returns a {@link VectorScorer} for the given byte query vector based on the {@link ScoreMode}.
     * If the underlying iterator is {@link BinaryDocValues}, a scorer is created by deserializing vectors from the doc values.
     * @param docIdsIteratorValues {@link KNNVectorValuesIterator.DocIdsIteratorValues}
     * @param target the byte query vector
     * @param scoreMode {@link ScoreMode} to determine scorer or rescorer
     * @param spaceType {@link SpaceType} used for BinaryDocValues scoring
     * @return {@link VectorScorer}
     * @throws IOException if an I/O error occurs
     */
    public static VectorScorer getScorer(
        final KNNVectorValuesIterator.DocIdsIteratorValues docIdsIteratorValues,
        final byte[] target,
        final ScoreMode scoreMode,
        final SpaceType spaceType
    ) throws IOException {
        final DocIdSetIterator docIdSetIterator = docIdsIteratorValues.getDocIdSetIterator();

        if (docIdSetIterator instanceof BinaryDocValues binaryDocValues) {
            return createBinaryDocValuesScorer(binaryDocValues, target, spaceType);
        }

        final KnnVectorValues knnVectorValues = docIdsIteratorValues.getKnnVectorValues();
        if (knnVectorValues instanceof ByteVectorValues byteVectorValues) {
            return scoreMode.getScorer(byteVectorValues, target);
        }
        throw new IllegalArgumentException("Byte target requires ByteVectorValues but got " + knnVectorValues.getClass().getSimpleName());
    }

    /**
     * Creates a {@link VectorScorer} for float query vector against float vectors stored in {@link BinaryDocValues}.
     */
    private static VectorScorer createBinaryDocValuesScorer(
        final BinaryDocValues binaryDocValues,
        final float[] target,
        final SpaceType spaceType
    ) {
        return new VectorScorer() {
            @Override
            public float score() throws IOException {
                final float[] docVector = KNNVectorAsCollectionOfFloatsSerializer.INSTANCE.byteToFloatArray(binaryDocValues.binaryValue());
                return spaceType.getKnnVectorSimilarityFunction().compare(target, docVector);
            }

            @Override
            public DocIdSetIterator iterator() {
                return binaryDocValues;
            }
        };
    }

    /**
     * Creates a {@link VectorScorer} for byte query vector against byte vectors stored in {@link BinaryDocValues}.
     */
    private static VectorScorer createBinaryDocValuesScorer(
        final BinaryDocValues binaryDocValues,
        final byte[] target,
        final SpaceType spaceType
    ) {
        return new VectorScorer() {
            @Override
            public float score() throws IOException {
                final var bytesRef = binaryDocValues.binaryValue();
                final byte[] docVector = ArrayUtil.copyOfSubArray(bytesRef.bytes, bytesRef.offset, bytesRef.offset + bytesRef.length);
                return spaceType.getKnnVectorSimilarityFunction().compare(target, docVector);
            }

            @Override
            public DocIdSetIterator iterator() {
                return binaryDocValues;
            }
        };
    }

    /**
     * Creates an ADC (Asymmetric Distance Computation) {@link VectorScorer} that scores a float query vector
     * against quantized byte document vectors.
     */
    private static VectorScorer createADCScorer(
        final ByteVectorValues byteVectorValues,
        final float[] target,
        final SpaceType spaceType
    ) {
        final KnnVectorValues.DocIndexIterator iterator = byteVectorValues.iterator();
        return new VectorScorer() {
            @Override
            public float score() throws IOException {
                return KNNScoringUtil.scoreWithADC(target, byteVectorValues.vectorValue(iterator.index()), spaceType);
            }

            @Override
            public DocIdSetIterator iterator() {
                return iterator;
            }
        };
    }
}
