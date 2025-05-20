package app.handler

import app.allocator.MemoryBlock

data class CacheEntry(
  var value: MemoryBlock?,
  var extra: MemoryBlock?,
  var cas: Long?
)