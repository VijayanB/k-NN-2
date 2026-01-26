## Refined Approach 2: Single JNI Call with Batch Processing

```mermaid
sequenceDiagram
    participant Java
    participant JNI as JNI Method<br/>(searchStorageBatch)
    participant Index
    participant Storage
    
    Java->>Java: exactSearchWithFilter(query, k, customIds[])
    Java->>JNI: searchStorageBatch(indexPtr, query, k, customIds[])
    
    JNI->>Index: Get IndexBinaryIDMap pointer
    JNI->>Index: Extract id_map from IndexBinaryIDMap
    JNI->>JNI: Build reverse map (customId->internalId)
    JNI->>JNI: Convert customIds[] to internalIds[] using reverse map
    
    JNI->>Index: Get IndexBinaryHNSW pointer
    JNI->>Index: auto storage = hnswReader->storage
    JNI->>Storage: storage->search(1, query, k, distances, ids, internalIds[])
    Storage->>Storage: Iterate only through specified internalIds
    Storage->>Storage: Calculate hamming distances
    Storage-->>JNI: distances[], internalIds[]
    
    JNI->>JNI: Convert internalIds[] back to customIds[] using id_map
    JNI->>JNI: Create KNNQueryResult objects
    JNI-->>Java: Return KNNQueryResult[]
```

## Approach 3: Direct Hamming Distance Calculation

```mermaid
sequenceDiagram
    participant Java
    participant JNI as JNI Method<br/>(directHammingSearch)
    participant Index
    participant Storage
    
    Java->>Java: exactSearchWithFilter(query, k, customIds[])
    Java->>JNI: directHammingSearch(indexPtr, query, k, customIds[])
    
    alt customIds is null/empty
        JNI->>Index: Get IndexBinaryHNSW pointer
        JNI->>Index: auto storage = hnswReader->storage
        JNI->>Storage: storage->search(1, query, k, distances, ids, nullptr)
        Storage-->>JNI: distances[], internalIds[]
        JNI->>Index: Get id_map from IndexBinaryIDMap
        JNI->>JNI: Convert internalIds[] to customIds[] using id_map
        JNI->>JNI: Create KNNQueryResult objects
        JNI-->>Java: Return KNNQueryResult[]
    else customIds provided
        JNI->>Index: Get IndexBinaryIDMap pointer
        JNI->>Index: Extract id_map from IndexBinaryIDMap
        JNI->>JNI: Build reverse map (customId->internalId)
        JNI->>JNI: Convert customIds[] to internalIds[] using reverse map
        
        JNI->>Index: Get IndexBinaryHNSW pointer
        JNI->>Index: auto storage = hnswReader->storage
        JNI->>JNI: Initialize priority queue for top-k results
        
        loop For each internalId in internalIds[]
            JNI->>Storage: Get vector pointer by internalId
            JNI->>JNI: Calculate hamming distance directly
            JNI->>JNI: Update priority queue if distance < current max
        end
        
        JNI->>JNI: Convert final internalIds[] to customIds[] using id_map
        JNI->>JNI: Create KNNQueryResult objects
        JNI-->>Java: Return KNNQueryResult[]
    end
```
## Complexity Comparison: Approach 1 vs Approach 2

### Approach 1 (Two JNI Calls)
**Time Complexity:**
- JNI Call 1: O(n) - extract id_map
- Java processing: O(n + m) - build reverse map + convert customIds
- JNI Call 2: O(m * log k) - search with bitmap filter

**Space Complexity:**
- Java: O(n + m) - id_map array + reverse map
- JNI: O(m) - bitmap/filter data

**Overhead:**
- 2 JNI boundary crossings
- Java heap allocation for id_map array
- Java HashMap operations for reverse mapping

### Approach 2 (Single JNI Call)
**Time Complexity:**
- Single JNI Call: O(n + m + m * log k) - extract id_map + build reverse map + search

**Space Complexity:**
- JNI: O(n + m) - id_map + reverse map in native memory

**Overhead Reduction:**
- 1 JNI boundary crossing (50% reduction)
- No Java heap allocation for id_map
- Native C++ unordered_map (faster than Java HashMap)
- Eliminates Java GC pressure from temporary objects

**Performance Gains:**
- Reduced JNI overhead: ~10-20% improvement
- Better memory locality: id mapping done in same memory space as search
- No Java object creation for intermediate data structures
## Approach 4: Storage Search with Bitmap Translation

```mermaid
sequenceDiagram
    participant Java
    participant JNI as JNI Method<br/>(storageSearchWithFilter)
    participant Index
    participant Storage
    
    Java->>Java: exactSearchWithFilter(query, k, customIds[])
    Java->>JNI: storageSearchWithFilter(indexPtr, query, k, customIds[])
    
    alt customIds is null/empty
        JNI->>Index: Get IndexBinaryHNSW pointer
        JNI->>Index: auto storage = hnswReader->storage
        JNI->>Storage: storage->search(1, query, k, distances, ids, nullptr)
        Storage-->>JNI: distances[], internalIds[]
        JNI->>Index: Get id_map from IndexBinaryIDMap
        JNI->>JNI: Convert internalIds[] to customIds[] using id_map
        JNI->>JNI: Create KNNQueryResult objects
        JNI-->>Java: Return KNNQueryResult[]
    else customIds provided
        JNI->>Index: Get IndexBinaryIDMap pointer
        JNI->>Index: Extract id_map from IndexBinaryIDMap
        JNI->>JNI: Build reverse map (customId->internalId)
        JNI->>JNI: Convert customIds[] to internalIds[] using reverse map
        JNI->>JNI: Create IDSelectorTranslated with bitmap filter
        
        JNI->>Index: Get IndexBinaryHNSW pointer
        JNI->>Index: auto storage = hnswReader->storage
        JNI->>Storage: storage->search(1, query, k, distances, ids, translatedSelector)
        Storage->>Storage: Filter using translated internal ID selector
        Storage->>Storage: Calculate hamming distances for filtered vectors
        Storage-->>JNI: distances[], internalIds[]
        
        JNI->>JNI: Convert internalIds[] to customIds[] using id_map
        JNI->>JNI: Create KNNQueryResult objects
        JNI-->>Java: Return KNNQueryResult[]
    end
```