/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.rescore;

import lombok.Builder;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.*;
import org.apache.lucene.search.join.BitSetProducer;
import org.opensearch.knn.index.VectorDataType;
import org.opensearch.knn.index.query.PerLeafResult;
import org.opensearch.knn.index.query.ResultUtil;
import org.opensearch.knn.index.query.exactsearch.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Builder
public class VectorSearchRescorer implements Rescorer {

    private final Object target;
    private final String fieldName;
    private final BitSetProducer parentsFilter;
    private final boolean isParentHits;
    private final VectorDataType dataType;
    private final int maxResults;
    private final Float radius;

    @Override
    public TopDocs rescore(final IndexSearcher indexSearcher, final List<Map<Integer,Float>> perLeafResults, Object target, int topN)
        throws IOException {
        if (perLeafResults == null || perLeafResults.isEmpty()) {
            return TopDocsCollector.EMPTY_TOPDOCS;
        }
        final List<LeafReaderContext> leafReaderContexts = indexSearcher.getIndexReader().leaves();
        final List<Callable<TopDocs>> rescoreTasks = new ArrayList<>(leafReaderContexts.size());
        for (int i = 0; i < perLeafResults.size(); i++) {
            final LeafReaderContext leafReaderContext = leafReaderContexts.get(i);
            final Map<Integer,Float> perLeafResult = perLeafResults.get(i);
            rescoreTasks.add(() -> {
                if (perLeafResult.isEmpty()) {
                    return TopDocsCollector.EMPTY_TOPDOCS;
                }
                final DocIdSetIterator matchedDocs = ResultUtil.resultMapToDocIds(perLeafResult);
                final KnnCollector collector = createCollector(matchedDocs);
                final ExactSearcher.ExactSearchContext exactSearchContext = ExactSearcher.ExactSearchContext.builder()
                    .matchedDocs(matchedDocs)
                    .fieldName(fieldName)
                    .parentsFilter(parentsFilter)
                    .isParentHits(isParentHits)
                    .targetVector(target)
                    .dataType(dataType)
                    .build();
                ExactSearcher.searchLeaf(leafReaderContext, exactSearchContext, collector);
                return collector.topDocs();
            });
        }
        List<TopDocs> topDocs = indexSearcher.getTaskExecutor().invokeAll(rescoreTasks);
        return TopDocs.merge(topN, topDocs.toArray(new TopDocs[0]));
    }

    private KnnCollector createCollector(DocIdSetIterator matchedDocs) {
        if (radius != null) {
            return new RadialSearchCollector(maxResults, radius);
        } else if (matchedDocs != null && matchedDocs.cost() <= maxResults) {
            return new AllDocsCollector();
        }
        return new TopKnnCollector(maxResults, Integer.MAX_VALUE);
    }
}
