package app.utils

import app.core.Event

object Validators {
  private const val BYTE_ZERO = 0.toByte()
  private const val BYTE_32 = 32.toByte()
  private const val BYTE_127 = 127.toByte()
  private const val SHORT_ZERO = 0.toShort()
  private const val SHORT_250 = 250.toShort()

  fun hasExtras(event: Event): Boolean {
    return event.header.extrasLength > BYTE_ZERO
        && event.body.extras != null
        && event.body.extras?.position() == event.body.extras?.capacity()
  }

  fun hasKey(event: Event): Boolean {
    if (event.header.keyLength <= SHORT_ZERO || event.header.keyLength > SHORT_250) {
      return false
    }
    val buffer = event.body.key
    if (buffer == null || buffer.position() < buffer.capacity()) {
      return false
    }
    while (buffer.hasRemaining()) {
      val b = buffer.get()
      if (b <= BYTE_32 || b == BYTE_127) {
        return false
      }
    }
    buffer.position(0)
    return event.header.keyLength > SHORT_ZERO
        && event.header.keyLength <= SHORT_250
        && event.body.key != null
        && event.body.key?.position() == event.body.key?.capacity()
  }

  fun hasValue(event: Event): Boolean {
    return event.header.valueLength > 0
        && event.body.value != null
        && event.body.value?.position() == event.body.value?.capacity()
  }
}