package app.handler

import app.core.Event
import app.server.Message
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.dsl.Disruptor

abstract class AbstractWorker(
  private val disruptor: Disruptor<Event>
): EventHandler<Event> {
  private val ringBuffer = disruptor.ringBuffer
  abstract fun process(event: Event)

  fun start() {
    if (!disruptor.hasStarted()) {
      disruptor.start()
    }
  }

  fun stop() {
    if (disruptor.hasStarted()) {
      disruptor.shutdown()
    }
  }

  fun dispatch(message: Message) {
    val sequence = ringBuffer.next()
    val request = ringBuffer[sequence]
    request.message = message
    ringBuffer.publish(sequence)
  }

  override fun onEvent(event: Event, sequence: Long, endOfBatch: Boolean) {
    process(event)
  }
}