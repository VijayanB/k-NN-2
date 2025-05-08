/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import org.apache.lucene.search.AbstractKnnCollector;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;

import java.util.ArrayList;
import java.util.List;

public final class RadialSearchCollector extends AbstractKnnCollector {

    private final float minScore;
    private final List<ScoreDoc> scoreDocList;

    public RadialSearchCollector(int limit, float minScore) {
        super(1, limit);
        this.minScore = minScore;
        this.scoreDocList = new ArrayList<>();
    }

    @Override
    public boolean collect(int docId, float score) {
        if (score >= minScore) {
            scoreDocList.add(new ScoreDoc(docId, score));
        }
        return true;
    }

    @Override
    public int numCollected() {
        return 0;
    }

    @Override
    public float minCompetitiveSimilarity() {
        return scoreDocList.size();
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
