/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.knn.plugin.action;

import joptsimple.internal.Strings;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.rules.DisableOnDebug;
import org.opensearch.action.ActionListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.knn.KNNRestTestCase;
import org.opensearch.knn.common.KNNConstants;
import org.opensearch.knn.index.KNNQueryBuilder;
import org.opensearch.knn.index.SpaceType;
import org.opensearch.knn.index.util.KNNEngine;
import org.opensearch.knn.indices.Model;
import org.opensearch.knn.indices.ModelDao;
import org.opensearch.knn.indices.ModelMetadata;
import org.opensearch.knn.indices.ModelState;
import org.opensearch.knn.plugin.KNNPlugin;
import org.opensearch.knn.plugin.stats.KNNStats;
import org.opensearch.knn.plugin.stats.StatNames;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestStatus;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.opensearch.knn.common.KNNConstants.DIMENSION;
import static org.opensearch.knn.common.KNNConstants.MODEL_DESCRIPTION;
import static org.opensearch.knn.common.KNNConstants.MODEL_ID;
import static org.opensearch.knn.common.KNNConstants.MODEL_INDEX_NAME;
import static org.opensearch.knn.plugin.stats.KNNStatsConfig.KNN_STATS;

/**
 * Integration tests to check the correctness of {@link org.opensearch.knn.plugin.rest.RestGetModelHandler}
 */
public class RestGetModelHandlerIT extends KNNRestTestCase {

    private static final Logger logger = LogManager.getLogger(RestGetModelHandlerIT.class);
    private boolean isDebuggingTest = new DisableOnDebug(null).isDebugging();
    private boolean isDebuggingRemoteCluster = System.getProperty("cluster.debug", "false").equals("true");

    private final static String TEST_MODEL_ID = "test-model-id";


    private ModelMetadata getModelMetadata() {
        return new ModelMetadata(KNNEngine.DEFAULT, SpaceType.DEFAULT, 4, ModelState.CREATED,
            TimeValue.timeValueDays(10), "test model", "");
    }

//    @Override
//    public void setUp() throws Exception {
//
//    }

    @Override
    public void tearDown() throws Exception {
//        deleteKnnDoc(MODEL_INDEX_NAME,TEST_MODEL_ID);
    }


    public void testGetModel() throws IOException, InterruptedException, ExecutionException {
        addModelToSystemIndex(TEST_MODEL_ID, getModelMetadata(), "hello".getBytes());
        Response response = executeGetModelRequest(TEST_MODEL_ID);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));

        Map<String, Object> modelResponse = parseGetModelResponse(response);
        assertEquals(TEST_MODEL_ID, modelResponse.get(MODEL_ID));

        final ModelMetadata modelMetadata = getModelMetadata();
        assertEquals(modelMetadata.getDimension(), modelResponse.get(DIMENSION));
        assertEquals(modelMetadata.getDescription(), modelResponse.get(MODEL_DESCRIPTION));
    }


    // Useful settings when debugging to prevent timeouts
    @Override
    protected Settings restClientSettings() {
        if (isDebuggingTest || isDebuggingRemoteCluster) {
            return Settings.builder()
                .put(CLIENT_SOCKET_TIMEOUT, TimeValue.timeValueMinutes(10))
                .build();
        } else {
            return super.restClientSettings();
        }
    }
}
