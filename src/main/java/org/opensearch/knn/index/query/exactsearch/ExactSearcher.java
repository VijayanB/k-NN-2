/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import lombok.Builder;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.KnnCollector;
import org.apache.lucene.search.join.BitSetProducer;
import org.opensearch.knn.index.VectorDataType;
import org.opensearch.knn.index.query.iterators.KNNIterator;
import org.opensearch.knn.index.query.iterators.KNNIteratorFactory;

import java.io.IOException;

public class ExactSearcher {

    public static void searchLeaf(
        final LeafReaderContext leafReaderContext,
        final ExactSearchContext context,
        final KnnCollector collector
    ) throws IOException {

        final KNNIteratorFactory factory = KNNIteratorFactory.builder()
            .filterIterator(context.matchedDocs)
            .fieldName(context.fieldName)
            .parentsFilter(context.parentsFilter)
            .isParentHits(context.isParentHits)
            .build();
        KNNIterator iterator = getKnnIterator(leafReaderContext, context, factory);
        search(iterator, collector);
    }

    private static KNNIterator getKnnIterator(LeafReaderContext leafReaderContext, ExactSearchContext context, KNNIteratorFactory factory)
        throws IOException {
        if (VectorDataType.FLOAT == context.dataType && context.targetVector instanceof float[]) {
            return factory.buildFloatKNNIterator(leafReaderContext, (float[]) context.targetVector);
        }
        if (VectorDataType.BINARY == context.dataType && context.targetVector instanceof byte[]) {
            return factory.buildBinaryKNNIterator(leafReaderContext, (byte[]) context.targetVector);
        }
        if (VectorDataType.BYTE == context.dataType && context.targetVector instanceof float[]) {
            return factory.buildByteKNNIterator(leafReaderContext, (float[]) context.targetVector);
        }
        return null;
    }

    private static void search(KNNIterator iterator, KnnCollector collector) throws IOException {
        if (iterator == null) return;
        int docId;
        while ((docId = iterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            collector.collect(docId, iterator.score());
        }
    }

    @Builder
    public static class ExactSearchContext {
        private final DocIdSetIterator matchedDocs;
        private final String fieldName;
        private final BitSetProducer parentsFilter;
        private final boolean isParentHits;
        private final Object targetVector;
        private final VectorDataType dataType;
    }
}
