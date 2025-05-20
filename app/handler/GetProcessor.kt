package app.handler

import app.core.CommandOpCodes
import app.core.Event
import app.core.ErrorCode
import app.dashtable.DashTable
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators

class GetProcessor(
  private val dashTable: DashTable<CacheEntry>
): BaseProcessor() {
  @Suppress("DuplicatedCode")
  override fun process(event: Event, command: CommandOpCodes) {
    if (Validators.hasExtras(event) || !Validators.hasKey(event) || Validators.hasValue(event) ) {
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

    val value = cacheEntry.value
    val extras = cacheEntry.extra
    val cas = cacheEntry.cas ?: 0L
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