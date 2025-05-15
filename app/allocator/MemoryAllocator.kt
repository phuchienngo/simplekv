package app.allocator

import java.nio.ByteBuffer
import java.util.LinkedList

class MemoryAllocator {
  private val minBlockSize: Int
  private val maxBlockSize: Int
  private val allocators: MutableList<BuddyAllocator>

  constructor(minBlockSize: Int, maxBlockSize: Int) {
    this.minBlockSize = minBlockSize
    this.maxBlockSize = maxBlockSize
    this.allocators = LinkedList()
  }

  fun allocateBlock(size: Int): MemoryBlock {
    if (size > maxBlockSize) {
      // big enough, allocate directly
      return MemoryBlock(
        ByteBuffer.allocate(size),
        0,
        null
      )
    }
    for (allocator in allocators) {
      val block = allocator.allocate(size) ?: continue
      return block
    }
    val newAllocator = BuddyAllocator(minBlockSize, maxBlockSize)
    allocators.add(newAllocator)
    return newAllocator.allocate(size)!!
  }

  fun freeBlock(block: MemoryBlock) {
    block.allocator?.free(block)
  }
}