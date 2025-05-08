/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.rescore;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.opensearch.knn.index.query.PerLeafResult;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface Rescorer {

    TopDocs rescore(final IndexSearcher indexSearcher, final List<Map<Integer,Float>> results, Object target, int topN) throws IOException;

}
