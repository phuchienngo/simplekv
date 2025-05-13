package app.handler

import app.core.CommandOpCodes
import app.core.ErrorCode
import app.core.Event
import app.utils.Responses
import java.nio.ByteBuffer

interface NullKeyHandler {
  val version: ByteBuffer

  fun handleNullKeyRequest(event: Event) {
    when (event.header.opcode) {
      CommandOpCodes.NOOP.value -> {
        val response = Responses.makeResponse(event.responseBuffer, event.header, 0, null, null, null)
        event.reply(response)
      }
      CommandOpCodes.VERSION.value -> {
        val buffer = version.duplicate()
        val response = Responses.makeResponse(event.responseBuffer, event.header, 0, null, null, buffer)
        event.reply(response)
      }
      CommandOpCodes.QUIT.value,
      CommandOpCodes.QUITQ.value,
      CommandOpCodes.FLUSH.value,
      CommandOpCodes.FLUSHQ.value,
      CommandOpCodes.STAT.value -> {
        val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.NotSupported)
        event.reply(response)
      }
      else -> {
        val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.UnknownCommand)
        event.reply(response)
      }
    }
  }
}