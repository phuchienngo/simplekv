package app.handler

import app.allocator.MemoryBlock
import app.core.CommandOpCodes
import app.core.Event
import app.core.ErrorCode
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

interface IncrementDecrementHandler: BaseHandler {
  fun processIncrementDecrementCommand(event: Event, command: CommandOpCodes) {
    if (!Validators.hasExtras(event) || !Validators.hasKey(event) || Validators.hasValue(event)) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.InvalidArguments)
      event.reply(response)
      return
    }

    val extras = event.body.extras!!
    val delta = extras.getLong().toULong()
    val initialValue = extras.getLong().toULong()
    val expiration = extras.getInt()
    extras.position(0)

    val key = decodeKey(event.body.key!!)
    val isKeyExists = valueMap.containsKey(key)

    if (isKeyExists && event.header.cas != 0L && event.header.cas != casMap[key]) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyExists)
      event.reply(response)
      return
    }

    val now = System.currentTimeMillis()
    if (!isKeyExists) {
      if (expiration == 0xFFFFFFFF.toInt()) {
        val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyNotFound)
        event.reply(response)
        return
      }

      applyAndResponse(key, initialValue, now, event, command)
      return
    }

    val currentValue = parseStringValue(valueMap[key]!!.buffer)
    val newValue = when (command) {
      CommandOpCodes.INCREMENT,
      CommandOpCodes.INCREMENTQ -> currentValue + delta
      else -> if (delta > currentValue) 0UL else currentValue - delta
    }
    applyAndResponse(key, newValue, now, event, command)
  }

  private fun parseStringValue(buffer: ByteBuffer): ULong {
    try {
      val value = StandardCharsets
        .US_ASCII
        .decode(buffer.duplicate())
        .toString()
      if (isAllNumber(value)) {
        return value.toULong()
      }
      return 0UL
    } catch (_: Exception) {
      return 0UL
    }
  }

  private fun applyAndResponse(key: String, newValue: ULong, cas: Long, event: Event, command: CommandOpCodes) {
    extrasMap[key]?.let(this::freeBlock)
    valueMap[key]?.let(this::freeBlock)
    valueMap[key] = createCounterValueBuffer(newValue)
    casMap[key] = cas
    extrasMap[key] = event.body.extras?.let {
      return@let allocateBlock(it.remaining()).apply {
        buffer.put(it.duplicate())
        buffer.flip()
      }
    }
    if (Commands.isQuietCommand(command)) {
      event.done()
    } else {
      val response = Responses.makeResponse(
        event.responseBuffer,
        event.header,
        cas,
        null,
        null,
        ByteBuffer.allocate(8)
          .putLong(newValue.toLong())
          .flip()
      )
      event.reply(response)
    }
  }

  private fun createCounterValueBuffer(value: ULong): MemoryBlock {
    return StandardCharsets.US_ASCII.encode("$value").let {
      val block = allocateBlock(it.remaining())
      block.buffer.put(it.duplicate())
      block.buffer.flip()
      return@let block
    }
  }

  private fun isAllNumber(value: String): Boolean {
    if (value.isEmpty()) {
      return false
    }
    return value.all(Char::isDigit)
  }
}