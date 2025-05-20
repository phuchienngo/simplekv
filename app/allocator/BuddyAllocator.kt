package app.allocator

import java.nio.ByteBuffer
import kotlin.math.log2
import kotlin.math.max

class BuddyAllocator {
  private val minBlockSize: Int
  private val maxBlockSize: Int
  private val blockCount: Int
  private val maxHeight: Int
  private val buffer: ByteBuffer
  private val blockUsage: Array<BlockStatus>

  constructor(minBlockSize: Int, maxBlockSize: Int) {
    this.minBlockSize = minBlockSize
    this.maxBlockSize = maxBlockSize
    this.maxHeight = log2((maxBlockSize / minBlockSize).toDouble()).toInt()
    this.blockCount = (1 shl (maxHeight + 1)) - 1
    this.buffer = ByteBuffer.allocateDirect(blockCount * minBlockSize) // full binary tree
    this.blockUsage = Array(blockCount) { BlockStatus.FREE }
  }

  fun allocate(size: Int): MemoryBlock? {
    if (size > maxBlockSize) {
      return null
    }

    val actualAllocatedSize = max(minBlockSize, nextPowerOfTwo(size))
    return internalAllocate(0, size, maxBlockSize, actualAllocatedSize)
  }

  private fun internalAllocate(blockIndex: Int, requestSize: Int, currentSize: Int, actualSize: Int): MemoryBlock? {
    if (blockIndex >= blockCount || blockUsage[blockIndex] == BlockStatus.ALLOCATED) {
      return null
    }
    if (currentSize == actualSize) {
      if (blockUsage[blockIndex] == BlockStatus.PARTIAL) {
        return null
      }
      blockUsage[blockIndex] = BlockStatus.ALLOCATED
      val offset = blockIndex * minBlockSize
      val slice = buffer.duplicate()
        .position(offset)
        .limit(offset + requestSize)
        .slice()
      return MemoryBlock(slice, offset, this)
    }
    val nextSize = currentSize shr 1
    val firstSibling = 2 * blockIndex + 1
    val secondSibling = 2 * blockIndex + 2
    val allocatedBlock = internalAllocate(firstSibling, requestSize, nextSize, actualSize)
      ?: internalAllocate(secondSibling, requestSize, nextSize, actualSize)

    if (allocatedBlock != null) {
      if (
        blockUsage[firstSibling] == BlockStatus.ALLOCATED &&
        blockUsage[secondSibling] == BlockStatus.ALLOCATED
      ) {
        blockUsage[blockIndex] = BlockStatus.ALLOCATED
      } else {
        blockUsage[blockIndex] = BlockStatus.PARTIAL
      }
      return allocatedBlock
    }

    return null
  }

  fun free(block: MemoryBlock) {
    var blockIndex = block.offset / minBlockSize
    blockUsage[blockIndex] = BlockStatus.FREE
    blockIndex = (blockIndex - 1) shr 1
    while (blockIndex >= 0) {
      val firstSibling = 2 * blockIndex + 1
      val secondSibling = 2 * blockIndex + 2
      blockUsage[blockIndex] = when {
        blockUsage[firstSibling] == BlockStatus.FREE
            && blockUsage[secondSibling] == BlockStatus.FREE -> BlockStatus.FREE
        blockUsage[firstSibling] == BlockStatus.ALLOCATED
            && blockUsage[secondSibling] == BlockStatus.ALLOCATED -> BlockStatus.ALLOCATED
        else -> BlockStatus.PARTIAL
      }
      blockIndex = (blockIndex - 1) shr 1
    }
  }

  private fun nextPowerOfTwo(n: Int): Int {
    var power = n - 1
    power = power or (power shr 1)
    power = power or (power shr 2)
    power = power or (power shr 4)
    power = power or (power shr 8)
    power = power or (power shr 16)
    return power + 1
  }

  private enum class BlockStatus {
    FREE,
    ALLOCATED,
    PARTIAL
  }
}