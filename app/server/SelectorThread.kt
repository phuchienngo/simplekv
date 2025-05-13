package app.server

import app.config.Config
import com.google.common.hash.Hashing
import com.lmax.disruptor.RingBuffer
import com.lmax.disruptor.Sequence
import com.lmax.disruptor.Sequencer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class SelectorThread(
  config: Config,
  private val server: Server,
  private val workers: List<Worker>,
  index: Int,
): Thread("${config.appName}-SelectorThread-$index") {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(SelectorThread::class.java)
  }
  private val ringBuffer = initRingBuffer()
  private val sequence = Sequence(Sequencer.INITIAL_CURSOR_VALUE)
  private val selector = SelectorProvider.provider().openSelector()
  private val isRunning: AtomicBoolean = AtomicBoolean(false)
  private val hashFunction = Hashing.farmHashFingerprint64()
  private val random = Random.Default

  init {
    ringBuffer.addGatingSequences(sequence)
  }

  fun addAcceptedConnection(channel: SocketChannel): Boolean {
    val next = try {
      ringBuffer.tryNext()
    } catch (_: Exception) {
      return false
    }
    val container = ringBuffer[next]
    container.value = channel
    ringBuffer.publish(next)
    selector.wakeup()
    return true
  }

  override fun run() {
    if (!isRunning.compareAndSet(false, true)) {
      return
    }
    try {
      while (isRunning.get()) {
        val isSelected = select()
        val isProcessed = processQueuedEvent()
        if (isSelected || isProcessed) {
          continue
        }
        yield()
      }
    } catch (e: Exception) {
      LOG.error("Selector Thread crashed due to an unexpected error", e)
    }

    for (selectionKey in selector.keys()) {
      cleanUpSelectionKey(selectionKey)
    }

    try {
      selector.close()
    } catch (e: Exception) {
      LOG.error("Error closing selector", e)
    }
    server.stop()
  }

  fun wakeup() {
    selector.wakeup()
  }

  fun stopRunning() {
    wakeup()
    isRunning.set(false)
  }

  private fun processQueuedEvent(): Boolean {
    val availableSequence = ringBuffer.cursor
    if (availableSequence <= sequence.get()) {
      return false
    }
    val prev = sequence.get()
    var current = prev
    while (current + 1 <= availableSequence && ringBuffer.isAvailable(current + 1)) {
      val container = ringBuffer[++current]
      val value = container.value ?: continue
      when (value) {
        is SocketChannel -> registerAcceptedConnection(value)
        is Message -> value.requestInterestChange()
      }
    }
    sequence.set(current)
    return prev < current
  }

  private fun registerAcceptedConnection(channel: SocketChannel) {
    var selectionKey: SelectionKey? = null
    try {
      selectionKey = channel.register(selector, SelectionKey.OP_READ)
      val message = Message(channel, selectionKey, this)
      selectionKey.attach(message)
    } catch (e: Exception) {
      LOG.error("Failed to register accepted connection to selector", e)
      if (selectionKey != null) {
        cleanUpSelectionKey(selectionKey)
      }
      channel.close()
    }
  }

  fun requestInterestChange(message: Message) {
    val next = ringBuffer.next()
    val container = ringBuffer[next]
    container.value = message
    ringBuffer.publish(next)
    selector.wakeup()
  }

  private fun cleanUpSelectionKey(selectionKey: SelectionKey) {
    try {
      val attachment = selectionKey.attachment() as? Message
      if (attachment != null) {
        attachment.close()
        return
      }
      selectionKey.cancel()
      selectionKey.channel().close()
    } catch (e: Exception) {
      LOG.error("Error closing connection", e)
    }
  }

  private fun select(): Boolean {
    var iterator: MutableIterator<SelectionKey>
    try {
      val readyChannels = selector.selectNow()
      if (readyChannels == 0) {
        return false
      }

      val selectedKeys = selector.selectedKeys()
      iterator = selectedKeys.iterator()
    } catch (e: Exception) {
      LOG.error("Encountered an error while selecting!", e)
      return false
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
    return true
  }

  private fun handleRead(selectionKey: SelectionKey) {
    val message = selectionKey.attachment() as Message
    if (!message.read()) {
      cleanUpSelectionKey(selectionKey)
      return
    }

    if (message.isLoaded()) {
      handleRequest(message)
    }
  }

  private fun handleWrite(selectionKey: SelectionKey) {
    val message = selectionKey.attachment() as Message
    if (!message.write()) {
      cleanUpSelectionKey(selectionKey)
    }
  }

  private fun initRingBuffer(): RingBuffer<Container<Any>> {
    return RingBuffer.createMultiProducer(Container.FACTORY, 1024)
  }

  private fun handleRequest(message: Message) {
    val workerIndex = if (message.body.key != null) {
      val key = message.body.key!!.duplicate()
      val hash = hashFunction.hashBytes(key)
      Hashing.consistentHash(hash, workers.size)
    } else {
      random.nextInt(0, workers.size)
    }

    workers[workerIndex].dispatch(message)
  }
}