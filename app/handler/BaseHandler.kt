package app.handler

import app.allocator.BuddyAllocator
import app.allocator.MemoryBlock
import app.core.Event
import app.core.ErrorCode
import app.datastructure.SwissMap
import app.utils.Responses
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

interface BaseHandler {
  val valueMap: SwissMap<String, MemoryBlock>
  val extrasMap: SwissMap<String, MemoryBlock>
  val casMap: SwissMap<String, Long>
  val allocators: MutableList<BuddyAllocator>
  val minBlockSize: Int
  val maxBlockSize: Int

  fun decodeKey(buffer: ByteBuffer): String {
    return StandardCharsets
      .US_ASCII
      .decode(buffer.duplicate())
      .toString()
  }

  fun processUnknownCommand(event: Event) {
    val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.UnknownCommand)
    event.reply(response)
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