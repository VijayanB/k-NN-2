# Exact Search for Quantized Vectors in FAISS

## Overview
This document compares three approaches for implementing exact (exhaustive) search on quantized binary vectors using FAISS in OpenSearch k-NN.

---

## Approach 1: Bypass HNSW Graph - Direct Storage Search

### Description
Bypass the HNSW graph layer and search directly on the underlying `IndexBinaryFlat` storage to perform brute-force exact search.

### Implementation
```cpp
auto hnswReader = dynamic_cast<const faiss::IndexBinaryHNSW*>(indexReader->index);
auto storage = dynamic_cast<const faiss::IndexBinaryFlat*>(hnswReader->storage);
storage->search(1, queryVector, k, distances, ids, nullptr);
// Manual ID mapping: customId = indexReader->id_map[internalId]
```

### Pros
- ✅ True exhaustive search (100% recall)
- ✅ Simple concept - direct brute-force
- ✅ No HNSW graph overhead

### Cons
- ❌ **No filter support** - `IndexBinaryFlat::search()` ignores `sel` parameter
- ❌ **Manual ID mapping required** - returns internal IDs, must map to custom IDs
- ❌ **Manual filtering** - must post-filter and re-sort results
- ❌ **Poor performance** - O(n) for every query
- ❌ **Breaks abstraction** - bypasses IDMap wrapper
- ❌ **No deleted vector handling** - storage doesn't track deletions

### Complexity
- Time: O(n × d/8) per query (n = vectors, d = dimensions)
- Space: O(k) for results

### Recommendation
**❌ Not Recommended** - Too many manual workarounds, defeats purpose of using FAISS abstractions.

---

## Approach 2: JNI Layer Exact Search Function

### Description
Implement a dedicated exact search function in the JNI layer that manually computes distances for all vectors.

### Implementation
```cpp
jobjectArray ExactSearchBinary(JNIEnv* env, jlong indexPtr, jbyteArray query, jint k) {
    auto* indexReader = reinterpret_cast<faiss::IndexBinaryIDMap*>(indexPtr);
    auto* storage = getStorage(indexReader);
    
    std::vector<std::pair<int32_t, faiss::idx_t>> results;
    for(size_t i = 0; i < storage->ntotal; i++) {
        int32_t dist = faiss::hamming(queryVec, storage->xb.data() + i * (d/8), d/8);
        results.push_back({dist, indexReader->id_map[i]});
    }
    std::partial_sort(results.begin(), results.begin() + k, results.end());
    return convertToJavaResults(results, k);
}
```

### Pros
- ✅ Full control over search logic
- ✅ Proper ID mapping built-in
- ✅ Can implement custom filtering efficiently
- ✅ Single JNI call - minimal overhead
- ✅ Reuses FAISS distance functions

### Cons
- ❌ More code to maintain in JNI layer
- ❌ Still O(n) performance
- ❌ Requires C++ expertise
- ❌ Must handle memory management carefully

### Complexity
- Time: O(n × d/8 + n log k) per query
- Space: O(n) temporary storage for all distances

### Recommendation
**✅ Recommended** - Best balance of control, performance, and maintainability.

---

## Approach 3: Java Layer with FAISS Distance Function

### Description
Implement exact search in Java, but use JNI calls to FAISS's `hamming()` function for distance computation.

### Implementation
```java
public KNNQueryResult[] exactSearch(byte[] query, int k, long indexPtr) {
    PriorityQueue<Result> heap = new PriorityQueue<>(k);
    
    for(int docId : allDocIds) {
        byte[] vector = getVector(indexPtr, docId);
        int distance = JNIFacade.calculateHammingDistance(query, vector);
        heap.offer(new Result(docId, distance));
        if(heap.size() > k) heap.poll();
    }
    return heap.toArray();
}

// JNI method
native int calculateHammingDistance(byte[] vec1, byte[] vec2);
```

### Pros
- ✅ Easy to implement and test
- ✅ Flexible - easy to add filtering, pagination
- ✅ No C++ expertise needed
- ✅ Leverages FAISS optimized distance computation

### Cons
- ❌ **Multiple JNI calls** - one per vector (huge overhead)
- ❌ **Memory copying** - vectors copied between Java/native
- ❌ **Slowest approach** - JNI overhead dominates
- ❌ **GC pressure** - creates many temporary objects
- ❌ Must retrieve all vectors from index

### Complexity
- Time: O(n × (JNI_overhead + d/8)) per query
- Space: O(n × d/8) if vectors cached in Java

### Recommendation
**❌ Not Recommended** - JNI overhead makes this prohibitively slow for large datasets.

---

## Comparison Matrix

| Aspect | Approach 1 | Approach 2 | Approach 3 | Approach 4 |
|--------|-----------|-----------|-----------|----------|
| **Performance** | Medium | Medium | Poor | Medium-Low |
| **Filter Support** | Manual | Native | Easy | Easy |
| **ID Mapping** | Manual | Native | Native | Native |
| **JNI Overhead** | Low | Low | Very High | Medium |
| **Code Complexity** | Medium | Medium | Low | Low |
| **Maintainability** | Poor | Good | Good | Excellent |
| **Scalability** | Poor | Poor | Very Poor | Medium |

---

## Approach 4: Hybrid Java Implementation (Selected)

### Description
Implement exact search logic entirely in Java, using only a single JNI call for optimized Hamming distance calculation. All filtering, ID mapping, and result sorting handled in Java.

### Implementation
```java
public KNNQueryResult[] exactSearch(byte[] query, int k, long indexPtr, BitSet filter) {
    PriorityQueue<Result> heap = new PriorityQueue<>(k, Comparator.comparingInt(r -> r.distance));
    
    // Get all vectors in single JNI call
    Map<Integer, byte[]> vectors = getAllVectors(indexPtr, filter);
    
    for(Map.Entry<Integer, byte[]> entry : vectors.entrySet()) {
        int docId = entry.getKey();
        byte[] vector = entry.getValue();
        
        // Single JNI call for distance calculation
        int distance = JNIFacade.calculateHammingDistance(query, vector);
        
        if(heap.size() < k) {
            heap.offer(new Result(docId, distance));
        } else if(distance < heap.peek().distance) {
            heap.poll();
            heap.offer(new Result(docId, distance));
        }
    }
    
    return heap.toArray(new KNNQueryResult[0]);
}

// Optimized JNI method - single call for distance
native int calculateHammingDistance(byte[] query, byte[] vector);
```

### Pros
- ✅ **Java-based logic** - Easy to implement, test, and maintain
- ✅ **Flexible filtering** - Can apply complex filters before distance calculation
- ✅ **Proper ID mapping** - Handles custom document IDs naturally
- ✅ **Optimized distance** - Uses FAISS's fast Hamming distance function
- ✅ **Reduced JNI calls** - Batch vector retrieval + single distance calls
- ✅ **Easy debugging** - Most logic in Java for easier troubleshooting

### Cons
- ❌ **Multiple JNI calls** - One per vector for distance calculation
- ❌ **Memory overhead** - Vectors stored temporarily in Java
- ❌ **GC pressure** - Creates temporary objects for vectors
- ❌ **Slower than Approach 2** - JNI overhead per distance calculation

### Complexity
- Time: O(n × JNI_overhead + n × d/8 + n log k) per query
- Space: O(n × d/8) for temporary vector storage

### Optimizations
1. **Batch vector retrieval** - Get all filtered vectors in single JNI call
2. **Early termination** - Skip distance calculation if heap is full and current min is better
3. **Memory pooling** - Reuse byte arrays to reduce GC pressure
4. **Parallel processing** - Use Java streams for concurrent distance calculations

### Recommendation
**✅ Selected Approach** - Balances maintainability with acceptable performance for exact search use cases.

---

## Final Recommendation

### **Use Approach 4: Hybrid Java Implementation**

**Rationale:**
1. **Maintainability** - Most logic in Java for easier development and testing
2. **Flexibility** - Easy to implement complex filtering and business logic
3. **Performance** - Leverages FAISS's optimized Hamming distance while keeping overhead manageable
4. **Debugging** - Java-based implementation easier to troubleshoot and profile
5. **Future-proof** - Can easily add features like pagination, custom scoring, etc.

**Alternative:**
If exact search is rarely needed, consider using HNSW with very high `efSearch` (e.g., `efSearch = ntotal`) instead - this provides near-exact results while maintaining proper abstractions.

---

## Implementation Notes

### Caveats of Direct Storage Access (Approach 1)

1. **ID Mapping Broken** - Storage returns internal IDs (0, 1, 2, ...), must manually map to custom IDs
2. **SearchParameters Ignored** - `IndexBinaryFlat::search()` ignores `sel` and `grp` parameters
3. **Manual Filtering Required** - Must post-filter results and re-sort
4. **No Deleted Vector Handling** - Storage doesn't track deletions
5. **Thread Safety** - Storage not designed for direct concurrent access
6. **Metric Type Issues** - Storage may not have correct metric configured

### Best Practices

- Always use `indexReader->search()` when possible for proper ID mapping
- For exact search, prefer Approach 2 (dedicated JNI function)
- Consider high `efSearch` as alternative to true exact search
- Profile performance before implementing exact search
- Document performance implications for users
