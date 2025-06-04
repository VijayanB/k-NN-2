/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query;

import lombok.SneakyThrows;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.opensearch.knn.indices.ModelDao;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.apache.lucene.tests.index.BaseKnnVectorsFormatTestCase.*;
import static org.mockito.Mockito.*;

public class RescoreKnnVectorQueryTests extends OpenSearchTestCase {

    public static final String FIELD_NAME = "vector-field";

    private List<float[]> generateRandomInput(int count, int dimension) {
        List<float[]> vectors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            vectors.add(randomVector(dimension));
        }
        return vectors;
    }

    private static void addDocuments(List<float[]> vectors, Directory directory) throws IOException {
        try (IndexWriter w = new IndexWriter(directory, newIndexWriterConfig())) {
            for (float[] vector : vectors) {
                Document document = new Document();
                KnnFloatVectorField vectorField = new KnnFloatVectorField(FIELD_NAME, vector, VectorSimilarityFunction.EUCLIDEAN);
                document.add(vectorField);
                w.addDocument(document);
                w.commit();
            }
        }
    }

    private List<Float> calculateTopScores(List<float[]> vectors, float[] queryVector, int k) {
        List<Float> scores = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            scores.add(VectorSimilarityFunction.EUCLIDEAN.compare(queryVector, vectors.get(i)));
        }
        scores.sort(Collections.reverseOrder());
        return scores;
    }

    @SneakyThrows
    public void testRescoreQuery() throws IOException {
        int docsCount = 15;
        int dimension = 4;
        int finalK = 5;
        try (Directory directory = newDirectory()) {
            List<float[]> vectors = generateRandomInput(docsCount, dimension);
            addDocuments(vectors, directory);
            try (IndexReader reader = DirectoryReader.open(directory)) {
                float[] queryVector = randomVector(dimension);
                Query innerQuery = new MatchAllDocsQuery();
                RescoreKNNVectorQuery rescoreKnnVectorQuery = new RescoreKNNVectorQuery(innerQuery, FIELD_NAME, finalK, queryVector, 1);
                IndexSearcher searcher = newSearcher(reader, true, false);
                try (MockedStatic<ModelDao.OpenSearchKNNModelDao> mocked = Mockito.mockStatic(ModelDao.OpenSearchKNNModelDao.class)) {
                    mocked.when(ModelDao.OpenSearchKNNModelDao::getInstance).thenReturn(mock(ModelDao.OpenSearchKNNModelDao.class));
                    TopDocs rescoredDocs = searcher.search(rescoreKnnVectorQuery, finalK);
                    assertEquals(finalK, rescoredDocs.scoreDocs.length);
                    List<Float> actualScores = new ArrayList<>();
                    for (int i = 0; i < rescoredDocs.scoreDocs.length; i++) {
                        actualScores.add(rescoredDocs.scoreDocs[i].score);
                    }
                    assertEquals(calculateTopScores(vectors, queryVector, finalK), actualScores);
                }
            }
        }
    }
}
