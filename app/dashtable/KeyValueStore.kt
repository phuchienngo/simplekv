package app.dashtable

import app.allocator.MemoryBlock

class KeyValueStore(segmentSize: Int = 60, regularSize: Int = 54, slotSize: Int = 14) {
  val valueMap = DashTable<MemoryBlock?>(segmentSize, regularSize, slotSize)
  val extrasMap = DashTable<MemoryBlock?>(segmentSize, regularSize, slotSize)
  val casMap = DashTable<Long?>(segmentSize, regularSize, slotSize)
}