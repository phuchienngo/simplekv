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
        && event.header.extrasLength.toInt() == event.body.extras?.size
  }

  fun hasKey(event: Event): Boolean {
    if (event.header.keyLength <= SHORT_ZERO
      || event.header.keyLength > SHORT_250
      || event.body.key == null
      || event.body.key?.size != event.header.keyLength.toInt()) {
      return false
    }
    val buffer = event.body.key
    if (buffer == null) {
      return false
    }
    for (b in event.body.key!!) {
      if (b <= BYTE_32 || b == BYTE_127) {
        return false
      }
    }
    return true
  }

  fun hasValue(event: Event): Boolean {
    return event.header.valueLength > 0
        && event.body.value != null
        && event.body.value?.size == event.header.valueLength
  }
}