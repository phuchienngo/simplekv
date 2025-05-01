package app.handler

import app.core.CommandOpCodes
import app.core.Event
import app.core.ResponseStatus
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators

interface GetHandler: BaseHandler {
  fun processGetCommand(event: Event, command: CommandOpCodes) {
    if (Validators.hasExtras(event) || !Validators.hasKey(event) || Validators.hasValue(event) ) {
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

    val value = valueMap[key]
    val extras = extrasMap[key]
    val cas = casMap[key]!!
    val response = Responses.makeResponse(
      event.header,
      cas,
      extras,
      if (command == CommandOpCodes.GETK || command == CommandOpCodes.GETKQ) {
        event.body.key
      } else {
        null
      },
      value
    )
    event.reply(response)
  }
}