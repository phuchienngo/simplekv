package app.handler

import app.core.CommandOpCodes
import app.core.Event
import app.core.ErrorCode
import app.datastructure.KeyValueStore
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators

class GetProcessor(
  private val keyValueStore: KeyValueStore
): BaseProcessor() {
  @Suppress("DuplicatedCode")
  override fun process(event: Event, command: CommandOpCodes) {
    if (Validators.hasExtras(event) || !Validators.hasKey(event) || Validators.hasValue(event) ) {
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

    val value = keyValueStore.valueMap[key]
    val extras = keyValueStore.extrasMap[key]
    val cas = keyValueStore.casMap[key]!!
    val response = Responses.makeResponse(
      event.responseBuffer,
      event.header,
      cas,
      extras?.buffer?.duplicate(),
      if (command == CommandOpCodes.GETK) {
        event.body.key?.duplicate()
      } else {
        null
      },
      if (command == CommandOpCodes.GET || command == CommandOpCodes.GETK) {
        value?.buffer?.duplicate()
      } else {
        null
      }
    )
    event.reply(response)
  }
}