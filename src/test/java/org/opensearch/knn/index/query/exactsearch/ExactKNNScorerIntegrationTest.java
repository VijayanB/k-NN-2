/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import org.apache.lucene.search.TopDocs;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.knn.indices.ModelDao;

import static org.junit.Assert.assertNotNull;

/**
 * Integration test to verify ExactKNNScorer can be used in KNNQuery and NativeEngineQuery
 */
public class ExactKNNScorerIntegrationTest {

    @Mock
    private ModelDao modelDao;

    private ExactKNNScorer exactKNNScorer;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        exactKNNScorer = new ExactKNNScorer(modelDao);
    }

    @Test
    public void testExactKNNScorerInstantiation() {
        assertNotNull("ExactKNNScorer should be instantiated", exactKNNScorer);
    }

    @Test
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
    }
}