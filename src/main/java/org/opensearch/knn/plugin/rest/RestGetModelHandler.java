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

package org.opensearch.knn.plugin.rest;

import com.google.common.collect.ImmutableList;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.knn.indices.Model;
import org.opensearch.knn.indices.ModelDao;
import org.opensearch.knn.plugin.KNNPlugin;
import org.opensearch.knn.plugin.transport.GetModelResponse;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestStatus;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static org.opensearch.knn.common.KNNConstants.MODELS;
import static org.opensearch.knn.common.KNNConstants.MODEL_ID;

public class RestGetModelHandler extends BaseRestHandler {

    private final static String NAME = "knn_model_action";
    private final ModelDao modelDao;


    public RestGetModelHandler(ModelDao modelDao) {
        this.modelDao = modelDao;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.GET,
                    String.format(Locale.ROOT, "%s/%s/{%s}", KNNPlugin.KNN_BASE_URI, MODELS, MODEL_ID)
                )
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient nodeClient) throws IOException {
        String modelID = restRequest.param(MODEL_ID);
        if (modelID == null || modelID.trim().length() == 0) {
            throw new IllegalArgumentException("model ID cannot be empty");
        }
        Model model = null;
        try {
            model = modelDao.get(modelID);
          } catch (ExecutionException | InterruptedException e) {
            throw new IOException(e);
        }
        Objects.requireNonNull(model, "No model found for given model ID");
        Model finalModel = model;
        return restChannel -> {
            GetModelResponse getModelResponse = new GetModelResponse(finalModel, modelID);
            XContentBuilder builder = restChannel.newBuilder();
            builder.startObject();
            getModelResponse.toXContent(builder, restRequest);
            builder.endObject();
            restChannel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }
}
