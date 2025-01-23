/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.codec.KNN990Codec;

import lombok.Builder;
import org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.apache.lucene.codecs.hnsw.FlatVectorsReader;
import org.apache.lucene.codecs.lucene99.Lucene99FlatVectorsFormat;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.hnsw.RandomVectorScorer;
import org.opensearch.common.util.io.IOUtils;
import org.opensearch.knn.index.SpaceType;
import org.opensearch.knn.index.codec.util.KNNCodecUtil;
import org.opensearch.knn.index.engine.KNNEngine;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.opensearch.knn.index.engine.KNNEngine.FAISS;

public class FaissFlatVectorsReader extends FlatVectorsReader {
    // 1. A Footer magic number (int - 4 bytes)
    // 2. A checksum algorithm id (int - 4 bytes)
    // 3. A checksum (long - bytes)
    // The checksum is computed on all the bytes written to the file up to that point.
    // Logic where footer is written in Lucene can be found here:
    // https://github.com/apache/lucene/blob/branch_9_0/lucene/core/src/java/org/apache/lucene/codecs/CodecUtil.java#L390-L412
    public static final int FOOT_MAGIC_SIZE = RamUsageEstimator.primitiveSizes.get(Integer.TYPE);
    public static final int ALGORITHM_SIZE = RamUsageEstimator.primitiveSizes.get(Integer.TYPE);
    public static final int CHECKSUM_SIZE = RamUsageEstimator.primitiveSizes.get(Long.TYPE);
    public static final int FLOAT_SIZE = RamUsageEstimator.primitiveSizes.get(Float.TYPE);
    public static final int SIZET_SIZE = RamUsageEstimator.primitiveSizes.get(Long.TYPE);
    public static final int FOOTER_SIZE = FOOT_MAGIC_SIZE + ALGORITHM_SIZE + CHECKSUM_SIZE;
    private static final long SHALLOW_SIZE = RamUsageEstimator.shallowSizeOfInstance(Lucene99FlatVectorsFormat.class);

    private final Map<String, IndexInput> fieldInputMap = new HashMap<>();
    private final Map<String, FaissFlatVectorsReader.FieldEntry> fields = new HashMap<>();

    public FaissFlatVectorsReader(final SegmentReadState state) throws IOException {
        super(new DefaultFlatVectorScorer());
        this.buildFieldInputMap(state);
        this.buildFieldsMap(state);
    }

    private void buildFieldInputMap(SegmentReadState state) throws IOException {
        final FieldInfos fieldInfos = state.fieldInfos;
        final SegmentInfo segmentInfo = state.segmentInfo;
        final Directory directory = state.directory;
        for (FieldInfo info : fieldInfos) {
            KNNEngine knnEngine = KNNCodecUtil.getNativeKNNEngine(info);
            if (FAISS != knnEngine) {
                continue;
            }
            final String vectorIndexFileName = KNNCodecUtil.getNativeEngineFileFromFieldInfo(info, segmentInfo);
            if (vectorIndexFileName == null) {
                continue;
            }
            final IndexInput in = directory.openInput(vectorIndexFileName, state.context.withRandomAccess());
            fieldInputMap.put(info.getName(), in);
        }
    }

    private void buildFieldsMap(SegmentReadState state) throws IOException {
        if (this.fieldInputMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, IndexInput> entry : this.fieldInputMap.entrySet()) {
            IndexInput in = entry.getValue();
            in.readInt();
            FieldInfo info = state.fieldInfos.fieldInfo(entry.getKey());
            FaissFlatVectorsReader.FieldEntry fieldEntry = this.readField(info, in);
            this.fields.put(entry.getKey(), fieldEntry);
        }
    }

    private FieldEntry readField(FieldInfo fieldInfo, IndexInput in) throws IOException {
        int dimension = in.readInt();
        long size = in.readLong();
        // skip two dummy longs
        in.readLong();
        in.readLong();
        // skip is trained attribute
        in.readByte();
        // extract metric type
        int metric_type = in.readInt();
        VectorSimilarityFunction vectorSimilarityFunction = convertMetricTypeToSimilarityFunction(metric_type);
        // skip metric args
        in.readInt();
        long filesize = in.length();
        // There is (size+1) * idx_t and FOOTER_SIZE
        long idSeek = filesize - (size + 1) * SIZET_SIZE - FOOTER_SIZE;
        long vectorSeek = idSeek - ((long) FLOAT_SIZE * dimension) * size - SIZET_SIZE;
        return FieldEntry.builder()
            .vectorEncoding(fieldInfo.getVectorEncoding())
            .dimension(dimension)
            .vectorDataOffset(vectorSeek)
            .idOffset(idSeek)
            .size(size)
            .similarityFunction(vectorSimilarityFunction)
            .build();
    }

    private VectorSimilarityFunction convertMetricTypeToSimilarityFunction(int metricType) {
        // Ref from jni/external/faiss/c_api/Index_c.h
        switch (metricType) {
            case 0:
                return SpaceType.INNER_PRODUCT.getKnnVectorSimilarityFunction().getVectorSimilarityFunction();
            case 1:
                return SpaceType.L2.getKnnVectorSimilarityFunction().getVectorSimilarityFunction();
            case 2:
                return SpaceType.L1.getKnnVectorSimilarityFunction().getVectorSimilarityFunction();
            case 3:
                return SpaceType.LINF.getKnnVectorSimilarityFunction().getVectorSimilarityFunction();
            default:
                throw new IllegalArgumentException("Unsupported metric type: " + metricType);
        }
    }

    @Override
    public RandomVectorScorer getRandomVectorScorer(String s, float[] floats) {
        return null;
    }

    @Override
    public RandomVectorScorer getRandomVectorScorer(String s, byte[] bytes) {
        return null;
    }

    @Override
    public void checkIntegrity() throws IOException {

    }

    @Override
    public FloatVectorValues getFloatVectorValues(String field) throws IOException {
        FieldEntry fieldEntry = fields.get(field);
        IndexInput input = fieldInputMap.get(field);
        if (fieldEntry == null) {
            throw new IllegalArgumentException("field=\"" + field + "\" not found");
        } else if (fieldEntry.vectorEncoding != VectorEncoding.FLOAT32) {
            throw new IllegalArgumentException(
                "field=\"" + field + "\" is encoded as: " + fieldEntry.vectorEncoding + " expected: " + VectorEncoding.FLOAT32
            );
        } else {
            return FaissFloatVectorValues.load(
                fieldEntry.similarityFunction,
                fieldEntry.vectorEncoding,
                fieldEntry.dimension,
                (int) fieldEntry.size,
                fieldEntry.idOffset,
                fieldEntry.vectorDataOffset,
                input
            );
        }
    }

    @Override
    public ByteVectorValues getByteVectorValues(String field) throws IOException {
        FieldEntry fieldEntry = fields.get(field);
        IndexInput input = fieldInputMap.get(field);
        if (fieldEntry == null) {
            throw new IllegalArgumentException("field=\"" + field + "\" not found");
        } else if (fieldEntry.vectorEncoding != VectorEncoding.BYTE) {
            throw new IllegalArgumentException(
                "field=\"" + field + "\" is encoded as: " + fieldEntry.vectorEncoding + " expected: " + VectorEncoding.FLOAT32
            );
        } else {
            return FaissBinaryVectorValues.load(
                fieldEntry.similarityFunction,
                fieldEntry.vectorEncoding,
                fieldEntry.dimension,
                (int) fieldEntry.size,
                fieldEntry.idOffset,
                fieldEntry.vectorDataOffset,
                input
            );
        }
    }

    @Override
    public void close() throws IOException {
        for (Map.Entry<String, IndexInput> entry : fieldInputMap.entrySet()) {
            IndexInput in = entry.getValue();
            IOUtils.close(in);
        }
    }

    @Override
    public long ramBytesUsed() {
        return SHALLOW_SIZE + RamUsageEstimator.sizeOfMap(
            this.fields,
            RamUsageEstimator.shallowSizeOfInstance(FaissFlatVectorsReader.class)
        );
    }

    @Builder
    private static class FieldEntry {
        final VectorSimilarityFunction similarityFunction;
        final VectorEncoding vectorEncoding;
        final int dimension;
        final long vectorDataOffset;
        final long idOffset;
        final long size;
    }
}
