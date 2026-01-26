# Flowchart: JNI Layer Exact Search Implementation

## High-Level Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Java Layer Calls JNI                     │
│  exactSearchBinary(query, k, filterIds, indexPtr)           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  JNI Entry Point                            │
│  jobjectArray ExactSearchBinary(...)                        │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Validate Input Parameters                      │
│  • Check query != null                                      │
│  • Check indexPtr valid                                     │
│  • Check k > 0                                              │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│           Extract Index Components                          │
│  indexReader = (IndexBinaryIDMap*)indexPtr                  │
│  hnswIndex = indexReader->index                             │
│  storage = hnswIndex->storage                               │
│  id_map = indexReader->id_map                               │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│          Get Query Vector from Java                         │
│  queryVec = GetByteArrayElements(queryVectorJ)              │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│         Process Filter (if provided)                        │
│  if (filterIds != null) {                                   │
│    Build filter set/bitmap                                  │
│  }                                                           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│    Compute Distances for ALL Vectors                        │
│  for i = 0 to storage->ntotal:                              │
│    distance[i] = faiss::hamming(                            │
│      queryVec,                                              │
│      storage->xb.data() + i * (d/8),                        │
│      d/8                                                     │
│    )                                                         │
│    results.push({distance[i], i})                           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Apply Filter (if provided)                     │
│  filtered_results = []                                      │
│  for each result in results:                                │
│    internalId = result.id                                   │
│    customId = id_map[internalId]                            │
│    if (filter.contains(customId)):                          │
│      filtered_results.push({result.dist, customId})         │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Sort and Get Top-K                             │
│  partial_sort(                                              │
│    filtered_results.begin(),                                │
│    filtered_results.begin() + k,                            │
│    filtered_results.end()                                   │
│  )                                                           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│         Convert to Java Result Array                        │
│  results = NewObjectArray(k, KNNQueryResult)                │
│  for i = 0 to k:                                            │
│    results[i] = new KNNQueryResult(                         │
│      filtered_results[i].customId,                          │
│      filtered_results[i].distance                           │
│    )                                                         │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Cleanup and Return                             │
│  ReleaseByteArrayElements(queryVectorJ)                     │
│  ReleaseLongArrayElements(filterIdsJ)                       │
│  return results                                             │
└─────────────────────────────────────────────────────────────┘
```

## Detailed Component Flows

### 1. Distance Computation Loop

```
START: Compute Distances
    │
    ├─→ Initialize: i = 0, results = []
    │
    ├─→ LOOP: while i < storage->ntotal
    │       │
    │       ├─→ Get vector pointer:
    │       │   vecPtr = storage->xb.data() + i * (d/8)
    │       │
    │       ├─→ Compute Hamming distance:
    │       │   dist = faiss::hamming(queryVec, vecPtr, d/8)
    │       │
    │       ├─→ Store result:
    │       │   results.push({dist, i})
    │       │
    │       └─→ i++
    │
    └─→ END: Return results
```

### 2. Filter Application Flow

```
START: Apply Filter
    │
    ├─→ Has filter?
    │   │
    │   ├─→ NO: Return all results
    │   │
    │   └─→ YES: Continue
    │
    ├─→ Initialize: filtered = []
    │
    ├─→ LOOP: for each (distance, internalId) in results
    │       │
    │       ├─→ Map to custom ID:
    │       │   customId = id_map[internalId]
    │       │
    │       ├─→ Check filter:
    │       │   │
    │       │   ├─→ BITMAP:
    │       │   │   if bitmap.is_member(customId)
    │       │   │
    │       │   └─→ BATCH:
    │       │       if filterSet.contains(customId)
    │       │
    │       ├─→ If passes filter:
    │       │   filtered.push({distance, customId})
    │       │
    │       └─→ Next result
    │
    └─→ END: Return filtered
```

### 3. Top-K Selection Flow

```
START: Get Top-K
    │
    ├─→ Check: filtered.size() >= k?
    │   │
    │   ├─→ YES: Use partial_sort for k elements
    │   │   partial_sort(begin, begin+k, end)
    │   │
    │   └─→ NO: Sort all elements
    │       sort(begin, end)
    │
    ├─→ resultSize = min(filtered.size(), k)
    │
    ├─→ Extract top resultSize elements
    │
    └─→ END: Return top-k results
```

## Memory Management Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Acquire Resources                        │
│  • queryVec = GetByteArrayElements()                        │
│  • filterIds = GetLongArrayElements()                       │
│  • Allocate results vector                                  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  Process (try block)                        │
│  • Compute distances                                        │
│  • Apply filters                                            │
│  • Sort results                                             │
└────────────────────────┬────────────────────────────────────┘
                         │
                    ┌────┴────┐
                    │         │
              Success    Exception
                    │         │
                    ▼         ▼
┌─────────────────────────────────────────────────────────────┐
│              Release Resources (finally)                    │
│  • ReleaseByteArrayElements(queryVec, JNI_ABORT)            │
│  • ReleaseLongArrayElements(filterIds, JNI_ABORT)           │
│  • If exception: rethrow                                    │
└─────────────────────────────────────────────────────────────┘
```

## Error Handling Flow

```
START
  │
  ├─→ Validate inputs
  │   └─→ Invalid? → Throw runtime_error
  │
  ├─→ Try: Main processing
  │   │
  │   └─→ Exception?
  │       │
  │       ├─→ Release JNI resources
  │       │
  │       └─→ Rethrow exception
  │
  └─→ Normal cleanup and return
```

## Performance Characteristics

```
┌──────────────────────┬─────────────────────────────────┐
│ Operation            │ Complexity                      │
├──────────────────────┼─────────────────────────────────┤
│ Distance Computation │ O(n × d/8)                      │
│ Filter Application   │ O(n) or O(n × log m)            │
│ Top-K Selection      │ O(n log k) or O(n)              │
│ ID Mapping           │ O(k)                            │
│ Total                │ O(n × d/8 + n log k)            │
└──────────────────────┴─────────────────────────────────┘

Where:
  n = total vectors
  d = dimensions
  k = results requested
  m = filter size
```

## Key Decision Points

```
1. Filter Type?
   ├─→ BITMAP: Use IDSelectorJlongBitmap
   └─→ BATCH: Use unordered_set

2. Filter Present?
   ├─→ YES: Apply filter during/after distance computation
   └─→ NO: Return top-k from all results

3. Results < k?
   ├─→ YES: Return all results
   └─→ NO: Return exactly k results

4. Exception Occurred?
   ├─→ YES: Cleanup → Rethrow
   └─→ NO: Normal cleanup → Return
```
