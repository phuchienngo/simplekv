package app.datastructure

import app.allocator.MemoryBlock

class KeyValueStore(initialCapacity: Int = 10_000_000, loadFactor: Float = 0.75f) {
  val valueMap = SwissMap<String, MemoryBlock>(initialCapacity, loadFactor)
  val extrasMap = SwissMap<String, MemoryBlock>(initialCapacity, loadFactor)
  val casMap = SwissMap<String, Long>(initialCapacity, loadFactor)
}