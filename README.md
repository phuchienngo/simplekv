# Simple in-memory key-value database
## How to run
- Install [Bazelisk](https://bazel.build/install/bazelisk)
- Run: `bazel run //app:main_app -- -p 11211`
### Configuration Options
- `-p`, `--port`: Port number (required)
- `-n`, `--name`: Server name (default: "simplekv")
- `-w`, `--worker_num`: Number of worker threads (default: 1)
- `-s`, `--selector_num`: Number of selector threads (default: 1)
- `-minbs`, `--min_block_size`: Minimum block size in bytes for buddy allocator (default: 256)
- `-maxbs`, `--max_block_size`: Maximum block size in bytes for buddy allocator (default: 16777216)
- `-ss`, `--segment_size`: Size of segments for DashTable (default: 60)
- `-rs`, `--regular_size`: Regular size for DashTable segments (default: 54)
- `-sls`, `--slot_size`: Size of slots in buckets in DashTable segments (default: 14)
## Features
- [MemCached Binary Protocol](https://docs.memcached.org/protocols/binary/) support
- Expiration
- I/O Multiplexing
- Key-based data partitioning with Consistent hashing for lock-free parallel processing
- LMAX Disruptor for efficient thread communication
- Buddy Allocation algorithm for buffer management:
  - Leveraging DirectByteBuffer for zero-copy I/O
  - Reducing both GC overhead and OS overhead on frequent dynamic memory allocation
  - Using array-based full binary tree for efficient usage management
  - O(log₂(n)) allocation and deallocation complexity where n = maxBlockSize/minBlockSize
- Graceful shutdown
- [DashTable](https://github.com/dragonflydb/dragonfly/blob/main/docs/dashtable.md) data structure
  - Space-efficient trie data structure using contiguous arrays
  - Eliminating full-table rehashing when growing with [Extendible hashing](https://en.wikipedia.org/wiki/Extendible_hashing)
  - SIMD acceleration for key lookup and comparison
- Bazel build system for simplified development environment
- Commands:
    - NO_OP, VERSION
    - GET, GETK, GETKQ, GETQ, 
    - SET, SETQ, ADD, ADDQ, REPLACE, REPLACEQ
    - DELETE, DELETEQ
    - INCREMENT, INCREMENTQ, DECREMENT, DECREMENTQ
    - APPEND, APPENDQ, PREPEND, PREPENDQ
## Benchmark
### Hardware Specification
- VM in GCP within the same region
- Server: 
  - Machine Type: `c4d-standard-32`
  - vCPUs: 32
  - Memory: 124GB
  - Network: 23Gbps
- Client:
  - Machine Type: `c3-standard-22`
  - vCPUs: 22
  - Memory: 88GB
  - Network: 23Gbps
### Server Configuration
- JVM: OpenJDK 21
- Test command:
  ```bash
      bazel run //app:main_app \
      --jvmopt="-Xms4G" \
      --jvmopt="-Xmx16G" \
      --jvmopt="-XX:+UseZGC" \
      --jvmopt="-XX:+ZGenerational" \
      --jvmopt="-XX:InitiatingHeapOccupancyPercent=70" \
      --jvmopt="-XX:SoftMaxHeapSize=12G" \
      --jvmopt="-XX:MaxDirectMemorySize=120G" \
      -- -p 11211 -n simplekv -w 4 -s 8
### Client Configuration
- Tool: [memtier_benchmark](https://github.com/RedisLabs/memtier_benchmark)
- Test command:
  - SET: `memtier_benchmark -h 10.128.0.7 -p 11211 --protocol=memcache_binary -c 20 --test-time 60 -t 8 -d 256 --distinct-client-seed --ratio 1:0`
  - GET: `memtier_benchmark -h 10.128.0.7 -p 11211 --protocol=memcache_binary -c 20 --test-time 60 -t 8 -d 256 --distinct-client-seed --ratio 0:1`
  - Mixed: `memtier_benchmark -h 10.128.0.7 -p 11211 --protocol=memcache_binary -c 20 --test-time 60 -t 8 -d 256 --distinct-client-seed --ratio 3:10`
### Results
| Operation          | Throughput (ops/sec)      | P99 Latency (ms) |
|--------------------|---------------------------|------------------|
| SET                | 807,184                   | 0.57             |
| GET                | 1,003,849                 | 0.25             |
| Mixed(1 SET/3 GET) | 974,490 (224,885/749,604) | 0.27 (0.28/0.27) |
## Todo
- Eviction
- Write-ahead log