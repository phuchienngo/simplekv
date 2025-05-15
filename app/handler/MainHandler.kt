package app.handler

import app.allocator.MemoryAllocator
import app.config.Config
import app.core.Event
import app.datastructure.KeyValueStore
import java.nio.charset.StandardCharsets

class MainHandler: Handler {
  private val config: Config
  private val notNullKeyProcessor: NotNullKeyProcessor
  private val nullKeyProcessor: NullKeyProcessor

  constructor(config: Config) {
    this.config = config
    val keyValueStore = KeyValueStore(config.initialCapacity, config.loadFactor)
    val memoryAllocator = MemoryAllocator(config.minBlockSize, config.maxBlockSize)
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