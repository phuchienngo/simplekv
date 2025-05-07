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
        val response = Responses.makeResponse(event.header, 0, null, null, null)
        event.message.reply(response)
      }
      CommandOpCodes.VERSION.value -> {
        val buffer = version.duplicate()
        val response = Responses.makeResponse(event.header, 0, null, null, buffer)
        event.message.reply(response)
      }
      CommandOpCodes.QUIT.value,
      CommandOpCodes.QUITQ.value,
      CommandOpCodes.FLUSH.value,
      CommandOpCodes.FLUSHQ.value,
      CommandOpCodes.STAT.value -> {
        val response = Responses.makeError(event.header, ErrorCode.NotSupported)
        event.message.reply(response)
      }
      else -> {
        val response = Responses.makeError(event.message.header, ErrorCode.UnknownCommand)
        event.message.reply(response)
      }
    }
  }
}