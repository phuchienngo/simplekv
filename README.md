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
  - Machine Type: `c4-standard-16`
  - vCPUs: 16
  - Memory: 60GB
  - Network: 16Gbps
- Client:
  - Machine Type: `c3-highcpu-8`
  - vCPUs: 8
  - Memory: 16GB
  - Network: 10Gbps
### Server Configuration
- JVM: OpenJDK 21
- Workers: 2 threads
- Selectors: 4 threads
- Test command:
  ```bash
      bazel run //app:main_app \
      --jvmopt="-Xms4G" \
      --jvmopt="-Xmx16G" \
      --jvmopt="-XX:+UseZGC" \
      --jvmopt="-XX:+ZGenerational" \
      --jvmopt="-XX:InitiatingHeapOccupancyPercent=70" \
      --jvmopt="-XX:SoftMaxHeapSize=12G" \
      --jvmopt="-XX:MaxDirectMemorySize=32G" \
      -- -p 11211 -n simplekv -w 2 -s 4
### Client Configuration
- Tool: [memtier_benchmark](https://github.com/RedisLabs/memtier_benchmark)
- Test command:
  - SET: `memtier_benchmark -h 10.128.0.7 -p 11211 --protocol=memcache_binary -c 20 --test-time 60 -t 8 -d 256 --distinct-client-seed --ratio 1:0`
  - GET: `memtier_benchmark -h 10.128.0.7 -p 11211 --protocol=memcache_binary -c 20 --test-time 60 -t 8 -d 256 --distinct-client-seed --ratio 0:1`
  - Mixed: `memtier_benchmark -h 10.128.0.7 -p 11211 --protocol=memcache_binary -c 20 --test-time 60 -t 8 -d 256 --distinct-client-seed --ratio 3:10`
### Results
| Operation          | Throughput (ops/sec)      | P99 Latency (ms) |
|--------------------|---------------------------|------------------|
| SET                | 359,243                   | 1.19             |
| GET                | 728,575                   | 0.36             |
| Mixed(1 SET/3 GET) | 573,537 (132,358/441,179) | 0.72 (0.73/0.71) |
## Todo
- Eviction
- Write-ahead log