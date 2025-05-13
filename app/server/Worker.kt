package app.server

import app.config.Config
import app.core.ErrorCode
import app.core.Event
import app.handler.Handler
import app.utils.Responses
import com.lmax.disruptor.RingBuffer
import com.lmax.disruptor.Sequence
import com.lmax.disruptor.Sequencer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class Worker internal constructor(
  private val config: Config,
  index: Int,
  private val handler: Handler
): Thread("${config.appName}-Worker-$index") {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(Worker::class.java)
  }
  private val ringBuffer = initRingBuffer()
  private val sequence = Sequence(Sequencer.INITIAL_CURSOR_VALUE)
  private val isRunning: AtomicBoolean = AtomicBoolean(false)

  init {
    ringBuffer.addGatingSequences(sequence)
  }

  override fun run() {
    if (!isRunning.compareAndSet(false, true)) {
      return
    }
    while (isRunning.get()) {
      if (processQueuedEvent()) {
        continue
      }
      yield()
    }
  }

  fun stopRunning() {
    isRunning.set(false)
  }

  fun dispatch(message: Message) {
    val sequence = ringBuffer.next()
    val request = ringBuffer[sequence]
    request.message = message
    ringBuffer.publish(sequence)
  }

  private fun processQueuedEvent(): Boolean {
    val availableSequence = ringBuffer.cursor
    if (availableSequence <= sequence.get()) {
      return false
    }
    val prev = sequence.get()
    var current = prev
    while (current + 1 <= availableSequence && ringBuffer.isAvailable(current + 1)) {
      val event = ringBuffer[++current]
      try {
        handler.handle(event)
      } catch (e: Exception) {
        LOG.error("Uncaught error when processing event at sequence {} event {}", sequence, event, e)
        val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.InternalError)
        event.reply(response)
      }
    }
    sequence.set(current)
    return prev < current
  }

  private fun initRingBuffer(): RingBuffer<Event> {
    return if (config.selectorNum == 1) {
      RingBuffer.createSingleProducer(Event.FACTORY, 1024)
    } else {
      RingBuffer.createMultiProducer(Event.FACTORY, 1024)
    }
  }
}