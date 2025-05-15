package app.handler

import app.core.CommandOpCodes
import app.core.Event
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

abstract class BaseProcessor {
  protected fun decodeKey(buffer: ByteBuffer): String {
    return StandardCharsets
      .US_ASCII
      .decode(buffer.duplicate())
      .toString()
  }

  abstract fun process(event: Event, command: CommandOpCodes)
}