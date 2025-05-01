package app.server

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SelectorThread(
  private val server: Server,
  threadName: String,
  private val selectorHandler: (Message) -> Unit
): Thread(threadName) {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(SelectorThread::class.java)
  }
  private val selector = SelectorProvider.provider().openSelector()
  private val acceptedConnectionQueue = AtomicReference(LinkedBlockingDeque<SocketChannel>())
  private val selectInterestChangesQueue = AtomicReference(LinkedBlockingDeque<Message>())
  private val isRunning: AtomicBoolean = AtomicBoolean(true)

  fun addAcceptedConnection(channel: SocketChannel): Boolean {
    val result = acceptedConnectionQueue.get().offer(channel)
    selector.wakeup()
    return result
  }

  override fun run() {
    try {
      while (isRunning.get()) {
        select()
        setupAcceptedConnection()
        changeInterestOpsSession()
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
    server.stop()
  }

  fun wakeup() {
    selector.wakeup()
  }

  fun stopRunning() {
    wakeup()
    isRunning.set(false)
  }

  private fun setupAcceptedConnection() {
    val currentInQueue = acceptedConnectionQueue.getAndSet(LinkedBlockingDeque<SocketChannel>())
    while (currentInQueue.isNotEmpty()) {
      registerAcceptedConnection(currentInQueue.poll())
    }
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
    if (selectInterestChangesQueue.get().add(message)) {
      selector.wakeup()
    }
  }

  private fun changeInterestOpsSession() {
    val currentInQueue = selectInterestChangesQueue.getAndSet(LinkedBlockingDeque<Message>())
    while (currentInQueue.isNotEmpty()) {
      val message = currentInQueue.poll()
      message.requestInterestChange()
    }
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
    var iterator: MutableIterator<SelectionKey>?
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
      selectorHandler(message)
    }
  }

  private fun handleWrite(selectionKey: SelectionKey) {
    val message = selectionKey.attachment() as Message
    if (!message.write()) {
      cleanUpSelectionKey(selectionKey)
      return
    }
  }
}