/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query;

import lombok.AllArgsConstructor;
import org.apache.lucene.search.*;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.opensearch.knn.index.query.common.QueryUtils;

import java.io.IOException;


@AllArgsConstructor
public class RescoreKnnVectorQuery extends Query {

    private final Query innerQuery;

    private final int k;



    @Override
    public String toString(String field) {
        return "";
    }

    @Override
    public void visit(QueryVisitor visitor) {

    }

    @Override
    public boolean equals(Object obj) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        Query rewrittenQuery = innerQuery.rewrite(searcher);
        TopDocs topDocs = searcher.search(rewrittenQuery, k);

        // calculate rescore

        // convert to DocAndScoreQuery
        return QueryUtils.INSTANCE.createDocAndScoreQuery(searcher.getIndexReader(), topDocs).createWeight(searcher, scoreMode, boost);
    }
}
