package app.handler

import app.core.Event
import app.core.ResponseStatus
import app.datastructure.SwissMap
import app.utils.Responses

interface BaseHandler {
  val valueMap: SwissMap<String, ByteArray>
  val extrasMap: SwissMap<String, ByteArray>
  val casMap: SwissMap<String, Long>

  fun decodeKey(buffer: ByteArray): String {
    return String(buffer, Charsets.US_ASCII)
  }

  fun processUnknownCommand(event: Event) {
    val response = Responses.makeError(event.header, ResponseStatus.UnknownCommand)
    event.reply(response)
  }
}