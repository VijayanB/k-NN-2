/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.vectorvalues;

import org.apache.lucene.search.DocAndFloatFeatureBuffer;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.Bits;

import java.io.IOException;

/**
 * A {@link VectorScorer.Bulk} implementation for nested documents that groups child docs by parent
 * and returns only the best-scoring child per parent.
 * <p>
 * The delegate bulk scorer produces batches of (childDocId, score) pairs. This wrapper deduplicates
 * them by parent boundary using the provided {@link BitSet}, keeping only the highest-scoring child
 * for each parent.
 */
public class NestedBulkScorer implements VectorScorer.Bulk {

    private final VectorScorer.Bulk delegate;
    private final BitSet parentBitSet;

    public NestedBulkScorer(final VectorScorer.Bulk delegate, final BitSet parentBitSet) {
        this.delegate = delegate;
        this.parentBitSet = parentBitSet;
    }

    @Override
    public float nextDocsAndScores(final int upTo, final Bits liveDocs, final DocAndFloatFeatureBuffer buffer) throws IOException {
        float maxScore = delegate.nextDocsAndScores(upTo, liveDocs, buffer);
        if (buffer.size == 0) {
            return maxScore;
        }

        // Deduplicate: keep only the best child per parent
        int outSize = 0;
        float globalMax = Float.NEGATIVE_INFINITY;
        int i = 0;
        while (i < buffer.size) {
            final int currentParent = parentBitSet.nextSetBit(buffer.docs[i]);
            int bestIdx = i;
            float bestScore = buffer.features[i];
            i++;
            while (i < buffer.size && parentBitSet.nextSetBit(buffer.docs[i]) == currentParent) {
                if (buffer.features[i] > bestScore) {
                    bestScore = buffer.features[i];
                    bestIdx = i;
                }
                i++;
            }
            buffer.docs[outSize] = buffer.docs[bestIdx];
            buffer.features[outSize] = bestScore;
            globalMax = Math.max(globalMax, bestScore);
            outSize++;
        }
        buffer.size = outSize;
        return globalMax;
    }
}
