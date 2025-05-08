/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.exactsearch;

import org.apache.lucene.search.HitQueue;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopKnnCollector;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TopKnnExactSearchCollector extends TopKnnCollector {

    public TopKnnExactSearchCollector(int limit) {
        super(limit, Integer.MAX_VALUE);
    }
}
