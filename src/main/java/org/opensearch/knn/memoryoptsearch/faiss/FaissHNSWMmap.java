/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.memoryoptsearch.faiss;

import lombok.Getter;
import org.apache.lucene.store.IndexInput;
import org.opensearch.knn.memoryoptsearch.MemorySegmentAddressExtractorUtil;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Loads the entire FAISS HNSW graph into memory using memory-mapped files (mmap).
 * This provides fast access to the graph data by mapping the file directly into the process's address space.
 */
@Getter
public class FaissHNSWMmap {
    private IndexInput indexInput;
    private int[] cumNumberNeighborPerLevel;
    private int[] levels;
    private int[] offsets;
    private int[] neighbors;
    private int entryPoint;
    private int maxLevel = -1;
    private int efSearch = 16;
    private long totalNumberOfVectors;
    private long[] addressAndSize;

    /**
     * Loads the entire FAISS HNSW graph file into memory using mmap via IndexInput.
     *
     * @param indexInput IndexInput from MMapDirectory.
     * @param totalNumberOfVectors The total number of vectors stored in the graph.
     * @throws IOException
     */
    public void load(IndexInput indexInput, long totalNumberOfVectors) throws IOException {
        this.indexInput = indexInput;
        this.totalNumberOfVectors = totalNumberOfVectors;

        // Extract memory segment addresses and sizes
        this.addressAndSize = MemorySegmentAddressExtractorUtil.tryExtractAddressAndSize(indexInput, 0, indexInput.length());

        // Load the FAISS HNSW structure
        indexInput.seek(0);

        // Skip assignProbas
        long size = indexInput.readLong();
        indexInput.skipBytes(Double.BYTES * size);

        // Load cumNumberNeighborPerLevel
        size = indexInput.readLong();
        cumNumberNeighborPerLevel = new int[(int) size];
        if (size > 0) {
            indexInput.readInts(cumNumberNeighborPerLevel, 0, (int) size);
        }

        // Load levels
        size = indexInput.readLong();
        levels = new int[(int) size];
        if (size > 0) {
            indexInput.readInts(levels, 0, (int) size);
        }

        // Load offsets
        size = indexInput.readLong();
        offsets = new int[(int) size];
        if (size > 0) {
            indexInput.readInts(offsets, 0, (int) size);
        }

        // Load neighbors
        size = indexInput.readLong();
        neighbors = new int[(int) size];
        if (size > 0) {
            indexInput.readInts(neighbors, 0, (int) size);
        }

        // Load HNSW parameters
        entryPoint = indexInput.readInt();
        maxLevel = indexInput.readInt();
        indexInput.readInt(); // efConstruction (unused)
        efSearch = indexInput.readInt();
        indexInput.readInt(); // deprecated field
    }

    public int getMaxNumNeighbors() {
        if (cumNumberNeighborPerLevel != null && cumNumberNeighborPerLevel.length >= 1) {
            return cumNumberNeighborPerLevel[1];
        }
        return 0;
    }
}
