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

import org.apache.http.util.EntityUtils;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.common.Strings;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.knn.KNNRestTestCase;
import org.opensearch.knn.common.KNNConstants;
import org.opensearch.knn.index.SpaceType;
import org.opensearch.knn.index.util.KNNEngine;
import org.opensearch.knn.indices.ModelMetadata;
import org.opensearch.knn.indices.ModelState;
import org.opensearch.knn.plugin.KNNPlugin;
import org.opensearch.rest.RestStatus;

import java.io.IOException;
import java.util.Map;

import static org.opensearch.knn.common.KNNConstants.DIMENSION;
import static org.opensearch.knn.common.KNNConstants.KNN_ENGINE;
import static org.opensearch.knn.common.KNNConstants.METHOD_PARAMETER_SPACE_TYPE;
import static org.opensearch.knn.common.KNNConstants.MODELS;
import static org.opensearch.knn.common.KNNConstants.MODEL_DESCRIPTION;
import static org.opensearch.knn.common.KNNConstants.MODEL_ERROR;
import static org.opensearch.knn.common.KNNConstants.MODEL_ID;
import static org.opensearch.knn.common.KNNConstants.MODEL_INDEX_NAME;
import static org.opensearch.knn.common.KNNConstants.MODEL_STATE;
import static org.opensearch.knn.common.KNNConstants.MODEL_TIMESTAMP;

/**
 * Integration tests to check the correctness of {@link org.opensearch.knn.plugin.rest.RestDeleteModelHandler}
 */

public class RestDeleteModelHandlerIT extends KNNRestTestCase {

    private ModelMetadata getModelMetadata() {
        return new ModelMetadata(KNNEngine.DEFAULT, SpaceType.DEFAULT, 4, ModelState.CREATED,
            "2021-03-27", "test model", "");
    }

    public void testDeleteModelExists() throws IOException {

        // Create an index
        XContentBuilder builder = XContentFactory.jsonBuilder()
            .startObject()
            .startObject("properties")
            .startObject("dsd")
            .field("type", "knn_vector")
            .field("dimension", 1)
            .endObject()
            .endObject()
            .endObject()
            .endObject();
        String mapping = Strings.toString(builder);
        createKnnIndex("indexName", mapping);
        deleteKnnDoc("indexName", "sffsd");
        createModelSystemIndex();
        String testModelID = "test-model-id";
        byte[] testModelBlob = "hello".getBytes();
        ModelMetadata testModelMetadata = getModelMetadata();

        addModelToSystemIndex(testModelID, testModelMetadata, testModelBlob);
        assertEquals(getDocCount(MODEL_INDEX_NAME),1);

        String restURI = String.join("/", KNNPlugin.KNN_BASE_URI, MODELS, testModelID);
        Request request = new Request("DELETE", restURI);

        Response response = client().performRequest(request);
        assertEquals(request.getEndpoint() + ": failed", RestStatus.OK,
            RestStatus.fromCode(response.getStatusLine().getStatusCode()));

        assertEquals(getDocCount(MODEL_INDEX_NAME),0);
    }

    public void testDeleteModelFailsInvalid() throws IOException {
        createModelSystemIndex();
        String restURI = String.join("/", KNNPlugin.KNN_BASE_URI, MODELS, "invalid-model-id123");
        Request request = new Request("DELETE", restURI);

        Response response = client().performRequest(request);
        assertEquals("SDfdsfs", response.toString());
    }
}
