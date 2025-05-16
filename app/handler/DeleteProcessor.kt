package app.handler

import app.allocator.MemoryAllocator
import app.core.CommandOpCodes
import app.core.Event
import app.core.ErrorCode
import app.dashtable.KeyValueStore
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators

class DeleteProcessor(
  private val keyValueStore: KeyValueStore,
  private val memoryAllocator: MemoryAllocator
): BaseProcessor() {
  @Suppress("DuplicatedCode")
  override fun process(event: Event, command: CommandOpCodes) {
    if (Validators.hasExtras(event) || Validators.hasValue(event) || !Validators.hasKey(event)) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.InvalidArguments)
      event.reply(response)
      return
    }

    val key = decodeKey(event.body.key!!)
    if (!keyValueStore.valueMap.containsKey(key)) {
      if (Commands.isQuietCommand(command)) {
        event.done()
      } else {
        val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyNotFound)
        event.reply(response)
      }
      return
    }

    val currentCas = keyValueStore.casMap.get(key)
    val requestCas = event.header.cas
    if (requestCas != 0L && requestCas != currentCas) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyExists)
      event.reply(response)
      return
    }

    keyValueStore.valueMap.remove(key)?.let(memoryAllocator::freeBlock)
    keyValueStore.extrasMap.remove(key)?.let(memoryAllocator::freeBlock)
    keyValueStore.casMap.remove(key)
    if (Commands.isQuietCommand(command)) {
      event.done()
      return
    }

    val response = Responses.makeResponse(
      event.responseBuffer,
      event.header,
      0L,
      null,
      null,
      null
    )
    event.reply(response)
  }
}