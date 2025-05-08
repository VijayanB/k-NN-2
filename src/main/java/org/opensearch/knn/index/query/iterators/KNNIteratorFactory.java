/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.knn.index.query.iterators;

import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.join.BitSetProducer;
import org.opensearch.common.lucene.Lucene;
import org.opensearch.knn.common.FieldInfoExtractor;
import org.opensearch.knn.index.SpaceType;
import org.opensearch.knn.index.query.SegmentLevelQuantizationInfo;
import org.opensearch.knn.index.query.SegmentLevelQuantizationUtil;
import org.opensearch.knn.index.vectorvalues.*;
import org.opensearch.knn.indices.ModelDao;

import java.io.IOException;

/**
 * Factory class for creating KNN iterators based on vector types and search requirements.
 */
@Log4j2
@Builder
public class KNNIteratorFactory {
    private final DocIdSetIterator filterIterator;
    private final String fieldName;
    private final boolean isParentHits;
    private final BitSetProducer parentsFilter;

    /**
     * Creates a KNNIterator based on the provided target vector.
     *
     * @param target The target vector for KNN search
     * @return KNNIterator instance or null if field info is not found
     * @throws IOException if there's an error reading from the index
     */
    public KNNIterator buildFloatKNNIterator(final LeafReaderContext leafReaderContext, float[] target) throws IOException {
        SegmentReader reader = Lucene.segmentReader(leafReaderContext.reader());
        FieldInfo fieldInfo = FieldInfoExtractor.getFieldInfo(reader, fieldName);

        if (fieldInfo == null) {
            log.debug("[KNN] Cannot get KNNIterator as Field info not found for {}:{}", fieldName, reader.getSegmentName());
            return null;
        }
        SpaceType spaceType = FieldInfoExtractor.getSpaceType(ModelDao.OpenSearchKNNModelDao.getInstance(), fieldInfo);
        KNNVectorValues<?> vectorValues = KNNVectorValuesFactory.getVectorValues(fieldInfo, reader);
        return getIteratorInstance(target, leafReaderContext, vectorValues, spaceType);
    }

    public KNNIterator buildByteKNNIterator(final LeafReaderContext leafReaderContext, float[] target) throws IOException {
        return buildFloatKNNIterator(leafReaderContext, target);
    }

    public KNNIterator buildBinaryKNNIterator(final LeafReaderContext leafReaderContext, byte[] target) throws IOException {
        SegmentReader reader = Lucene.segmentReader(leafReaderContext.reader());
        FieldInfo fieldInfo = FieldInfoExtractor.getFieldInfo(reader, fieldName);

        if (fieldInfo == null) {
            log.debug("[KNN] Cannot get KNNIterator as Field info not found for {}:{}", fieldName, reader.getSegmentName());
            return null;
        }

        SpaceType spaceType = FieldInfoExtractor.getSpaceType(ModelDao.OpenSearchKNNModelDao.getInstance(), fieldInfo);
        KNNVectorValues<?> vectorValues = KNNVectorValuesFactory.getVectorValues(fieldInfo, reader);
        boolean isNestedRequired = isParentHits && parentsFilter != null;
        return isNestedRequired
            ? new NestedBinaryVectorIdsKNNIterator(
                filterIterator,
                target,
                (KNNBinaryVectorValues) vectorValues,
                spaceType,
                parentsFilter.getBitSet(leafReaderContext)
            )
            : new BinaryVectorIdsKNNIterator(filterIterator, target, (KNNBinaryVectorValues) vectorValues, spaceType);

    }

    private KNNIterator buildQunatizedKNNIterator(final LeafReaderContext leafReaderContext, float[] target) throws IOException {
        SegmentReader reader = Lucene.segmentReader(leafReaderContext.reader());
        FieldInfo fieldInfo = FieldInfoExtractor.getFieldInfo(reader, fieldName);

        // Build Segment Level Quantization info.
        final SegmentLevelQuantizationInfo segmentLevelQuantizationInfo = SegmentLevelQuantizationInfo.build(reader, fieldInfo, fieldName);
        final byte[] quantizedQueryVector = SegmentLevelQuantizationUtil.quantizeVector(target, segmentLevelQuantizationInfo);
        SpaceType spaceType = FieldInfoExtractor.getSpaceType(ModelDao.OpenSearchKNNModelDao.getInstance(), fieldInfo);
        KNNVectorValues<?> vectorValues = KNNVectorValuesFactory.getVectorValues(fieldInfo, reader);
        boolean isNestedRequired = isParentHits && parentsFilter != null;
        return isNestedRequired
            ? new NestedQuantizedVectorIdsKNNIterator(
                filterIterator,
                quantizedQueryVector,
                (KNNFloatVectorValues) vectorValues,
                spaceType,
                parentsFilter.getBitSet(leafReaderContext),
                segmentLevelQuantizationInfo
            )
            : new QuantizedVectorIdsKNNIterator(
                filterIterator,
                quantizedQueryVector,
                (KNNFloatVectorValues) vectorValues,
                spaceType,
                segmentLevelQuantizationInfo
            );

    }

    private KNNIterator getIteratorInstance(
        float[] target,
        final LeafReaderContext leafReaderContext,
        KNNVectorValues<?> vectorValues,
        SpaceType spaceType
    ) throws IOException {
        boolean isNestedRequired = isParentHits && parentsFilter != null;
        if (vectorValues instanceof KNNFloatVectorValues) {
            return isNestedRequired
                ? new NestedVectorIdsKNNIterator(
                    filterIterator,
                    target,
                    (KNNFloatVectorValues) vectorValues,
                    spaceType,
                    parentsFilter.getBitSet(leafReaderContext)
                )
                : new VectorIdsKNNIterator(filterIterator, target, (KNNFloatVectorValues) vectorValues, spaceType);
        } else if (vectorValues instanceof KNNByteVectorValues) {
            return isNestedRequired
                ? new NestedByteVectorIdsKNNIterator(
                    filterIterator,
                    target,
                    (KNNByteVectorValues) vectorValues,
                    spaceType,
                    parentsFilter.getBitSet(leafReaderContext)
                )
                : new ByteVectorIdsKNNIterator(filterIterator, target, (KNNByteVectorValues) vectorValues, spaceType);
        }
        throw new IllegalStateException("Unsupported vector values type for float target: " + vectorValues.getClass());
    }
}
