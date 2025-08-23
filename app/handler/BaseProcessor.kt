package app.handler

import app.core.CommandOpCodes
import app.core.Event
import java.nio.ByteBuffer

abstract class BaseProcessor {
  protected fun decodeKey(buffer: ByteBuffer): ByteArray {
    val byteArray = ByteArray(buffer.remaining())
    buffer.get(byteArray, 0, byteArray.size)
    return byteArray
  }

  abstract fun process(event: Event, command: CommandOpCodes)
}