/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.vectorvalues;

import lombok.SneakyThrows;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.search.VectorScorer;
import org.mockito.Mockito;
import org.opensearch.knn.KNNTestCase;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ScoreModeTests extends KNNTestCase {

    @SneakyThrows
    public void testScore_delegatesToScorer_forFloatVectorValues() {
        final FloatVectorValues floatVectorValues = Mockito.mock(FloatVectorValues.class);
        final VectorScorer expectedScorer = Mockito.mock(VectorScorer.class);
        final float[] target = new float[] { 1.0f };
        when(floatVectorValues.scorer(target)).thenReturn(expectedScorer);

        final VectorScorer result = ScoreMode.SCORE.getScorer(floatVectorValues, target);

        assertSame(expectedScorer, result);
        verify(floatVectorValues).scorer(target);
    }

    @SneakyThrows
    public void testRescore_delegatesToRescorer_forFloatVectorValues() {
        final FloatVectorValues floatVectorValues = Mockito.mock(FloatVectorValues.class);
        final VectorScorer expectedScorer = Mockito.mock(VectorScorer.class);
        final float[] target = new float[] { 1.0f };
        when(floatVectorValues.rescorer(target)).thenReturn(expectedScorer);

        final VectorScorer result = ScoreMode.RESCORE.getScorer(floatVectorValues, target);

        assertSame(expectedScorer, result);
        verify(floatVectorValues).rescorer(target);
    }

    @SneakyThrows
    public void testScore_delegatesToScorer_forByteVectorValues() {
        final ByteVectorValues byteVectorValues = Mockito.mock(ByteVectorValues.class);
        final VectorScorer expectedScorer = Mockito.mock(VectorScorer.class);
        final byte[] target = new byte[] { 1 };
        when(byteVectorValues.scorer(target)).thenReturn(expectedScorer);

        final VectorScorer result = ScoreMode.SCORE.getScorer(byteVectorValues, target);

        assertSame(expectedScorer, result);
        verify(byteVectorValues).scorer(target);
    }

    @SneakyThrows
    public void testRescore_delegatesToRescorer_forByteVectorValues() {
        final ByteVectorValues byteVectorValues = Mockito.mock(ByteVectorValues.class);
        final VectorScorer expectedScorer = Mockito.mock(VectorScorer.class);
        final byte[] target = new byte[] { 1 };
        when(byteVectorValues.rescorer(target)).thenReturn(expectedScorer);

        final VectorScorer result = ScoreMode.RESCORE.getScorer(byteVectorValues, target);

        assertSame(expectedScorer, result);
        verify(byteVectorValues).rescorer(target);
    }
}
