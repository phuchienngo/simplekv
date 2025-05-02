package app.server

import app.handler.Router
import com.lmax.disruptor.InsufficientCapacityException
import com.lmax.disruptor.Sequence
import com.lmax.disruptor.Sequencer
import com.lmax.disruptor.dsl.Disruptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.atomic.AtomicBoolean

class SelectorThread(
  threadName: String,
  private val server: Server,
  private val router: Router,
  private val disruptor: Disruptor<Container<Any>>
): Thread(threadName) {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(SelectorThread::class.java)
  }
  private val ringBuffer = disruptor.ringBuffer
  private val sequence = Sequence(Sequencer.INITIAL_CURSOR_VALUE)
  private val selector = SelectorProvider.provider().openSelector()
  private val isRunning: AtomicBoolean = AtomicBoolean(true)
  init {
    ringBuffer.addGatingSequences(sequence)
  }

  fun addAcceptedConnection(channel: SocketChannel): Boolean {
    if (publishToRingBuffer(channel)) {
      selector.wakeup()
      return true
    }
    return false
  }

  override fun run() {
    if (!disruptor.hasStarted()) {
      disruptor.start()
    }
    try {
      while (isRunning.get()) {
        select()
        processQueuedEvent()
      }
    } catch (e: IOException) {
      LOG.error("Selector Thread crashed due to an unexpected error", e)
    }

    for (selectionKey in selector.keys()) {
      cleanUpSelectionKey(selectionKey)
    }

    try {
      selector.close()
    } catch (e: IOException) {
      LOG.error("Error closing selector", e)
    }
    if (disruptor.hasStarted()) {
      disruptor.shutdown()
    }
    server.stop()
  }

  fun wakeup() {
    selector.wakeup()
  }

  fun stopRunning() {
    wakeup()
    isRunning.set(false)
    if (!disruptor.hasStarted()) {
      disruptor.shutdown()
    }
  }

  private fun processQueuedEvent() {
    val availableSequence = ringBuffer.cursor
    if (availableSequence <= sequence.get()) {
      return
    }
    for (i in (sequence.get() + 1)..availableSequence) {
      val container = ringBuffer[i]
      val value = container.value ?: continue
      when (value) {
        is SocketChannel -> registerAcceptedConnection(value)
        is Message -> value.requestInterestChange()
      }
    }

    sequence.set(availableSequence)
  }

  private fun registerAcceptedConnection(channel: SocketChannel) {
    var selectionKey: SelectionKey? = null
    try {
      selectionKey = channel.register(selector, SelectionKey.OP_READ)
      val message = Message(channel, selectionKey, this)
      selectionKey.attach(message)
    } catch (e: IOException) {
      LOG.error("Failed to register accepted connection to selector", e)
      if (selectionKey != null) {
        cleanUpSelectionKey(selectionKey)
      }
      channel.close()
    }
  }

  fun requestInterestChange(message: Message) {
    if (publishToRingBuffer(message)) {
      selector.wakeup()
    }
  }

  private fun publishToRingBuffer(value: Any): Boolean {
    val next = try {
      ringBuffer.tryNext()
    } catch (_: InsufficientCapacityException) {
      return false
    }
    val container = ringBuffer[next]
    container.value = value
    ringBuffer.publish(next)
    return true
  }

  private fun cleanUpSelectionKey(selectionKey: SelectionKey) {
    try {
      selectionKey.cancel()
      selectionKey.channel().close()
    } catch (e: IOException) {
      LOG.error("Error closing connection", e)
    }
  }

  private fun select() {
    var iterator: MutableIterator<SelectionKey>
    try {
      val readyChannels = selector.selectNow()
      if (readyChannels == 0) {
        return
      }

      val selectedKeys = selector.selectedKeys()
      iterator = selectedKeys.iterator()
    } catch (e: IOException) {
      LOG.warn("Encountered an error while selecting!", e)
      return
    }

    while (isRunning.get() && iterator.hasNext()) {
      val selectionKey = iterator.next()
      iterator.remove()
      when {
        !selectionKey.isValid -> cleanUpSelectionKey(selectionKey)
        selectionKey.isReadable -> handleRead(selectionKey)
        selectionKey.isWritable -> handleWrite(selectionKey)
        else -> LOG.warn("Unexpected state [{}] in select!", selectionKey.interestOps())
      }
    }
  }

  private fun handleRead(selectionKey: SelectionKey) {
    val message = selectionKey.attachment() as Message
    if (!message.read()) {
      cleanUpSelectionKey(selectionKey)
      return
    }

    if (message.isLoaded()) {
      router.handle(message)
    }
  }

  private fun handleWrite(selectionKey: SelectionKey) {
    val message = selectionKey.attachment() as Message
    if (!message.write()) {
      cleanUpSelectionKey(selectionKey)
    }
  }
}