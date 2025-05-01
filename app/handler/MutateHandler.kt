package app.handler

import app.core.CommandOpCodes
import app.core.Event
import app.core.ErrorCode
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators

interface MutateHandler: BaseHandler {
  fun processMutateCommand(event: Event, command: CommandOpCodes) {
    if (!Validators.hasExtras(event) || !Validators.hasKey(event)) {
      val response = Responses.makeError(event.header, ErrorCode.InvalidArguments)
      event.reply(response)
      return
    }
    val key = decodeKey(event.body.key!!)
    val currentCas = casMap.get(key)
    val requestCas = event.header.cas
    if (requestCas != 0L && requestCas != currentCas) {
      val response = Responses.makeError(event.header, ErrorCode.KeyExists)
      event.reply(response)
      return
    }
    val extras = event.body.extras
    val value = event.body.value ?: ByteArray(0)
    val now = System.currentTimeMillis()

    when (command) {
      CommandOpCodes.SET, CommandOpCodes.SETQ -> {
        addOrUpdateAndReply(command, event, key, value, extras, now)
      }
      CommandOpCodes.ADD, CommandOpCodes.ADDQ -> {
        if (valueMap.containsKey(key)) {
          val response = Responses.makeError(event.header, ErrorCode.KeyExists)
          event.reply(response)
          return
        }
        addOrUpdateAndReply(command, event, key, value, extras, now)
      }
      CommandOpCodes.REPLACE, CommandOpCodes.REPLACEQ -> {
        if (!valueMap.containsKey(key)) {
          val response = Responses.makeError(event.header, ErrorCode.KeyNotFound)
          event.reply(response)
          return
        }
        addOrUpdateAndReply(command, event, key, value, extras, now)
      }
      else -> {} // never reached
    }
  }

  private fun addOrUpdateAndReply(command: CommandOpCodes, event: Event, key: String, value: ByteArray?, extras: ByteArray?, cas: Long) {
    valueMap[key] = value
    extrasMap[key] = extras
    casMap[key] = cas
    if (Commands.isQuietCommand(command)) {
      event.done()
    } else {
      val response = Responses.makeResponse(event.header, cas, null, null, null)
      event.reply(response)
    }
  }
}