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

package org.opensearch.knn.plugin.stats.suppliers;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.knn.indices.ModelDao;

import java.util.Objects;
import java.util.function.Supplier;

public class ModelIndexStatusSupplier implements Supplier<ClusterHealthStatus> {
    private ModelDao modelDao;


    public ModelIndexStatusSupplier() {
        this(ModelDao.OpenSearchKNNModelDao.getInstance());
    }

    /**
     * Constructor
     *
     * @param modelDao {@link ModelDao} instance to get health status
     */
    public ModelIndexStatusSupplier(@NonNull ModelDao modelDao) {
        this.modelDao = Objects.requireNonNull(modelDao);
    }

    @Override
    public ClusterHealthStatus get() {
        try {
            return modelDao.getHealthStatus();
        }catch (IndexNotFoundException e){
            return null;
        }
    }
}
