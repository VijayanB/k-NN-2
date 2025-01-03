/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.mapper;

/**
 * Transform given vector into new value of same type. This can be used by parser if they want
 * to transform parsed value into new value before passing value to the next stage
 */
public interface VectorTransformer {
    /**
     * Transforms given float[] vector into new value
     * @param vector input value to transform
     * @return transformed vector
     */
    default float[] transform(float[] vector) {
        return vector;
    }

    VectorTransformer NOOP_VECTOR_TRANSFORMER = new VectorTransformer() {
    };
}
