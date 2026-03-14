/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.vectorvalues;

import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.search.VectorScorer;

import java.io.IOException;

/**
 * Determines how a {@link VectorScorer} is obtained from vector values.
 */
public interface ScoreMode {

    ScoreMode SCORE = new ScoreMode() {
        @Override
        public VectorScorer getScorer(FloatVectorValues vectorValues, float[] target) throws IOException {
            return vectorValues.scorer(target);
        }

        @Override
        public VectorScorer getScorer(ByteVectorValues vectorValues, byte[] target) throws IOException {
            return vectorValues.scorer(target);
        }
    };

    ScoreMode RESCORE = new ScoreMode() {
        @Override
        public VectorScorer getScorer(FloatVectorValues vectorValues, float[] target) throws IOException {
            return vectorValues.rescorer(target);
        }

        @Override
        public VectorScorer getScorer(ByteVectorValues vectorValues, byte[] target) throws IOException {
            return vectorValues.rescorer(target);
        }
    };

    VectorScorer getScorer(FloatVectorValues vectorValues, float[] target) throws IOException;

    VectorScorer getScorer(ByteVectorValues vectorValues, byte[] target) throws IOException;
}
