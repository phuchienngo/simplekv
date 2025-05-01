package app.handler

import app.core.CommandOpCodes
import app.core.Event
import app.core.ResponseStatus
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators

interface AppendPrependHandler: BaseHandler {
  @Suppress("DuplicatedCode")
  fun processAppendPrependCommand(event: Event, command: CommandOpCodes) {
    if (Validators.hasExtras(event) || !Validators.hasKey(event) || !Validators.hasValue(event)) {
      val response = Responses.makeError(event.header, ResponseStatus.InvalidArguments)
      event.reply(response)
      return
    }

    val key = decodeKey(event.body.key!!)
    if (!valueMap.containsKey(key)) {
      if (Commands.isQuietCommand(command)) {
        event.done()
      } else {
        val response = Responses.makeError(event.header, ResponseStatus.KeyNotFound)
        event.reply(response)
      }
      return
    }

    val currentCas = casMap[key]
    if (event.header.cas != 0L && event.header.cas != currentCas) {
      val response = Responses.makeError(event.header, ResponseStatus.KeyExists)
      event.reply(response)
      return
    }

    val existingValue = valueMap[key]!!
    val newValue = when (command) {
      CommandOpCodes.APPEND,
      CommandOpCodes.APPENDQ -> existingValue + event.body.value!!
      CommandOpCodes.PREPEND,
      CommandOpCodes.PREPENDQ -> event.body.value!! + existingValue
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
      event.header,
      now,
      null,
      null,
      null
    )
    event.reply(response)
  }
}