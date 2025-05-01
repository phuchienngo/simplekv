package app.core

import app.server.Message
import com.lmax.disruptor.EventFactory
import java.nio.ByteBuffer

class Event {
  companion object {
    val FACTORY = EventFactory { Event() }
  }

  lateinit var message: Message
  val header: Header
    get() = message.header
  val body: Body
    get() = message.body

  fun reply(buffer: ByteBuffer) {
    message.reply(buffer)
  }

  fun done() {
    message.done()
  }
}
