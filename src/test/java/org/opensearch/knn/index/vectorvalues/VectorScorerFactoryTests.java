/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.vectorvalues;

import lombok.SneakyThrows;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.VectorScorer;
import org.opensearch.knn.KNNTestCase;
import org.opensearch.knn.index.SpaceType;
import org.opensearch.knn.plugin.script.KNNScoringUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VectorScorerFactoryTests extends KNNTestCase {

    // ======================== Float target with FloatVectorValues ========================

    @SneakyThrows
    public void testGetScorer_floatTarget_floatVectorValues_score() {
        final float[] target = { 1.0f, 2.0f };
        final VectorScorer expectedScorer = mock(VectorScorer.class);
        final FloatVectorValues floatVectorValues = mock(FloatVectorValues.class);
        when(floatVectorValues.scorer(target)).thenReturn(expectedScorer);

        final var iterator = mock(KNNVectorValuesIterator.DocIdsIteratorValues.class);
        when(iterator.getDocIdSetIterator()).thenReturn(mock(KnnVectorValues.DocIndexIterator.class));
        when(iterator.getKnnVectorValues()).thenReturn(floatVectorValues);

        final VectorScorer result = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, SpaceType.L2);

        assertSame(expectedScorer, result);
    }

    @SneakyThrows
    public void testGetScorer_floatTarget_floatVectorValues_rescore() {
        final float[] target = { 1.0f, 2.0f };
        final VectorScorer expectedScorer = mock(VectorScorer.class);
        final FloatVectorValues floatVectorValues = mock(FloatVectorValues.class);
        when(floatVectorValues.rescorer(target)).thenReturn(expectedScorer);

        final var iterator = mock(KNNVectorValuesIterator.DocIdsIteratorValues.class);
        when(iterator.getDocIdSetIterator()).thenReturn(mock(KnnVectorValues.DocIndexIterator.class));
        when(iterator.getKnnVectorValues()).thenReturn(floatVectorValues);

        final VectorScorer result = VectorScorerFactory.getScorer(iterator, target, ScoreMode.RESCORE, SpaceType.L2);

        assertSame(expectedScorer, result);
    }

    // ======================== Byte target with ByteVectorValues ========================

    @SneakyThrows
    public void testGetScorer_byteTarget_byteVectorValues_score() {
        final byte[] target = { 1, 2 };
        final VectorScorer expectedScorer = mock(VectorScorer.class);
        final ByteVectorValues byteVectorValues = mock(ByteVectorValues.class);
        when(byteVectorValues.scorer(target)).thenReturn(expectedScorer);

        final var iterator = mock(KNNVectorValuesIterator.DocIdsIteratorValues.class);
        when(iterator.getDocIdSetIterator()).thenReturn(mock(KnnVectorValues.DocIndexIterator.class));
        when(iterator.getKnnVectorValues()).thenReturn(byteVectorValues);

        final VectorScorer result = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, SpaceType.L2);

        assertSame(expectedScorer, result);
    }

    @SneakyThrows
    public void testGetScorer_byteTarget_byteVectorValues_rescore() {
        final byte[] target = { 1, 2 };
        final VectorScorer expectedScorer = mock(VectorScorer.class);
        final ByteVectorValues byteVectorValues = mock(ByteVectorValues.class);
        when(byteVectorValues.rescorer(target)).thenReturn(expectedScorer);

        final var iterator = mock(KNNVectorValuesIterator.DocIdsIteratorValues.class);
        when(iterator.getDocIdSetIterator()).thenReturn(mock(KnnVectorValues.DocIndexIterator.class));
        when(iterator.getKnnVectorValues()).thenReturn(byteVectorValues);

        final VectorScorer result = VectorScorerFactory.getScorer(iterator, target, ScoreMode.RESCORE, SpaceType.L2);

        assertSame(expectedScorer, result);
    }

    // ======================== Float target with BinaryDocValues ========================

    @SneakyThrows
    public void testGetScorer_floatTarget_binaryDocValues() {
        final SpaceType spaceType = SpaceType.L2;
        final List<float[]> vectors = List.of(new float[] { 1.0f, 0.0f }, new float[] { 0.0f, 1.0f });
        final float[] target = { 1.0f, 0.0f };
        final var binaryDocValues = new TestVectorValues.PredefinedFloatVectorBinaryDocValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(binaryDocValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, spaceType);

        assertNotNull(scorer);
        assertSame(binaryDocValues, scorer.iterator());
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    // ======================== Byte target with BinaryDocValues ========================

    @SneakyThrows
    public void testGetScorer_byteTarget_binaryDocValues() {
        final SpaceType spaceType = SpaceType.L2;
        final List<byte[]> vectors = List.of(new byte[] { 10, 20 }, new byte[] { 30, 40 });
        final byte[] target = { 10, 20 };
        final var binaryDocValues = new TestVectorValues.PredefinedByteVectorBinaryDocValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(binaryDocValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, spaceType);

        assertNotNull(scorer);
        assertSame(binaryDocValues, scorer.iterator());
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    // ======================== Float target with ByteVectorValues (ADC) ========================

    @SneakyThrows
    public void testGetScorer_floatTarget_byteVectorValues_adc() {
        final SpaceType spaceType = SpaceType.L2;
        final float[] target = { 1.0f, 2.0f };
        final List<byte[]> vectors = List.of(new byte[] { 1, 2 }, new byte[] { 3, 4 });
        final var byteVectorValues = new TestVectorValues.PreDefinedByteVectorValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(byteVectorValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, spaceType);

        assertNotNull(scorer);
        assertNotNull(scorer.iterator());
    }

    // ======================== ADC scoring with actual score verification ========================

    @SneakyThrows
    public void testGetScorer_floatTarget_byteVectorValues_adc_verifyScores() {
        final SpaceType spaceType = SpaceType.L2;
        final float[] target = { 1.0f, 2.0f, 3.0f };
        final List<byte[]> vectors = Arrays.asList(new byte[] { 11, 12, 13 }, new byte[] { 14, 15, 16 }, new byte[] { 17, 18, 19 });
        final List<Float> expectedScores = vectors.stream()
            .map(vector -> KNNScoringUtil.scoreWithADC(target, vector, spaceType))
            .collect(Collectors.toList());
        final var byteVectorValues = new TestVectorValues.PreDefinedByteVectorValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(byteVectorValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, spaceType);

        for (int i = 0; i < vectors.size(); i++) {
            scorer.iterator().nextDoc();
            assertEquals(expectedScores.get(i), scorer.score(), 1e-5f);
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc());
    }

    @SneakyThrows
    public void testGetScorer_floatTarget_byteVectorValues_adc_innerProduct() {
        final SpaceType spaceType = SpaceType.INNER_PRODUCT;
        final float[] target = { 1.0f, 2.0f };
        final List<byte[]> vectors = Arrays.asList(new byte[] { 3, 4 }, new byte[] { 5, 6 });
        final List<Float> expectedScores = vectors.stream()
            .map(vector -> KNNScoringUtil.scoreWithADC(target, vector, spaceType))
            .collect(Collectors.toList());
        final var byteVectorValues = new TestVectorValues.PreDefinedByteVectorValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(byteVectorValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, spaceType);

        for (int i = 0; i < vectors.size(); i++) {
            scorer.iterator().nextDoc();
            assertEquals(expectedScores.get(i), scorer.score(), 1e-5f);
        }
    }

    @SneakyThrows
    public void testGetScorer_floatTarget_binaryVectorValues_adc() {
        final SpaceType spaceType = SpaceType.L2;
        final float[] target = { 1.0f, 2.0f, 3.0f };
        final List<byte[]> vectors = Arrays.asList(new byte[] { 10, 20, 30 }, new byte[] { 40, 50, 60 });
        final List<Float> expectedScores = vectors.stream()
            .map(vector -> KNNScoringUtil.scoreWithADC(target, vector, spaceType))
            .collect(Collectors.toList());
        final var binaryVectorValues = new TestVectorValues.PreDefinedBinaryVectorValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(binaryVectorValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, spaceType);

        for (int i = 0; i < vectors.size(); i++) {
            scorer.iterator().nextDoc();
            assertEquals(expectedScores.get(i), scorer.score(), 1e-5f);
        }
    }

    // ======================== BinaryDocValues with ConstantVectorBinaryDocValues ========================

    @SneakyThrows
    public void testGetScorer_floatTarget_constantVectorBinaryDocValues() {
        final SpaceType spaceType = SpaceType.L2;
        final int count = 3;
        final int dimension = 2;
        final float constantValue = 5.0f;
        final float[] target = { 1.0f, 2.0f };
        final float[] expectedDocVector = { constantValue, constantValue };
        final var binaryDocValues = new TestVectorValues.ConstantVectorBinaryDocValues(count, dimension, constantValue);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(binaryDocValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, spaceType);

        final float expectedScore = spaceType.getKnnVectorSimilarityFunction().compare(target, expectedDocVector);
        for (int i = 0; i < count; i++) {
            scorer.iterator().nextDoc();
            assertEquals(expectedScore, scorer.score(), 1e-5f);
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc());
    }

    // ======================== BinaryDocValues with multiple space types ========================

    @SneakyThrows
    public void testGetScorer_floatTarget_binaryDocValues_cosine() {
        final SpaceType spaceType = SpaceType.COSINESIMIL;
        final List<float[]> vectors = List.of(new float[] { 1.0f, 0.0f }, new float[] { 0.0f, 1.0f });
        final float[] target = { 1.0f, 0.0f };
        final var binaryDocValues = new TestVectorValues.PredefinedFloatVectorBinaryDocValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(binaryDocValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, spaceType);

        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    @SneakyThrows
    public void testGetScorer_byteTarget_binaryDocValues_hamming() {
        final SpaceType spaceType = SpaceType.HAMMING;
        final List<byte[]> vectors = Arrays.asList(new byte[] { 1, 2, 3 }, new byte[] { 4, 5, 6 });
        final byte[] target = { 1, 2, 3 };
        final var binaryDocValues = new TestVectorValues.PredefinedByteVectorBinaryDocValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(binaryDocValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, spaceType);

        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    // ======================== KNNFloatVectorValues.scorer via createKNNFloatVectorValues ========================

    @SneakyThrows
    public void testKNNFloatVectorValues_scorer_viaCreateHelper() {
        final SpaceType spaceType = SpaceType.L2;
        final List<float[]> vectors = Arrays.asList(new float[] { 1.0f, 2.0f }, new float[] { 3.0f, 4.0f });
        final float[] target = { 1.0f, 2.0f };
        final KNNFloatVectorValues knnFloatVectorValues = (KNNFloatVectorValues) TestVectorValues.createKNNFloatVectorValues(vectors);

        final VectorScorer scorer = knnFloatVectorValues.scorer(target, spaceType);

        assertNotNull(scorer);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    @SneakyThrows
    public void testKNNFloatVectorValues_rescorer_viaCreateHelper() {
        final SpaceType spaceType = SpaceType.COSINESIMIL;
        final List<float[]> vectors = Arrays.asList(new float[] { 1.0f, 0.0f }, new float[] { 0.0f, 1.0f });
        final float[] target = { 1.0f, 0.0f };
        final KNNFloatVectorValues knnFloatVectorValues = (KNNFloatVectorValues) TestVectorValues.createKNNFloatVectorValues(vectors);

        final VectorScorer scorer = knnFloatVectorValues.rescorer(target, spaceType);

        assertNotNull(scorer);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    // ======================== KNNBinaryVectorValues.scorer via createKNNBinaryVectorValues ========================

    @SneakyThrows
    public void testKNNBinaryVectorValues_byteScorer_viaBinaryDocValuesHelper() {
        final SpaceType spaceType = SpaceType.HAMMING;
        final List<byte[]> vectors = Arrays.asList(new byte[] { 1, 2, 3 }, new byte[] { 4, 5, 6 });
        final byte[] target = { 1, 2, 3 };
        final var binaryDocValues = new TestVectorValues.PredefinedByteVectorBinaryDocValues(vectors);
        final KNNBinaryVectorValues knnBinaryVectorValues = (KNNBinaryVectorValues) TestVectorValues
            .createKNNBinaryVectorValues(binaryDocValues);

        final VectorScorer scorer = knnBinaryVectorValues.scorer(target, spaceType);

        assertNotNull(scorer);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    @SneakyThrows
    public void testKNNBinaryVectorValues_floatScorer_viaCreateHelper() {
        final SpaceType spaceType = SpaceType.L2;
        final List<byte[]> vectors = Arrays.asList(new byte[] { 10, 20 }, new byte[] { 30, 40 });
        final float[] target = { 10.0f, 20.0f };
        final KNNBinaryVectorValues knnBinaryVectorValues = (KNNBinaryVectorValues) TestVectorValues.createKNNBinaryVectorValues(vectors);

        final VectorScorer scorer = knnBinaryVectorValues.scorer(target, spaceType);

        assertNotNull(scorer);
        scorer.iterator().nextDoc();
        assertEquals(KNNScoringUtil.scoreWithADC(target, vectors.get(0), spaceType), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(KNNScoringUtil.scoreWithADC(target, vectors.get(1), spaceType), scorer.score(), 1e-5f);
    }

    @SneakyThrows
    public void testKNNBinaryVectorValues_byteRescorer_viaBinaryDocValuesHelper() {
        final SpaceType spaceType = SpaceType.HAMMING;
        final List<byte[]> vectors = Arrays.asList(new byte[] { 7, 8 }, new byte[] { 9, 10 });
        final byte[] target = { 7, 8 };
        final var binaryDocValues = new TestVectorValues.PredefinedByteVectorBinaryDocValues(vectors);
        final KNNBinaryVectorValues knnBinaryVectorValues = (KNNBinaryVectorValues) TestVectorValues
            .createKNNBinaryVectorValues(binaryDocValues);

        final VectorScorer scorer = knnBinaryVectorValues.rescorer(target, spaceType);

        assertNotNull(scorer);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    @SneakyThrows
    public void testKNNBinaryVectorValues_floatRescorer_viaCreateHelper() {
        final SpaceType spaceType = SpaceType.L2;
        final List<byte[]> vectors = Arrays.asList(new byte[] { 10, 20 }, new byte[] { 30, 40 });
        final float[] target = { 10.0f, 20.0f };
        final KNNBinaryVectorValues knnBinaryVectorValues = (KNNBinaryVectorValues) TestVectorValues.createKNNBinaryVectorValues(vectors);

        final VectorScorer scorer = knnBinaryVectorValues.rescorer(target, spaceType);

        assertNotNull(scorer);
        scorer.iterator().nextDoc();
        assertEquals(KNNScoringUtil.scoreWithADC(target, vectors.get(0), spaceType), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(KNNScoringUtil.scoreWithADC(target, vectors.get(1), spaceType), scorer.score(), 1e-5f);
    }

    @SneakyThrows
    public void testGetScorer_floatTarget_binaryDocValues_rescore() {
        final SpaceType spaceType = SpaceType.L2;
        final List<float[]> vectors = List.of(new float[] { 1.0f, 2.0f }, new float[] { 3.0f, 4.0f });
        final float[] target = { 1.0f, 2.0f };
        final var binaryDocValues = new TestVectorValues.PredefinedFloatVectorBinaryDocValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(binaryDocValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.RESCORE, spaceType);

        assertNotNull(scorer);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    @SneakyThrows
    public void testGetScorer_byteTarget_binaryDocValues_rescore() {
        final SpaceType spaceType = SpaceType.HAMMING;
        final List<byte[]> vectors = Arrays.asList(new byte[] { 1, 2 }, new byte[] { 3, 4 });
        final byte[] target = { 1, 2 };
        final var binaryDocValues = new TestVectorValues.PredefinedByteVectorBinaryDocValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(binaryDocValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.RESCORE, spaceType);

        assertNotNull(scorer);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    @SneakyThrows
    public void testGetScorer_floatTarget_byteVectorValues_adc_rescore() {
        final SpaceType spaceType = SpaceType.COSINESIMIL;
        final float[] target = { 1.0f, 2.0f };
        final List<byte[]> vectors = Arrays.asList(new byte[] { 3, 4 }, new byte[] { 5, 6 });
        final List<Float> expectedScores = vectors.stream()
            .map(vector -> KNNScoringUtil.scoreWithADC(target, vector, spaceType))
            .collect(Collectors.toList());
        final var byteVectorValues = new TestVectorValues.PreDefinedByteVectorValues(vectors);
        final var iterator = new KNNVectorValuesIterator.DocIdsIteratorValues(byteVectorValues);

        final VectorScorer scorer = VectorScorerFactory.getScorer(iterator, target, ScoreMode.RESCORE, spaceType);

        for (int i = 0; i < vectors.size(); i++) {
            scorer.iterator().nextDoc();
            assertEquals(expectedScores.get(i), scorer.score(), 1e-5f);
        }
    }

    @SneakyThrows
    public void testKNNBinaryVectorValues_viaCreateHelper_binaryDocValues() {
        final SpaceType spaceType = SpaceType.HAMMING;
        final List<byte[]> vectors = Arrays.asList(new byte[] { 1, 2 }, new byte[] { 3, 4 });
        final byte[] target = { 1, 2 };
        final var binaryDocValues = new TestVectorValues.PredefinedByteVectorBinaryDocValues(vectors);
        final KNNBinaryVectorValues knnBinaryVectorValues = (KNNBinaryVectorValues) TestVectorValues
            .createKNNBinaryVectorValues(binaryDocValues);

        final VectorScorer scorer = knnBinaryVectorValues.scorer(target, spaceType);

        assertNotNull(scorer);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(0)), scorer.score(), 1e-5f);
        scorer.iterator().nextDoc();
        assertEquals(spaceType.getKnnVectorSimilarityFunction().compare(target, vectors.get(1)), scorer.score(), 1e-5f);
    }

    // ======================== Error cases ========================

    @SneakyThrows
    public void testGetScorer_floatTarget_nullKnnVectorValues_thenException() {
        final float[] target = { 1.0f };
        final var iterator = mock(KNNVectorValuesIterator.DocIdsIteratorValues.class);
        when(iterator.getDocIdSetIterator()).thenReturn(mock(DocIdSetIterator.class));
        when(iterator.getKnnVectorValues()).thenReturn(null);

        expectThrows(
            NullPointerException.class,
            () -> VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, SpaceType.L2)
        );
    }

    @SneakyThrows
    public void testGetScorer_byteTarget_floatVectorValues_thenException() {
        final byte[] target = { 1 };
        final FloatVectorValues floatVectorValues = mock(FloatVectorValues.class);
        final var iterator = mock(KNNVectorValuesIterator.DocIdsIteratorValues.class);
        when(iterator.getDocIdSetIterator()).thenReturn(mock(DocIdSetIterator.class));
        when(iterator.getKnnVectorValues()).thenReturn(floatVectorValues);

        expectThrows(
            IllegalArgumentException.class,
            () -> VectorScorerFactory.getScorer(iterator, target, ScoreMode.SCORE, SpaceType.L2)
        );
    }
}
