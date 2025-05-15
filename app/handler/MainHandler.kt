package app.handler

import app.allocator.MemoryAllocator
import app.core.Event
import app.datastructure.KeyValueStore
import java.nio.charset.StandardCharsets

class MainHandler: Handler {
  private val notNullKeyProcessor: NotNullKeyProcessor
  private val nullKeyProcessor: NullKeyProcessor

  constructor() {
    val keyValueStore = KeyValueStore()
    val memoryAllocator = MemoryAllocator(256, 16777216)
    val appendPrependProcessor = AppendPrependProcessor(keyValueStore, memoryAllocator)
    val deleteProcessor = DeleteProcessor(keyValueStore, memoryAllocator)
    val getProcessor = GetProcessor(keyValueStore)
    val incrementDecrementProcessor = IncrementDecrementProcessor(keyValueStore, memoryAllocator)
    val mutateProcessor = MutateProcessor(keyValueStore, memoryAllocator)
    notNullKeyProcessor = NotNullKeyProcessor(
      appendPrependProcessor,
      deleteProcessor,
      getProcessor,
      incrementDecrementProcessor,
      mutateProcessor
    )
    nullKeyProcessor = NullKeyProcessor(StandardCharsets.US_ASCII.encode("1.0.0"))
  }

  override fun handle(event: Event) {
    if (event.body.key == null) {
      nullKeyProcessor.handleNullKeyRequest(event)
    } else {
      notNullKeyProcessor.handleNotNullKeyRequest(event)
    }
  }
}