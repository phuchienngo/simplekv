package app.handler

import app.core.CommandOpCodes
import app.core.Event
import app.core.ResponseStatus
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators
import java.nio.ByteBuffer

interface IncrementDecrementHandler: BaseHandler {
  fun processIncrementDecrementCommand(event: Event, command: CommandOpCodes) {
    if (!Validators.hasExtras(event) || !Validators.hasKey(event) || Validators.hasValue(event)) {
      val response = Responses.makeError(event.header, ResponseStatus.InvalidArguments)
      event.reply(response)
      return
    }

    val extras = ByteBuffer.wrap(event.message.body.extras!!)
    val delta = extras.getLong().toULong()
    val initialValue = extras.getLong().toULong()
    val expiration = extras.getInt()
    extras.position(0)

    val key = decodeKey(event.body.key!!)
    val isKeyExists = valueMap.containsKey(key)

    if (isKeyExists && event.header.cas != 0L && event.header.cas != casMap[key]) {
      val response = Responses.makeError(event.header, ResponseStatus.KeyExists)
      event.reply(response)
      return
    }

    val now = System.currentTimeMillis()
    if (!isKeyExists) {
      if (expiration == 0xFFFFFFFF.toInt()) {
        val response = Responses.makeError(event.header, ResponseStatus.KeyNotFound)
        event.reply(response)
        return
      }

      applyAndResponse(key, initialValue, now, event, command)
      return
    }

    val currentValue = parseStringValue(valueMap[key]!!)
    val newValue = when (command) {
      CommandOpCodes.INCREMENT,
      CommandOpCodes.INCREMENTQ -> currentValue + delta
      else -> if (delta > currentValue) 0UL else currentValue - delta
    }
    applyAndResponse(key, newValue, now, event, command)
  }

  private fun parseStringValue(buffer: ByteArray): ULong {
    try {
      val value = String(buffer, Charsets.US_ASCII)
      if (isAllNumber(value)) {
        return value.toULong()
      }
      return 0UL
    } catch (_: Exception) {
      return 0UL
    }
  }

  private fun applyAndResponse(key: String, newValue: ULong, cas: Long, event: Event, command: CommandOpCodes) {
    valueMap[key] = createCounterValueBuffer(newValue)
    casMap[key] = cas
    extrasMap[key] = event.message.body.extras!!
    if (Commands.isQuietCommand(command)) {
      event.done()
    } else {
      val response = Responses.makeResponse(
        event.header,
        cas,
        null,
        null,
        ByteBuffer.wrap(valueMap[key])
      )
      event.reply(response)
    }
  }

  private fun createCounterValueBuffer(value: ULong): ByteArray {
    return "$value".toByteArray(Charsets.US_ASCII)
  }

  private fun isAllNumber(value: String): Boolean {
    if (value.isEmpty()) {
      return false
    }
    return value.all(Char::isDigit)
  }
}