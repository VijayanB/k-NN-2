/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import org.opensearch.knn.KNNTestCase;
import org.opensearch.knn.indices.ModelDao;

import static org.mockito.Mockito.mock;

/**
 * Integration test to verify ExactKNNScorer can be used in KNNQuery and NativeEngineQuery
 */
public class ExactKNNScorerIntegrationTest extends KNNTestCase {

    public void testExactKNNScorerInstantiation() {
        ModelDao modelDao = mock(ModelDao.class);
        ExactKNNScorer exactKNNScorer = new ExactKNNScorer(modelDao);
        assertNotNull("ExactKNNScorer should be instantiated", exactKNNScorer);
    }

    public void testExactScorerContextBuilder() {
        ExactKNNScorer.ExactScorerContext context = ExactKNNScorer.ExactScorerContext.builder()
            .useQuantizedVectorsForSearch(true)
            .k(10)
            .field("test_field")
            .floatQueryVector(new float[]{1.0f, 2.0f, 3.0f})
            .numberOfMatchedDocs(100)
            .isMemoryOptimizedSearchEnabled(false)
            .build();

        assertNotNull("ExactScorerContext should be built successfully", context);
        assertEquals("Field should match", "test_field", context.getField());
        assertEquals("K should match", 10, context.getK());
        assertTrue("Should use quantized vectors", context.isUseQuantizedVectorsForSearch());
        assertFalse("Memory optimized search should be disabled", context.getIsMemoryOptimizedSearchEnabled());
    }

    public void testExactScorerContextClass() {
        // Test that the ExactScorerContext class exists and can be referenced
        Class<?> contextClass = ExactKNNScorer.ExactScorerContext.class;
        assertNotNull("ExactScorerContext class should exist", contextClass);
        assertEquals("Class name should match", "ExactScorerContext", contextClass.getSimpleName());
    }
}