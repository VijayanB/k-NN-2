/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.search.DocAndFloatFeatureBuffer;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.HitQueue;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.search.join.BitSetProducer;
import org.apache.lucene.util.BitSet;
import org.opensearch.common.Nullable;
import org.opensearch.common.lucene.Lucene;
import org.opensearch.knn.common.FieldInfoExtractor;
import org.opensearch.knn.index.SpaceType;
import org.opensearch.knn.index.VectorDataType;
import org.opensearch.knn.index.engine.KNNEngine;
import org.opensearch.knn.index.query.SegmentLevelQuantizationInfo;
import org.opensearch.knn.index.query.SegmentLevelQuantizationUtil;
import org.opensearch.knn.index.vectorvalues.KNNVectorValues;
import org.opensearch.knn.index.vectorvalues.KNNVectorValuesFactory;
import org.opensearch.knn.index.vectorvalues.KNNVectorValuesIterator;
import org.opensearch.knn.index.vectorvalues.ScoreMode;
import org.opensearch.knn.index.vectorvalues.VectorScorers;
import org.opensearch.knn.index.query.exactsearch.NestedBestChildVectorScorer;
import org.opensearch.knn.indices.ModelDao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Performs exact KNN search using {@link VectorScorer} with bulk scoring, similar to
 * {@code AbstractKnnVectorQuery#exactSearch} in Lucene.
 */
@Log4j2
@AllArgsConstructor
public class ExactKNNScorer {

    private final ModelDao modelDao;

    /**
     * Execute an exact search on a subset of documents of a leaf.
     *
     * @param leafReaderContext {@link LeafReaderContext}
     * @param context {@link ExactScorerContext}
     * @return TopDocs containing the results of the search
     * @throws IOException if an error occurs during search
     */
    public TopDocs searchLeaf(final LeafReaderContext leafReaderContext, final ExactScorerContext context) throws IOException {
        final VectorScorer vectorScorer = createVectorScorer(leafReaderContext, context);
        if (vectorScorer == null) {
            return TopDocsCollector.EMPTY_TOPDOCS;
        }
        // When nested, matchedDocsIterator is already consumed inside NestedBestChildVectorScorer,
        // so pass null to avoid double consumption of the same iterator.
        final boolean isNested = context.getParentsFilter() != null;
        final DocIdSetIterator matchedDocs = isNested ? null : context.getMatchedDocsIterator();
        if (context.getRadius() != null) {
            return doRadialSearch(leafReaderContext, context, vectorScorer, matchedDocs);
        }
        if (matchedDocs != null && context.getNumberOfMatchedDocs() <= context.getK()) {
            return scoreAllDocs(vectorScorer, matchedDocs);
        }
        return searchTopK(vectorScorer, matchedDocs, context.getK());
    }

    private TopDocs doRadialSearch(
        final LeafReaderContext leafReaderContext,
        final ExactScorerContext context,
        final VectorScorer vectorScorer,
        final DocIdSetIterator matchedDocs
    ) throws IOException {
        assert (context.getIsMemoryOptimizedSearchEnabled() != null);

        final SegmentReader reader = Lucene.segmentReader(leafReaderContext.reader());
        final FieldInfo fieldInfo = FieldInfoExtractor.getFieldInfo(reader, context.getField());
        if (fieldInfo == null) {
            return TopDocsCollector.EMPTY_TOPDOCS;
        }
        final KNNEngine engine = FieldInfoExtractor.extractKNNEngine(fieldInfo);
        if (KNNEngine.FAISS != engine) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Engine [%s] does not support radial search", engine));
        }
        final SpaceType spaceType = FieldInfoExtractor.getSpaceType(modelDao, fieldInfo);
        final float minScore = context.getIsMemoryOptimizedSearchEnabled()
            ? context.getRadius()
            : engine.score(context.getRadius(), spaceType);

        return searchWithMinScore(vectorScorer, matchedDocs, context.getMaxResultWindow(), minScore);
    }

    private TopDocs scoreAllDocs(final VectorScorer vectorScorer, final DocIdSetIterator matchedDocs) throws IOException {
        final VectorScorer.Bulk bulkScorer = vectorScorer.bulk(matchedDocs);
        final DocAndFloatFeatureBuffer buffer = new DocAndFloatFeatureBuffer();
        final List<ScoreDoc> scoreDocList = new ArrayList<>();

        while (true) {
            bulkScorer.nextDocsAndScores(DocIdSetIterator.NO_MORE_DOCS, null, buffer);
            if (buffer.size == 0) {
                break;
            }
            for (int i = 0; i < buffer.size; i++) {
                scoreDocList.add(new ScoreDoc(buffer.docs[i], buffer.features[i]));
            }
        }

        scoreDocList.sort(Comparator.comparing(scoreDoc -> scoreDoc.score, Comparator.reverseOrder()));
        return new TopDocs(new TotalHits(scoreDocList.size(), TotalHits.Relation.EQUAL_TO), scoreDocList.toArray(ScoreDoc[]::new));
    }

    private TopDocs searchTopK(final VectorScorer vectorScorer, final DocIdSetIterator matchedDocs, final int k) throws IOException {
        final VectorScorer.Bulk bulkScorer = vectorScorer.bulk(matchedDocs);
        final DocAndFloatFeatureBuffer buffer = new DocAndFloatFeatureBuffer();
        final HitQueue queue = new HitQueue(k, true);
        ScoreDoc topDoc = queue.top();

        for (float maxScore = bulkScorer.nextDocsAndScores(DocIdSetIterator.NO_MORE_DOCS, null, buffer);
            buffer.size > 0;
            maxScore = bulkScorer.nextDocsAndScores(DocIdSetIterator.NO_MORE_DOCS, null, buffer)) {
            if (maxScore < topDoc.score) {
                continue;
            }
            for (int i = 0; i < buffer.size; i++) {
                if (buffer.features[i] > topDoc.score) {
                    topDoc.score = buffer.features[i];
                    topDoc.doc = buffer.docs[i];
                    topDoc = queue.updateTop();
                }
            }
        }

        while (queue.size() > 0 && queue.top().score < 0) {
            queue.pop();
        }

        final ScoreDoc[] topScoreDocs = new ScoreDoc[queue.size()];
        for (int i = topScoreDocs.length - 1; i >= 0; i--) {
            topScoreDocs[i] = queue.pop();
        }
        return new TopDocs(new TotalHits(topScoreDocs.length, TotalHits.Relation.EQUAL_TO), topScoreDocs);
    }

    private TopDocs searchWithMinScore(
        final VectorScorer vectorScorer,
        final DocIdSetIterator matchedDocs,
        final int maxResultWindow,
        final float minScore
    ) throws IOException {
        final VectorScorer.Bulk bulkScorer = vectorScorer.bulk(matchedDocs);
        final DocAndFloatFeatureBuffer buffer = new DocAndFloatFeatureBuffer();
        final HitQueue queue = new HitQueue(maxResultWindow, true);
        ScoreDoc topDoc = queue.top();

        for (float maxBatchScore = bulkScorer.nextDocsAndScores(DocIdSetIterator.NO_MORE_DOCS, null, buffer);
            buffer.size > 0;
            maxBatchScore = bulkScorer.nextDocsAndScores(DocIdSetIterator.NO_MORE_DOCS, null, buffer)) {
            if (maxBatchScore < minScore) {
                continue;
            }
            for (int i = 0; i < buffer.size; i++) {
                final float score = buffer.features[i];
                if (score >= minScore && score > topDoc.score) {
                    topDoc.score = score;
                    topDoc.doc = buffer.docs[i];
                    topDoc = queue.updateTop();
                }
            }
        }

        while (queue.size() > 0 && queue.top().score < 0) {
            queue.pop();
        }

        final ScoreDoc[] topScoreDocs = new ScoreDoc[queue.size()];
        for (int i = topScoreDocs.length - 1; i >= 0; i--) {
            topScoreDocs[i] = queue.pop();
        }
        return new TopDocs(new TotalHits(topScoreDocs.length, TotalHits.Relation.EQUAL_TO), topScoreDocs);
    }

    private VectorScorer createVectorScorer(final LeafReaderContext leafReaderContext, final ExactScorerContext context) throws IOException {
        final SegmentReader reader = Lucene.segmentReader(leafReaderContext.reader());
        final FieldInfo fieldInfo = FieldInfoExtractor.getFieldInfo(reader, context.getField());
        if (fieldInfo == null) {
            log.debug("[KNN] Cannot create VectorScorer as FieldInfo not found for {}:{}", context.getField(), reader.getSegmentName());
            return null;
        }

        final VectorDataType vectorDataType = FieldInfoExtractor.extractVectorDataType(fieldInfo);
        final SpaceType spaceType = FieldInfoExtractor.getSpaceType(modelDao, fieldInfo);
        final ScoreMode scoreMode = context.isUseQuantizedVectorsForSearch() ? ScoreMode.SCORE : ScoreMode.RESCORE;
        final boolean isNestedRequired = context.getParentsFilter() != null;
        final DocIdSetIterator acceptedChildrenIterator = isNestedRequired ? context.getMatchedDocsIterator() : null;
        final BitSet parentBitSet = isNestedRequired ? context.getParentsFilter().getBitSet(leafReaderContext) : null;

        final KNNVectorValues<?> vectorValues = KNNVectorValuesFactory.getVectorValues(fieldInfo, reader);
        final KNNVectorValuesIterator.DocIdsIteratorValues iteratorValues =
            (KNNVectorValuesIterator.DocIdsIteratorValues) vectorValues.getVectorValuesIterator();

        VectorScorer baseScorer;
        if (VectorDataType.BINARY == vectorDataType) {
            baseScorer = VectorScorers.createScorer(
                iteratorValues, context.getByteQueryVector(), scoreMode, spaceType, null, null
            );
        } else if (VectorDataType.BYTE == vectorDataType) {
            final float[] floatQueryVector = context.getFloatQueryVector();
            final byte[] byteQueryVector = new byte[floatQueryVector.length];
            for (int i = 0; i < byteQueryVector.length; i++) {
                byteQueryVector[i] = (byte) floatQueryVector[i];
            }
            baseScorer = VectorScorers.createScorer(
                iteratorValues, byteQueryVector, scoreMode, spaceType, null, null
            );
        } else {
            // Float vector path
            final SegmentLevelQuantizationInfo quantizationInfo = SegmentLevelQuantizationInfo.build(reader, fieldInfo, context.getField());

            if (quantizationInfo == null || !context.isUseQuantizedVectorsForSearch()) {
                baseScorer = VectorScorers.createScorer(
                    iteratorValues, context.getFloatQueryVector(), scoreMode, spaceType, null, null
                );
            } else {
                // Quantized path — need byte vector values
                final KNNVectorValues<?> quantizedValues = KNNVectorValuesFactory.getVectorValues(fieldInfo, reader, true);
                final KNNVectorValuesIterator.DocIdsIteratorValues quantizedIteratorValues =
                    (KNNVectorValuesIterator.DocIdsIteratorValues) quantizedValues.getVectorValuesIterator();

                if (SegmentLevelQuantizationUtil.isAdcEnabled(quantizationInfo)) {
                    SegmentLevelQuantizationUtil.transformVectorWithADC(context.getFloatQueryVector(), quantizationInfo, spaceType);
                    baseScorer = VectorScorers.createScorer(
                        quantizedIteratorValues, context.getFloatQueryVector(), scoreMode, spaceType, null, null
                    );
                } else {
                    final byte[] quantizedQueryVector = SegmentLevelQuantizationUtil.quantizeVector(context.getFloatQueryVector(), quantizationInfo);
                    baseScorer = VectorScorers.createScorer(
                        quantizedIteratorValues, quantizedQueryVector, scoreMode, SpaceType.HAMMING, null, null
                    );
                }
            }
        }

        // Wrap with nested scorer if needed
        if (isNestedRequired) {
            return new NestedBestChildVectorScorer(acceptedChildrenIterator, parentBitSet, baseScorer);
        }

        return baseScorer;
    }

    @Value
    @Builder
    public static class ExactScorerContext {
        boolean useQuantizedVectorsForSearch;
        int k;
        Float radius;
        @Nullable
        DocIdSetIterator matchedDocsIterator;
        long numberOfMatchedDocs;
        BitSetProducer parentsFilter;
        float[] floatQueryVector;
        byte[] byteQueryVector;
        String field;
        Integer maxResultWindow;
        Boolean isMemoryOptimizedSearchEnabled;
    }
}
