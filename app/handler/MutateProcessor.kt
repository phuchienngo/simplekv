package app.handler

import app.allocator.MemoryAllocator
import app.core.CommandOpCodes
import app.core.Event
import app.core.ErrorCode
import app.datastructure.KeyValueStore
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators
import java.nio.ByteBuffer

class MutateProcessor(
  private val keyValueStore: KeyValueStore,
  private val memoryAllocator: MemoryAllocator
): BaseProcessor() {
  override fun process(event: Event, command: CommandOpCodes) {
    if (!Validators.hasExtras(event) || !Validators.hasKey(event)) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.InvalidArguments)
      event.reply(response)
      return
    }
    val key = decodeKey(event.body.key!!)
    val currentCas = keyValueStore.casMap.get(key)
    val requestCas = event.header.cas
    if (requestCas != 0L && requestCas != currentCas) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyExists)
      event.reply(response)
      return
    }
    val extras = event.body.extras
    val value = event.body.value ?: ByteBuffer.allocate(0)
    val now = System.currentTimeMillis()

    when (command) {
      CommandOpCodes.SET, CommandOpCodes.SETQ -> {
        addOrUpdateAndReply(command, event, key, value, extras, now)
      }
      CommandOpCodes.ADD, CommandOpCodes.ADDQ -> {
        if (keyValueStore.valueMap.containsKey(key)) {
          val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyExists)
          event.reply(response)
          return
        }
        addOrUpdateAndReply(command, event, key, value, extras, now)
      }
      CommandOpCodes.REPLACE, CommandOpCodes.REPLACEQ -> {
        if (!keyValueStore.valueMap.containsKey(key)) {
          val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyNotFound)
          event.reply(response)
          return
        }
        addOrUpdateAndReply(command, event, key, value, extras, now)
      }
      else -> {} // never reached
    }
  }

  private fun addOrUpdateAndReply(command: CommandOpCodes, event: Event, key: String, value: ByteBuffer?, extras: ByteBuffer?, cas: Long) {
    keyValueStore.valueMap[key]?.let(memoryAllocator::freeBlock)
    keyValueStore.extrasMap[key]?.let(memoryAllocator::freeBlock)
    keyValueStore.valueMap[key] = value?.let {
      return@let memoryAllocator.allocateBlock(it.remaining()).apply {
        buffer.put(it.duplicate())
        buffer.flip()
      }
    }
    keyValueStore.extrasMap[key] = extras?.let {
      return@let memoryAllocator.allocateBlock(it.remaining()).apply {
        buffer.put(it.duplicate())
        buffer.flip()
      }
    }
    keyValueStore.casMap[key] = cas
    if (Commands.isQuietCommand(command)) {
      event.done()
    } else {
      val response = Responses.makeResponse(event.responseBuffer, event.header, cas, null, null, null)
      event.reply(response)
    }
  }
}