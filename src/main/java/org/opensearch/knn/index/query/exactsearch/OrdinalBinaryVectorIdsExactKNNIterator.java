/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.opensearch.common.Nullable;
import org.opensearch.knn.index.SpaceType;
import org.opensearch.knn.index.query.SegmentLevelQuantizationInfo;
import org.opensearch.knn.index.vectorvalues.KNNBinaryVectorValues;
import org.opensearch.knn.index.vectorvalues.KNNFloatVectorValues;
import org.opensearch.knn.plugin.script.KNNScoringUtil;

import java.io.IOException;

/**
 * Iterator that uses DocIndexIterator ordinal from FloatVectorValues to advance BinaryVectorValues
 */
class OrdinalBinaryVectorIdsExactKNNIterator implements ExactKNNIterator {
    protected final DocIdSetIterator filterIdsIterator;
    protected final float[] queryVector;
    private final byte[] quantizedQueryVector;
    protected final KNNFloatVectorValues knnFloatVectorValues;
    protected final KNNBinaryVectorValues knnBinaryVectorValues;
    protected final SpaceType spaceType;
    protected float currentScore = Float.NEGATIVE_INFINITY;
    protected int docId;
    private final SegmentLevelQuantizationInfo segmentLevelQuantizationInfo;

    public OrdinalBinaryVectorIdsExactKNNIterator(
        @Nullable final DocIdSetIterator filterIdsIterator,
        final float[] queryVector,
        final KNNFloatVectorValues knnFloatVectorValues,
        final KNNBinaryVectorValues knnBinaryVectorValues,
        final SpaceType spaceType,
        final byte[] quantizedQueryVector,
        final SegmentLevelQuantizationInfo segmentLevelQuantizationInfo
    ) throws IOException {
        this.filterIdsIterator = filterIdsIterator;
        this.queryVector = queryVector;
        this.knnFloatVectorValues = knnFloatVectorValues;
        this.knnBinaryVectorValues = knnBinaryVectorValues;
        this.spaceType = spaceType;
        this.docId = getNextDocId();
        this.quantizedQueryVector = quantizedQueryVector;
        this.segmentLevelQuantizationInfo = segmentLevelQuantizationInfo;
    }

    @Override
    public int nextDoc() throws IOException {
        if (docId == DocIdSetIterator.NO_MORE_DOCS) {
            return DocIdSetIterator.NO_MORE_DOCS;
        }
        currentScore = computeScore();
        int currentDocId = docId;
        docId = getNextDocId();
        return currentDocId;
    }

    @Override
    public float score() {
        return currentScore;
    }

    protected float computeScore() throws IOException {
        if (segmentLevelQuantizationInfo == null) {
            final float[] vector = knnFloatVectorValues.getVector();
            return spaceType.getKnnVectorSimilarityFunction().compare(queryVector, vector);
        }

        byte[] quantizedVector = knnBinaryVectorValues.getVector();
        if (quantizedQueryVector == null) {
            return scoreWithADC(queryVector, quantizedVector, spaceType);
        }
        return SpaceType.HAMMING.getKnnVectorSimilarityFunction().compare(quantizedQueryVector, quantizedVector);
    }

    protected int getNextDocId() throws IOException {
        if (filterIdsIterator == null) {
            int nextDocID = knnFloatVectorValues.nextDoc();
            if (nextDocID != DocIdSetIterator.NO_MORE_DOCS && knnBinaryVectorValues != null) {
                DocIdSetIterator floatIterator = knnFloatVectorValues.getVectorValuesIterator().getDocIdSetIterator();
                if (floatIterator instanceof KnnVectorValues.DocIndexIterator) {
                    int ordinal = ((KnnVectorValues.DocIndexIterator) floatIterator).index();
                    knnBinaryVectorValues.advance(ordinal);
                }
            }
            return nextDocID;
        }
        int nextDocID = this.filterIdsIterator.nextDoc();
        if (nextDocID != DocIdSetIterator.NO_MORE_DOCS) {
            knnFloatVectorValues.advance(nextDocID);
            if (knnBinaryVectorValues != null) {
                DocIdSetIterator floatIterator = knnFloatVectorValues.getVectorValuesIterator().getDocIdSetIterator();
                if (floatIterator instanceof KnnVectorValues.DocIndexIterator) {
                    int ordinal = ((KnnVectorValues.DocIndexIterator) floatIterator).index();
                    knnBinaryVectorValues.advance(ordinal);
                }
            }
        }
        return nextDocID;
    }

    protected float scoreWithADC(float[] queryVector, byte[] documentVector, SpaceType spaceType) {
        if (spaceType.equals(SpaceType.L2)) {
            return SpaceType.L2.scoreTranslation(KNNScoringUtil.l2SquaredADC(queryVector, documentVector));
        } else if (spaceType.equals(SpaceType.INNER_PRODUCT)) {
            return SpaceType.INNER_PRODUCT.scoreTranslation((-1 * KNNScoringUtil.innerProductADC(queryVector, documentVector)));
        } else if (spaceType.equals(SpaceType.COSINESIMIL)) {
            return SpaceType.COSINESIMIL.scoreTranslation(1 - KNNScoringUtil.innerProductADC(queryVector, documentVector));
        }
        throw new UnsupportedOperationException("Space type " + spaceType.getValue() + " is not supported for ADC");
    }
}
