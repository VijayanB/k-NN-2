# Exact Search JNI Implementation - Mermaid Diagrams

## Main Flow with Early Filter Check

```mermaid
flowchart TD
    Start([Java calls exactSearchBinary]) --> ValidateInput{Validate Input?}
    ValidateInput -->|Invalid| ThrowError[Throw Exception]
    ValidateInput -->|Valid| ExtractIndex[Extract Index Components<br/>indexReader, storage, id_map]
    
    ExtractIndex --> GetQuery[Get Query Vector<br/>from Java byte array]
    GetQuery --> HasFilter{Has Filter?}
    
    HasFilter -->|No| ComputeAll[Compute distances<br/>for ALL vectors]
    HasFilter -->|Yes| BuildFilter[Build Filter Set<br/>from custom IDs]
    
    BuildFilter --> BuildReverseMap[Build Reverse Map<br/>customId → internalId]
    BuildReverseMap --> LoopStart[Initialize: i = 0<br/>results = empty]
    
    LoopStart --> CheckLoop{i < ntotal?}
    CheckLoop -->|No| SortResults[Sort results by distance]
    CheckLoop -->|Yes| GetCustomId[customId = id_map[i]]
    
    GetCustomId --> CheckFilter{Filter contains<br/>customId?}
    CheckFilter -->|No| IncrementSkip[i++]
    CheckFilter -->|Yes| ComputeDist[Compute Hamming Distance<br/>dist = hamming query, storage[i]]
    
    ComputeDist --> StoreResult[Store: results.push<br/>distance, customId]
    StoreResult --> Increment[i++]
    Increment --> CheckLoop
    IncrementSkip --> CheckLoop
    
    ComputeAll --> SortAll[Sort all results]
    SortAll --> SortResults
    
    SortResults --> GetTopK[Get top-k results<br/>min k, results.size]
    GetTopK --> ConvertJava[Convert to Java<br/>KNNQueryResult array]
    
    ConvertJava --> Cleanup[Release JNI resources<br/>query, filter arrays]
    Cleanup --> Return([Return results to Java])
    
    ThrowError --> End([End])
    Return --> End
    
    style Start fill:#e1f5e1
    style Return fill:#e1f5e1
    style End fill:#ffe1e1
    style CheckFilter fill:#fff4e1
    style ComputeDist fill:#e1f0ff
```

## Detailed Distance Computation with Filter

```mermaid
flowchart TD
    Start([Start Distance Loop]) --> Init[i = 0<br/>results = empty<br/>filtered_count = 0]
    
    Init --> Loop{i < ntotal?}
    
    Loop -->|No| Done[Return results<br/>filtered_count items]
    Loop -->|Yes| MapId[Map internal to custom:<br/>customId = id_map[i]]
    
    MapId --> HasFilter{Has Filter?}
    
    HasFilter -->|No| Compute[Compute Distance]
    HasFilter -->|Yes| CheckMember{Filter contains<br/>customId?}
    
    CheckMember -->|No| Skip[Skip this vector<br/>i++]
    CheckMember -->|Yes| Compute
    
    Compute --> CalcHamming[dist = faiss::hamming<br/>query, storage + i*d/8, d/8]
    CalcHamming --> Store[results.push<br/>dist, customId]
    Store --> IncCount[filtered_count++]
    IncCount --> Next[i++]
    Next --> Loop
    Skip --> Loop
    
    Done --> End([End])
    
    style Start fill:#e1f5e1
    style End fill:#e1f5e1
    style CheckMember fill:#fff4e1
    style CalcHamming fill:#e1f0ff
    style Skip fill:#ffe1e1
```

## Filter Building Process

```mermaid
flowchart TD
    Start([Receive Filter IDs<br/>custom IDs]) --> CheckType{Filter Type?}
    
    CheckType -->|BITMAP| BuildBitmap[Create IDSelectorJlongBitmap<br/>from bitmap array]
    CheckType -->|BATCH| BuildSet[Create unordered_set<br/>from ID array]
    
    BuildBitmap --> BuildRevMap[Build Reverse Map]
    BuildSet --> BuildRevMap
    
    BuildRevMap --> InitMap[reverseMap = empty]
    InitMap --> LoopMap{For each i in id_map}
    
    LoopMap -->|More| AddMapping[reverseMap customId → i]
    LoopMap -->|Done| CreateTranslated[Create IDSelectorTranslated<br/>id_map, original_selector]
    
    AddMapping --> LoopMap
    
    CreateTranslated --> Return([Return: filter ready<br/>for internal ID checks])
    
    style Start fill:#e1f5e1
    style Return fill:#e1f5e1
    style CheckType fill:#fff4e1
```

## Top-K Selection with Partial Sort

```mermaid
flowchart TD
    Start([Receive filtered results]) --> CheckSize{results.size<br/>vs k?}
    
    CheckSize -->|size < k| UseAll[resultSize = size<br/>sort all results]
    CheckSize -->|size >= k| UseK[resultSize = k<br/>partial_sort k elements]
    
    UseAll --> SortAll[sort results.begin<br/>results.end]
    UseK --> PartialSort[partial_sort<br/>begin, begin+k, end]
    
    SortAll --> Extract[Extract resultSize elements]
    PartialSort --> Extract
    
    Extract --> Return([Return top-k results])
    
    style Start fill:#e1f5e1
    style Return fill:#e1f5e1
    style CheckSize fill:#fff4e1
```

## Memory Management Flow

```mermaid
flowchart TD
    Start([Function Entry]) --> Acquire[Acquire JNI Resources<br/>GetByteArrayElements<br/>GetLongArrayElements]
    
    Acquire --> TryBlock[Try Block Start]
    TryBlock --> Process[Main Processing<br/>compute, filter, sort]
    
    Process --> Success{Success?}
    
    Success -->|Yes| NormalCleanup[Release Resources<br/>JNI_ABORT mode]
    Success -->|No| Exception[Catch Exception]
    
    Exception --> ErrorCleanup[Release Resources<br/>JNI_ABORT mode]
    ErrorCleanup --> Rethrow[Rethrow Exception]
    
    NormalCleanup --> Return([Return Results])
    Rethrow --> End([End with Error])
    Return --> End2([End Success])
    
    style Start fill:#e1f5e1
    style Return fill:#e1f5e1
    style End fill:#ffe1e1
    style End2 fill:#e1f5e1
    style Exception fill:#ffcccc
```

## Complete Sequence Diagram with Hamming Distance API

```mermaid
sequenceDiagram
    participant Java
    participant JNI
    participant Index
    participant Storage
    participant Filter
    participant FAISS
    
    Java->>JNI: exactSearchBinary(query, k, filter)
    JNI->>JNI: Validate inputs
    JNI->>Index: Get id_map, ntotal, dimension
    Index-->>JNI: id_map[], n, d
    
    alt Has Filter
        JNI->>Filter: Build filter set from custom IDs
        Filter-->>JNI: Filter ready
    end
    
    JNI->>JNI: Get query vector from Java
    JNI->>Storage: Get storage pointer
    Storage-->>JNI: storage->xb pointer
    
    loop For each internal ID i in [0, ntotal)
        JNI->>Index: customId = id_map[i]
        Index-->>JNI: customId
        
        alt Has Filter
            JNI->>Filter: contains(customId)?
            Filter-->>JNI: true/false
            
            alt Not in filter
                Note over JNI: Skip - no distance computation
            else In filter
                JNI->>FAISS: calculateHammingDistance(indexPtr, query, internalId=i)
                FAISS->>Storage: Get vector pointer: storage->xb + i*(d/8)
                Storage-->>FAISS: vector pointer
                FAISS->>FAISS: hamming(query, vecPtr, d/8)
                FAISS-->>JNI: distance
                JNI->>JNI: Store (distance, customId)
            end
        else No Filter
            JNI->>FAISS: calculateHammingDistance(indexPtr, query, internalId=i)
            FAISS->>Storage: Get vector pointer: storage->xb + i*(d/8)
            Storage-->>FAISS: vector pointer
            FAISS->>FAISS: hamming(query, vecPtr, d/8)
            FAISS-->>JNI: distance
            JNI->>JNI: Store (distance, customId)
        end
    end
    
    JNI->>JNI: Sort results by distance
    JNI->>JNI: Get top-k results
    JNI->>JNI: Convert to Java KNNQueryResult[]
    JNI->>JNI: Release JNI resources
    JNI-->>Java: Return KNNQueryResult[]
```

## Optimized Batch Distance Computation

```mermaid
sequenceDiagram
    participant Java
    participant JNI
    participant Index
    participant Filter
    participant FAISS
    
    Java->>JNI: exactSearchBinary(query, k, filter)
    JNI->>JNI: Validate and extract components
    JNI->>Index: Get id_map
    Index-->>JNI: id_map[]
    
    alt Has Filter
        JNI->>Filter: Build filter set
        Filter-->>JNI: Filter ready
        
        Note over JNI: Collect filtered internal IDs
        loop For each i in [0, ntotal)
            JNI->>Index: customId = id_map[i]
            Index-->>JNI: customId
            JNI->>Filter: contains(customId)?
            Filter-->>JNI: true/false
            alt In filter
                JNI->>JNI: filteredIds.push(i)
            end
        end
        
        Note over JNI: Batch compute distances
        JNI->>FAISS: calculateHammingDistances(indexPtr, query, filteredIds[])
        loop For each id in filteredIds
            FAISS->>FAISS: Get vecPtr, compute hamming
        end
        FAISS-->>JNI: distances[]
        
        JNI->>JNI: Combine (distances, customIds)
    else No Filter
        Note over JNI: Compute all distances
        JNI->>FAISS: calculateAllHammingDistances(indexPtr, query)
        FAISS-->>JNI: distances[] for all vectors
        JNI->>JNI: Map to customIds
    end
    
    JNI->>JNI: Sort and get top-k
    JNI-->>Java: Return results
```

## Performance Comparison

```mermaid
graph LR
    A[Total Vectors: n] --> B{Filter Present?}
    
    B -->|No Filter| C[Compute: n distances<br/>O n × d/8]
    B -->|With Filter| D[Check: n filters<br/>O n]
    
    D --> E[Compute: m distances<br/>O m × d/8<br/>where m << n]
    
    C --> F[Sort: O n log k]
    E --> G[Sort: O m log k]
    
    F --> H[Total: O n × d/8 + n log k]
    G --> I[Total: O n + m × d/8 + m log k<br/>Much faster when m << n]
    
    style I fill:#e1f5e1
    style H fill:#fff4e1
```

## Key Optimization: Early Filter Check

```mermaid
flowchart LR
    A[Vector i] --> B{Check Filter<br/>BEFORE compute}
    
    B -->|Not in filter| C[Skip<br/>Save expensive<br/>distance calc]
    B -->|In filter| D[Compute Distance<br/>Only for filtered IDs]
    
    C --> E[Next vector]
    D --> F[Store result]
    F --> E
    
    style C fill:#ffe1e1
    style D fill:#e1f0ff
    style B fill:#fff4e1
```


---

# Approach 3: Java Layer Exact Search with JNI Hamming Calls

## Main Flow - Java Layer Implementation

```mermaid
flowchart TD
    Start([Java: exactSearch called]) --> Init[Initialize:<br/>PriorityQueue heap<br/>maxHeap size k]
    
    Init --> GetAllDocs[Get all document IDs<br/>from index]
    GetAllDocs --> HasFilter{Has Filter?}
    
    HasFilter -->|Yes| FilterDocs[Filter document IDs<br/>keep only filtered IDs]
    HasFilter -->|No| UseAll[Use all document IDs]
    
    FilterDocs --> LoopStart[For each docId]
    UseAll --> LoopStart
    
    LoopStart --> LoopCheck{More docs?}
    LoopCheck -->|No| ExtractResults[Extract results<br/>from heap]
    LoopCheck -->|Yes| GetVector[JNI: getVector<br/>indexPtr, docId]
    
    GetVector --> JNICall1[JNI Call]
    JNICall1 --> ReturnVec[Return: byte vector]
    ReturnVec --> CalcDist[JNI: calculateHammingDistance<br/>query, vector]
    
    CalcDist --> JNICall2[JNI Call]
    JNICall2 --> ReturnDist[Return: int distance]
    ReturnDist --> AddHeap[Add to heap:<br/>docId, distance]
    
    AddHeap --> CheckSize{heap.size > k?}
    CheckSize -->|Yes| RemoveMax[heap.poll<br/>remove max distance]
    CheckSize -->|No| NextDoc[Next document]
    RemoveMax --> NextDoc
    NextDoc --> LoopCheck
    
    ExtractResults --> SortHeap[Sort heap by distance]
    SortHeap --> Return([Return KNNQueryResult[]])
    
    style Start fill:#e1f5e1
    style Return fill:#e1f5e1
    style JNICall1 fill:#ffcccc
    style JNICall2 fill:#ffcccc
    style GetVector fill:#ffe1e1
    style CalcDist fill:#ffe1e1
```

## Sequence Diagram - Java Layer with Multiple JNI Calls

```mermaid
sequenceDiagram
    participant User
    participant Java as Java ExactSearch
    participant JNI as JNI Layer
    participant Index as FAISS Index
    participant FAISS as FAISS Hamming
    
    User->>Java: exactSearch(query, k, filter)
    Java->>Java: Initialize PriorityQueue(k)
    Java->>Java: Get all document IDs
    
    alt Has Filter
        Java->>Java: Filter document IDs
    end
    
    loop For EACH document ID
        Note over Java,FAISS: ⚠️ JNI Call #1 - Get Vector
        Java->>JNI: getVector(indexPtr, docId)
        JNI->>Index: Get id_map, find internal ID
        Index-->>JNI: internalId
        JNI->>Index: Get vector from storage
        Index-->>JNI: byte[] vector
        JNI->>Java: Copy vector to Java
        Java-->>Java: byte[] vector
        
        Note over Java,FAISS: ⚠️ JNI Call #2 - Calculate Distance
        Java->>JNI: calculateHammingDistance(query, vector)
        JNI->>JNI: Copy query from Java
        JNI->>JNI: Copy vector from Java
        JNI->>FAISS: hamming(query, vector, d/8)
        FAISS-->>JNI: distance
        JNI-->>Java: int distance
        
        Java->>Java: heap.offer(docId, distance)
        alt heap.size > k
            Java->>Java: heap.poll() remove max
        end
    end
    
    Java->>Java: Sort heap results
    Java-->>User: Return KNNQueryResult[]
    
    Note over Java,FAISS: ⚠️ Problem: n × 2 JNI calls!
```

## Performance Problem Visualization

```mermaid
flowchart LR
    A[n documents] --> B[n × getVector calls]
    B --> C[n × JNI overhead]
    C --> D[n × memory copy]
    
    A --> E[n × calculateDistance calls]
    E --> F[n × JNI overhead]
    F --> G[n × memory copy]
    
    D --> H[Total: 2n JNI calls<br/>2n memory copies]
    G --> H
    
    H --> I[Very Slow!<br/>JNI overhead dominates]
    
    style I fill:#ffcccc
    style H fill:#ffe1e1
```

## JNI Overhead Breakdown

```mermaid
flowchart TD
    Start([Single Document]) --> Call1[JNI Call 1: getVector]
    
    Call1 --> Over1[JNI Overhead:<br/>- Method lookup<br/>- Stack frame setup<br/>- Type conversion]
    Over1 --> Copy1[Memory Copy:<br/>Java ← Native<br/>byte vector]
    
    Copy1 --> Call2[JNI Call 2: calculateDistance]
    Call2 --> Over2[JNI Overhead:<br/>- Method lookup<br/>- Stack frame setup<br/>- Type conversion]
    Over2 --> Copy2[Memory Copy:<br/>Native ← Java<br/>query + vector]
    
    Copy2 --> Compute[Actual Computation:<br/>hamming distance]
    Compute --> Copy3[Memory Copy:<br/>Java ← Native<br/>int result]
    
    Copy3 --> End([Total Time])
    
    style Over1 fill:#ffcccc
    style Over2 fill:#ffcccc
    style Copy1 fill:#ffe1e1
    style Copy2 fill:#ffe1e1
    style Copy3 fill:#ffe1e1
    style Compute fill:#e1f0ff
```

## Comparison: Java vs JNI Implementation

```mermaid
graph TB
    subgraph Java["Java Layer (Approach 3)"]
        J1[For each doc: n iterations]
        J2[JNI: getVector - n calls]
        J3[JNI: calcDistance - n calls]
        J4[Total: 2n JNI calls]
        J1 --> J2 --> J3 --> J4
    end
    
    subgraph JNI["JNI Layer (Approach 2)"]
        N1[Single JNI call]
        N2[Native loop: n iterations]
        N3[Direct storage access]
        N4[No memory copies]
        N1 --> N2 --> N3 --> N4
    end
    
    J4 -.->|Much Slower| Perf1[High JNI overhead<br/>High memory copy cost]
    N4 -.->|Much Faster| Perf2[Low overhead<br/>Zero copy]
    
    style Java fill:#ffcccc
    style JNI fill:#e1f5e1
    style Perf1 fill:#ffcccc
    style Perf2 fill:#e1f5e1
```

## Memory Copy Overhead

```mermaid
sequenceDiagram
    participant Java Heap
    participant JNI
    participant Native Heap
    
    Note over Java Heap,Native Heap: For EACH document (n times)
    
    rect rgb(255, 230, 230)
        Note over Java Heap,Native Heap: JNI Call 1: getVector
        Java Heap->>JNI: Request vector for docId
        JNI->>Native Heap: Allocate temp buffer
        Native Heap->>Native Heap: Copy vector data
        Native Heap->>JNI: Vector in native buffer
        JNI->>Java Heap: Copy to Java byte[]
        Note over Java Heap: Vector now in Java heap
    end
    
    rect rgb(255, 230, 230)
        Note over Java Heap,Native Heap: JNI Call 2: calculateDistance
        Java Heap->>JNI: Pass query + vector
        JNI->>Native Heap: Copy query to native
        JNI->>Native Heap: Copy vector to native
        Native Heap->>Native Heap: Compute hamming
        Native Heap->>JNI: Distance result
        JNI->>Java Heap: Return int
    end
    
    Note over Java Heap,Native Heap: Total: 4 memory copies per document!
```

## Performance Metrics Comparison

```mermaid
graph TD
    A[Performance Metrics] --> B[Approach 2: JNI Layer]
    A --> C[Approach 3: Java Layer]
    
    B --> B1[JNI Calls: 1]
    B --> B2[Memory Copies: 0]
    B --> B3[Overhead: Low]
    B --> B4[Time: O n × d/8]
    
    C --> C1[JNI Calls: 2n]
    C --> C2[Memory Copies: 4n]
    C --> C3[Overhead: Very High]
    C --> C4[Time: O n × JNI_overhead]
    
    B1 --> Good[✅ Efficient]
    B2 --> Good
    B3 --> Good
    B4 --> Good
    
    C1 --> Bad[❌ Inefficient]
    C2 --> Bad
    C3 --> Bad
    C4 --> Bad
    
    style Good fill:#e1f5e1
    style Bad fill:#ffcccc
    style B fill:#e1f5e1
    style C fill:#ffcccc
```

## Why Approach 3 is Not Recommended

```mermaid
flowchart TD
    Start[Approach 3: Java Layer] --> Problem1[Problem 1:<br/>2n JNI calls]
    Problem1 --> Impact1[Each call has overhead:<br/>~100-1000 ns]
    
    Start --> Problem2[Problem 2:<br/>4n memory copies]
    Problem2 --> Impact2[Copy overhead:<br/>~d/8 bytes × n]
    
    Start --> Problem3[Problem 3:<br/>GC pressure]
    Problem3 --> Impact3[n temporary byte arrays<br/>created and discarded]
    
    Start --> Problem4[Problem 4:<br/>No optimization possible]
    Problem4 --> Impact4[Cannot batch operations<br/>Cannot use SIMD]
    
    Impact1 --> Total[Total Overhead:<br/>Dominates actual computation]
    Impact2 --> Total
    Impact3 --> Total
    Impact4 --> Total
    
    Total --> Conclusion[❌ Not Recommended<br/>Use Approach 2 instead]
    
    style Start fill:#ffcccc
    style Conclusion fill:#ffcccc
    style Total fill:#ffe1e1
```

## Recommended Alternative

```mermaid
flowchart LR
    A[Need Exact Search?] --> B{Where to implement?}
    
    B -->|❌ Java Layer| C[Approach 3<br/>Multiple JNI calls<br/>High overhead]
    B -->|✅ JNI Layer| D[Approach 2<br/>Single JNI call<br/>Low overhead]
    
    C --> E[Performance: Poor<br/>Scalability: Poor<br/>Complexity: Low]
    D --> F[Performance: Good<br/>Scalability: Good<br/>Complexity: Medium]
    
    E --> G[Use only for:<br/>- Prototyping<br/>- Very small datasets]
    F --> H[Use for:<br/>- Production<br/>- Large datasets<br/>- Performance critical]
    
    style C fill:#ffcccc
    style D fill:#e1f5e1
    style E fill:#ffcccc
    style F fill:#e1f5e1
    style G fill:#ffe1e1
    style H fill:#e1f5e1
```
