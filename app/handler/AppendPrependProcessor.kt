package app.handler

import app.allocator.MemoryAllocator
import app.allocator.MemoryBlock
import app.core.CommandOpCodes
import app.core.ErrorCode
import app.core.Event
import app.dashtable.DashTable
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators

class AppendPrependProcessor(
  private val dashTable: DashTable,
  private val memoryAllocator: MemoryAllocator
): BaseProcessor() {
  @Suppress("DuplicatedCode")
  override fun process(event: Event, command: CommandOpCodes) {
    if (Validators.hasExtras(event) || !Validators.hasKey(event) || !Validators.hasValue(event)) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.InvalidArguments)
      event.reply(response)
      return
    }

    val key = decodeKey(event.body.key!!)
    val cacheEntry = dashTable.get(key)
    if (cacheEntry == null) {
      if (Commands.isQuietCommand(command)) {
        event.done()
      } else {
        val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyNotFound)
        event.reply(response)
      }
      return
    }


    val currentCas = cacheEntry.cas
    if (event.header.cas != 0L && event.header.cas != currentCas) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyExists)
      event.reply(response)
      return
    }

    val existingValue = cacheEntry.value!!
    val appendValue = MemoryBlock(
      event.body.value!!,
      0
    )
    val newValue = when (command) {
      CommandOpCodes.APPEND,
      CommandOpCodes.APPENDQ -> concat(existingValue, appendValue)
      CommandOpCodes.PREPEND,
      CommandOpCodes.PREPENDQ -> concat(appendValue, existingValue)
      else -> existingValue // This case should never happen
    }

    val now = System.currentTimeMillis()
    cacheEntry.value = newValue
    cacheEntry.cas = now
    memoryAllocator.freeBlock(existingValue)
    if (Commands.isQuietCommand(command)) {
      event.done()
      return
    }

    val response = Responses.makeResponse(
      event.responseBuffer,
      event.header,
      now,
      null,
      null,
      null
    )
    event.reply(response)
  }

  private fun concat(b1: MemoryBlock, b2: MemoryBlock): MemoryBlock {
    val block = memoryAllocator.allocateBlock(b1.buffer.remaining() + b2.buffer.remaining())
    block.buffer.put(b1.buffer.duplicate())
    block.buffer.put(b2.buffer.duplicate())
    block.buffer.flip()
    return block
  }
}