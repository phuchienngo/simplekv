package app.allocator

import java.nio.ByteBuffer

data class MemoryBlock(
  val buffer: ByteBuffer,
  val offset: Int,
  val allocator: BuddyAllocator? = null
)