package app.handler

import app.allocator.MemoryAllocator
import app.config.Config
import app.core.Event
import app.dashtable.DashTable
import java.nio.charset.StandardCharsets

class MainHandler: Handler {
  private val config: Config
  private val notNullKeyProcessor: NotNullKeyProcessor
  private val nullKeyProcessor: NullKeyProcessor

  constructor(config: Config) {
    this.config = config
    val dashTable = DashTable<CacheEntry>(config.segmentSize, config.regularSize, config.slotSize)
    val memoryAllocator = MemoryAllocator(config.minBlockSize, config.maxBlockSize)
    val appendPrependProcessor = AppendPrependProcessor(dashTable, memoryAllocator)
    val deleteProcessor = DeleteProcessor(dashTable, memoryAllocator)
    val getProcessor = GetProcessor(dashTable)
    val incrementDecrementProcessor = IncrementDecrementProcessor(dashTable, memoryAllocator)
    val mutateProcessor = MutateProcessor(dashTable, memoryAllocator)
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