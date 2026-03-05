/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.memoryoptsearch;

import org.apache.lucene.store.IndexInput;
import org.opensearch.knn.memoryoptsearch.AbstractMemorySegmentAddressExtractor.MemorySegmentInfo;

/**
 * From {@link IndexInput}, this class tries to extract mapped address with size.
 * When {@link IndexInput} created from {@link org.apache.lucene.store.MMapDirectory} was given, then we can extract
 * mapped pointer from it for faster computation.
 */
public interface MemorySegmentAddressExtractor {
    /**
     * Try to extract {@code MemorySegment[]} from given input stream, and return address and size info of them.
     *
     * @param indexInput : Input stream
     * @param baseOffset : The offset used to determine which chunks to include.
     *                     Only chunks whose end offset is greater than baseOffset are collected, while those ending before baseOffset are
     *                     excluded.
     * @return null if it fails to extract mapped pointer otherwise it will return an array having address and size.
     *         Ex: address_i = array[i], size_i = array[i + 1].
     */
    long[] extractAddressAndSize(IndexInput indexInput, long baseOffset, long requestSize);

    /**
     * Try to extract {@code MemorySegment[]} from given input stream, and return memory segment info.
     *
     * @param indexInput : Input stream
     * @return null if it fails to extract mapped pointer otherwise it will return an array of MemorySegmentInfo.
     */
    MemorySegmentInfo[] extractMemorySegmentInfo(IndexInput indexInput);

    MemorySegmentAddressExtractor EMPTY = new MemorySegmentAddressExtractor() {
        @Override
        public long[] extractAddressAndSize(IndexInput indexInput, long baseOffset, long requestSize) {
            return null;
        }

        @Override
        public MemorySegmentInfo[] extractMemorySegmentInfo(IndexInput indexInput) {
            return null;
        }
    };

}
