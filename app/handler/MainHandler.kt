package app.handler

import app.allocator.BuddyAllocator
import app.allocator.MemoryBlock
import app.core.Event
import app.datastructure.SwissMap
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.LinkedList

class MainHandler(): NullKeyHandler, NotNullKeyHandler, Handler {
  override val valueMap = SwissMap<String, MemoryBlock>(10_000_000, 0.75f)
  override val extrasMap = SwissMap<String, MemoryBlock>(10_000_000, 0.75f)
  override val casMap = SwissMap<String, Long>(10_000_000, 0.75f)
  override val version: ByteBuffer = StandardCharsets.US_ASCII.encode("1.0.0")
  override val allocators: MutableList<BuddyAllocator> = LinkedList()
  override val minBlockSize: Int = 256
  override val maxBlockSize: Int = 16777216

  override fun handle(event: Event) {
    if (event.body.key == null) {
      handleNullKeyRequest(event)
    } else {
      handleNotNullKeyRequest(event)
    }
  }
}