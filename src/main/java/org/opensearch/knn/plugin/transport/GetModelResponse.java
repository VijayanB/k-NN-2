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

package org.opensearch.knn.plugin.transport;

import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.knn.common.KNNConstants;
import org.opensearch.knn.indices.Model;

import java.io.IOException;

public class GetModelResponse implements ToXContent {

    private final Model model;
    private final String modelID;

    public GetModelResponse(final Model model, final String modelID){
        this.model = model;
        this.modelID = modelID;
    }
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(KNNConstants.DIMENSION, model.getModelMetadata().getDimension());
        builder.field(KNNConstants.KNN_ENGINE, model.getModelMetadata().getKnnEngine());
        builder.field(KNNConstants.METHOD_PARAMETER_SPACE_TYPE, model.getModelMetadata().getSpaceType());
        builder.field(KNNConstants.MODEL_BLOB_PARAMETER, model.getModelBlob());
        builder.field(KNNConstants.MODEL_DESCRIPTION, model.getModelMetadata().getDescription());
        builder.field(KNNConstants.MODEL_ERROR, model.getModelMetadata().getError());
        builder.field(KNNConstants.MODEL_STATE, model.getModelMetadata().getState().getName());
        builder.field(KNNConstants.MODEL_TIMESTAMP, model.getModelMetadata().getTimestamp());
        builder.field(KNNConstants.MODEL_ID, modelID);
        return builder;
    }
}
