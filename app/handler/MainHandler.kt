package app.handler

import app.core.Event
import app.datastructure.SwissMap
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class MainHandler(): NullKeyHandler, NotNullKeyHandler, Handler {
  override val valueMap = SwissMap<String, ByteBuffer>(10_000_000, 0.75f)
  override val extrasMap = SwissMap<String, ByteBuffer>(10_000_000, 0.75f)
  override val casMap = SwissMap<String, Long>(10_000_000, 0.75f)
  override val version: ByteBuffer = StandardCharsets.US_ASCII.encode("1.0.0")

  override fun handle(event: Event) {
    if (event.body.key == null) {
      handleNullKeyRequest(event)
    } else {
      handleNotNullKeyRequest(event)
    }
  }
}