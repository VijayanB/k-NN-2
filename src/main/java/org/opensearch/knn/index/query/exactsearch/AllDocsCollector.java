/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import org.apache.lucene.search.KnnCollector;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;

import java.util.*;
import java.util.stream.Collectors;

public class AllDocsCollector implements KnnCollector {

    private final List<ScoreDoc> scoreDocList;

    public AllDocsCollector() {
        this.scoreDocList = new ArrayList<>();
    }

    @Override
    public boolean earlyTerminated() {
        return false;
    }

    @Override
    public void incVisitedCount(int count) {

    }

    @Override
    public long visitedCount() {
        return this.scoreDocList.size();
    }

    @Override
    public long visitLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int k() {
        return 1;
    }

    @Override
    public boolean collect(int docId, float score) {
        this.scoreDocList.add(new ScoreDoc(docId, score));
        return true;
    }

    @Override
    public float minCompetitiveSimilarity() {
        return 0;
    }

    @Override
    public TopDocs topDocs() {
        // Results are not returned in a sorted order to prevent unnecessary calculations (because we do
        // not need to maintain the topK)
        TotalHits.Relation relation =
                earlyTerminated()
                        ? TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
                        : TotalHits.Relation.EQUAL_TO;
        return new TopDocs(
                new TotalHits(visitedCount(), relation), scoreDocList.toArray(ScoreDoc[]::new));
    }
}
