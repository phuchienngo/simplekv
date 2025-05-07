# Simple in-memory key-value database

## How to run
- Install [Bazelisk](https://bazel.build/install/bazelisk)
- Run: `bazel run //app:main_app -- -p 11211 -n simplekv -w 2 -s 4`
    - p: port number
    - n: server name
    - w: number of worker threads
    - s: number of selector threads
## Features
- [MemCached Binary Protocol](https://docs.memcached.org/protocols/binary/) support
- I/O Multiplexing
- Key-based data partitioning:
  - Keys hashed to specific worker threads
  - Enables lock-free parallel processing
- LMAX Disruptor for efficient thread communication:
  - Lock-free ring buffer design
  - High-throughput data transfer between selectors and workers
- Graceful shutdown
- Swiss table data structure
- Bazel build system for simplified development environment
- Commands:
    - NO_OP, VERSION
    - GET, GETK, GETKQ, GETQ, 
    - SET, SETQ, ADD, ADDQ, REPLACE, REPLACEQ
    - DELETE, DELETEQ
    - INCREMENT, INCREMENTQ, DECREMENT, DECREMENTQ
    - APPEND, APPENDQ, PREPEND, PREPENDQ
## Todo
- Expiration and eviction
- Write-ahead log
- Lightweight-locking for batched operations