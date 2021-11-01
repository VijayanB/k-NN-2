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
/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package org.opensearch.knn.plugin.transport;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.Nullable;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * KNNStatsResponse consists of the aggregated responses from the nodes
 */
public class KNNStatsResponse extends BaseNodesResponse<KNNStatsNodeResponse> implements ToXContentObject {

    private static final String NODES_KEY = "nodes";
    private static final String MODEL_KEY = "model";
    private static final String INDEX_STATUS_KEY = "index_status";
    private Map<String, Object> clusterStats;
    private ClusterHealthStatus modelIndexHealth;

    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException thrown when unable to read from stream
     */
    public KNNStatsResponse(StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(KNNStatsNodeResponse::readStats), in.readList(FailedNodeException::new));
        clusterStats = in.readMap();
        if(in.readOptionalBoolean()){
            modelIndexHealth = ClusterHealthStatus.readFrom(in);
        }
    }

    /**
     * Constructor
     *
     * @param clusterName name of cluster
     * @param nodes List of KNNStatsNodeResponses
     * @param failures List of failures from nodes
     * @param clusterStats Cluster level stats only obtained from a single node
     * @param modelIndexHealth Model Index's cluster health status if index exists else null
     */
    public KNNStatsResponse(ClusterName clusterName, List<KNNStatsNodeResponse> nodes, List<FailedNodeException> failures,
                     Map<String, Object> clusterStats, @Nullable ClusterHealthStatus modelIndexHealth) {
        super(clusterName, nodes, failures);
        this.clusterStats = clusterStats;
        this.modelIndexHealth = modelIndexHealth;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeMap(clusterStats);
        if(modelIndexHealth == null){
            out.writeBoolean(false);
        }
        out.writeBoolean(true);
        modelIndexHealth.writeTo(out);
    }

    @Override
    public void writeNodesTo(StreamOutput out, List<KNNStatsNodeResponse> nodes) throws IOException {
        out.writeList(nodes);
    }

    @Override
    public List<KNNStatsNodeResponse> readNodesFrom(StreamInput in) throws IOException {
        return in.readList(KNNStatsNodeResponse::readStats);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        // Return cluster level stats
        for (Map.Entry<String, Object> clusterStat : clusterStats.entrySet()) {
            builder.field(clusterStat.getKey(), clusterStat.getValue());
        }

        // Add model only if model index is created
        if(modelIndexHealth != null){
            builder.startObject(MODEL_KEY);
            builder.field(INDEX_STATUS_KEY, modelIndexHealth.name().toLowerCase());
            builder.endObject();
        }

        // Return node level stats
        String nodeId;
        DiscoveryNode node;
        builder.startObject(NODES_KEY);
        for (KNNStatsNodeResponse knnStats : getNodes()) {
            node = knnStats.getNode();
            nodeId = node.getId();
            builder.startObject(nodeId);
            knnStats.toXContent(builder, params);
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }
}