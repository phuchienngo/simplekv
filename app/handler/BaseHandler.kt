package app.handler

import app.core.Event
import app.core.ErrorCode
import app.datastructure.SwissMap
import app.utils.Responses
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

interface BaseHandler {
  val valueMap: SwissMap<String, ByteBuffer>
  val extrasMap: SwissMap<String, ByteBuffer>
  val casMap: SwissMap<String, Long>

  fun decodeKey(buffer: ByteBuffer): String {
    return StandardCharsets
      .US_ASCII
      .decode(buffer.duplicate())
      .toString()
  }

  fun processUnknownCommand(event: Event) {
    val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.UnknownCommand)
    event.reply(response)
  }

  fun copyBuffer(buffer: ByteBuffer?): ByteBuffer? {
    if (buffer == null) {
      return null
    }
    val newBuffer = ByteBuffer.allocateDirect(buffer.remaining())
    newBuffer.put(buffer)
    newBuffer.flip()
    buffer.position(0)
    return newBuffer
  }
}