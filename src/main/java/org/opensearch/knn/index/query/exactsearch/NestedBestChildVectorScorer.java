/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.util.BitSet;

import java.io.IOException;

public class NestedBestChildVectorScorer implements VectorScorer {
    private final VectorScorer vectorScorer;
    private final DocIdSetIterator vectorIterator;
    private final DocIdSetIterator acceptedChildrenIterator;
    private final BitSet parentBitSet;
    private final DocIdSetIterator iterator;
    private int bestChild = -1;
    private float currentScore = Float.NEGATIVE_INFINITY;

    public NestedBestChildVectorScorer(DocIdSetIterator acceptedChildrenIterator, BitSet parentBitSet, VectorScorer vectorScorer) {
        this.acceptedChildrenIterator = acceptedChildrenIterator;
        this.vectorScorer = vectorScorer;
        this.vectorIterator = vectorScorer.iterator();
        this.parentBitSet = parentBitSet;
        this.iterator = createIterator();
    }

    @Override
    public float score() throws IOException {
        return currentScore;
    }

    @Override
    public DocIdSetIterator iterator() {
        return iterator;
    }

    private DocIdSetIterator createIterator() {
        return new DocIdSetIterator() {
            @Override
            public int docID() {
                return bestChild;
            }

            @Override
            public int nextDoc() throws IOException {
                int nextChild = acceptedChildrenIterator.docID();
                if (nextChild == -1) {
                    nextChild = acceptedChildrenIterator.nextDoc();
                }
                if (nextChild == NO_MORE_DOCS) {
                    bestChild = NO_MORE_DOCS;
                    return NO_MORE_DOCS;
                }
                currentScore = Float.NEGATIVE_INFINITY;
                int currentParent = parentBitSet.nextSetBit(nextChild);
                bestChild = -1;
                while (nextChild != NO_MORE_DOCS && nextChild < currentParent) {
                    vectorIterator.advance(nextChild);
                    float score = vectorScorer.score();
                    if (score > currentScore) {
                        bestChild = nextChild;
                        currentScore = score;
                    }
                    nextChild = acceptedChildrenIterator.nextDoc();
                }
                return bestChild;
            }

            @Override
            public int advance(int target) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public long cost() {
                return acceptedChildrenIterator.cost();
            }
        };
    }
}
