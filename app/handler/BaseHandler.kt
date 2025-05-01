package app.handler

import app.core.Event
import app.core.ResponseStatus
import app.datastructure.SwissMap
import app.utils.Responses
import java.nio.ByteBuffer
import java.nio.charset.CharsetDecoder

interface BaseHandler {
  val decoder: CharsetDecoder
  val valueMap: SwissMap<String, ByteBuffer>
  val extrasMap: SwissMap<String, ByteBuffer>
  val casMap: SwissMap<String, Long>

  fun decodeKey(buffer: ByteBuffer): String {
    decoder.reset()
    val charBuffer = decoder.decode(buffer)
    buffer.position(0)
    return charBuffer.toString()
  }

  fun processUnknownCommand(event: Event) {
    val response = Responses.makeError(event.header, ResponseStatus.UnknownCommand)
    event.reply(response)
  }
}