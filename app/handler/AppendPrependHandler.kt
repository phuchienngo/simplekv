package app.handler

import app.core.CommandOpCodes
import app.core.ErrorCode
import app.core.Event
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators
import java.nio.ByteBuffer

interface AppendPrependHandler: BaseHandler {
  @Suppress("DuplicatedCode")
  fun processAppendPrependCommand(event: Event, command: CommandOpCodes) {
    if (Validators.hasExtras(event) || !Validators.hasKey(event) || !Validators.hasValue(event)) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.InvalidArguments)
      event.reply(response)
      return
    }

    val key = decodeKey(event.body.key!!)
    if (!valueMap.containsKey(key)) {
      if (Commands.isQuietCommand(command)) {
        event.done()
      } else {
        val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyNotFound)
        event.reply(response)
      }
      return
    }

    val currentCas = casMap[key]
    if (event.header.cas != 0L && event.header.cas != currentCas) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyExists)
      event.reply(response)
      return
    }

    val existingValue = valueMap[key]!!
    val newValue = when (command) {
      CommandOpCodes.APPEND,
      CommandOpCodes.APPENDQ -> concat(existingValue, event.body.value!!)
      CommandOpCodes.PREPEND,
      CommandOpCodes.PREPENDQ -> concat(event.body.value!!, existingValue)
      else -> existingValue // This case should never happen
    }

    val now = System.currentTimeMillis()
    valueMap[key] = newValue
    casMap[key] = now
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

  private fun concat(b1: ByteBuffer, b2: ByteBuffer): ByteBuffer {
    val result: ByteBuffer = ByteBuffer.allocate(b1.remaining() + b2.remaining())
    result.put(b1.duplicate())
    result.put(b2.duplicate())
    result.flip()
    return result
  }
}