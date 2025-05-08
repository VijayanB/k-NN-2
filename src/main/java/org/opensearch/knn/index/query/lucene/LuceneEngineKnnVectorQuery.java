/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.lucene;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.*;
import org.apache.lucene.search.join.BitSetProducer;
import org.opensearch.common.StopWatch;
import org.opensearch.knn.index.KNNSettings;
import org.opensearch.knn.index.VectorDataType;
import org.opensearch.knn.index.query.KNNWeight;
import org.opensearch.knn.index.query.PerLeafResult;
import org.opensearch.knn.index.query.ResultUtil;
import org.opensearch.knn.index.query.common.QueryUtils;
import org.opensearch.knn.index.query.lucenelib.NestedKnnVectorQueryFactory;
import org.opensearch.knn.index.query.rescore.RescoreContext;
import org.opensearch.knn.index.query.rescore.Rescorer;
import org.opensearch.knn.index.query.rescore.VectorSearchRescorer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LuceneEngineKnnVectorQuery is a wrapper around a vector queries for the Lucene engine.
 * This enables us to defer rewrites until weight creation to optimize repeated execution
 * of Lucene based k-NN queries.
 */
@Builder
@Log4j2
public class LuceneEngineKnnVectorQuery extends Query {
    private final String fieldName;
    private final byte[] byteVector;
    private final int k;
    private final Query filterQuery;
    private final BitSetProducer parentFilter;
    private final boolean expandNested;
    private final float[] floatVector;
    private final VectorDataType vectorDataType;
    private Query luceneQuery;
    private RescoreContext rescoreContext;
    private final String indexName;

    /*
      Prevents repeated rewrites of the query for the Lucene engine.
    */
    @Override
    public Query rewrite(IndexSearcher indexSearcher) {
        return this;
    }

    /*
       Rewrites the query just before weight creation.
     */
    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        final IndexReader reader = searcher.getIndexReader();
        List<LeafReaderContext> leafReaderContexts = reader.leaves();
        if (rescoreContext == null || !rescoreContext.isRescoreEnabled()) {
            Query luceneQuery = buildLuceneVectorQuery(k);
            Query rewrittenQuery = luceneQuery.rewrite(searcher);
            return rewrittenQuery.createWeight(searcher, scoreMode, boost);
        } else {
            boolean isShardLevelRescoringDisabled = KNNSettings.isShardLevelRescoringDisabledForDiskBasedVector(indexName);
            int dimension = getDimension();
            int firstPassK = rescoreContext.getFirstPassK(k, isShardLevelRescoringDisabled, dimension);
            Query luceneQuery = buildLuceneVectorQuery(firstPassK);
            Query rewrittenQuery = luceneQuery.rewrite(searcher);
            Weight weight = rewrittenQuery.createWeight(searcher, scoreMode, boost);
            List<Map<Integer, Float>> perLeafResultMaps= QueryUtils.INSTANCE.doSearch(searcher, leafReaderContexts, weight);
            StopWatch stopWatch = new StopWatch().start();
            Rescorer rescorer = VectorSearchRescorer.builder().fieldName(fieldName).build();
            long rescoreTime = stopWatch.stop().totalTime().millis();
            // perLeafResults = doRescore(indexSearcher, leafReaderContexts, knnWeight, perLeafResults, finalK);
            TopDocs rescore = rescorer.rescore(searcher, perLeafResultMaps, floatVector, k);
            log.debug("Rescoring results took {} ms. oversampled k:{}, segments:{}", rescoreTime, firstPassK, leafReaderContexts.size());
            return QueryUtils.INSTANCE.createDocAndScoreQuery(reader, rescore).createWeight(searcher, scoreMode, boost);
        }
    }

    @Override
    public String toString(String s) {
        return luceneQuery.toString();
    }

    @Override
    public void visit(QueryVisitor queryVisitor) {
        queryVisitor.visitLeaf(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LuceneEngineKnnVectorQuery otherQuery = (LuceneEngineKnnVectorQuery) o;
        return luceneQuery.equals(otherQuery.luceneQuery);
    }

    @Override
    public int hashCode() {
        return luceneQuery.hashCode();
    }

    /**
     * If parentFilter is not null, it is a nested query. Therefore, we delegate creation of query to {@link NestedKnnVectorQueryFactory}
     * which will create query to dedupe search result per parent so that we can get k parent results at the end.
     */
    private Query buildLuceneVectorQuery(int k) {
        if (vectorDataType == VectorDataType.FLOAT) {
            if (parentFilter == null) {
                assert expandNested == false : "expandNested is allowed to be true only for nested fields.";
                return new KnnFloatVectorQuery(fieldName, floatVector, k, filterQuery);
            }return NestedKnnVectorQueryFactory.createNestedKnnVectorQuery(
                    fieldName,
                    floatVector,
                    k,
                    filterQuery,
                    parentFilter,
                    expandNested
            );
        }
        if (parentFilter == null) {
            assert expandNested == false : "expandNested is allowed to be true only for nested fields.";
            return new KnnByteVectorQuery(fieldName, byteVector, k, filterQuery);
        }            return NestedKnnVectorQueryFactory.createNestedKnnVectorQuery(
                fieldName,
                byteVector,
                k,
                filterQuery,
                parentFilter,
                expandNested
        );

    }

    private int getDimension() {
        if (vectorDataType == VectorDataType.BINARY) {
            return byteVector.length;
        }
        return floatVector.length;
    }
}
