package app.server

import app.utils.ByteBufferPooledFactory
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.nio.channels.spi.SelectorProvider
import java.util.LinkedList
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SelectorThread: Thread {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(SelectorThread::class.java)
  }
  private val selector = SelectorProvider.provider().openSelector()
  private val acceptedConnectionQueue = AtomicReference(LinkedBlockingDeque<SocketChannel>())
  private val notifyDoneSessionQueue = AtomicReference(LinkedBlockingDeque<Session>())
  private val isRunning: AtomicBoolean = AtomicBoolean(true)
  private val bufferPool: GenericObjectPool<ByteBuffer>

  constructor(threadName: String, bufferSize: Int, direct: Boolean): super(threadName) {
    val factory = ByteBufferPooledFactory(bufferSize, direct)
    val config = GenericObjectPoolConfig<ByteBuffer>()
    config.blockWhenExhausted = false
    config.maxTotal = -1
    config.maxIdle = 10
    config.testOnBorrow = true
    config.testOnReturn = true
    bufferPool = GenericObjectPool(factory, config)
  }

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
  }

  fun wakeup() {
    selector.wakeup()
  }

  fun stopRunning(): Boolean {
    return isRunning.compareAndSet(true, false)
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
      val session = Session(
        channel,
        selectionKey,
        this,
        LinkedList<ByteBuffer>(),
        null
      )
      selectionKey.attach(session)
    } catch (e: IOException) {
      LOG.error("Failed to register accepted connection to selector", e)
      if (selectionKey != null) {
        cleanUpSelectionKey(selectionKey)
      }
      channel.close()
    }
  }

  private fun notifyRequestDone(session: Session) {
    if (notifyDoneSessionQueue.get().add(session)) {
      selector.wakeup()
    }
  }

  private fun changeInterestOpsSession() {
    val currentInQueue = notifyDoneSessionQueue.getAndSet(LinkedBlockingDeque<Session>())
    var session: Session? = null
    while (currentInQueue.isNotEmpty()) {
      session = currentInQueue.poll()
      session.selectionKey.interestOps(SelectionKey.OP_WRITE)
    }
    session?.selectorThread?.wakeup()
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
    var iterator: MutableIterator<SelectionKey>? = null
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
    val session = selectionKey.attachment() as Session
    try {
      while (true) {
        val buffer = bufferPool.borrowObject()
        val byteReads = session.channel.read(buffer)
        buffer.flip()

        if (byteReads <= 0) {
          bufferPool.returnObject(buffer)
          if (byteReads < 0) {
            resetSession(session)
            cleanUpSelectionKey(selectionKey)
            return
          }
          break
        }

        session.requestBuffers.add(buffer)
        if (buffer.limit() < buffer.capacity()) {
          break
        }
      }

      if (tryParseRequest(session)) {
        processRequest(session)
      }

    } catch (e: IOException) {
      LOG.error("Error during read operation", e)
      resetSession(session)
      cleanUpSelectionKey(selectionKey)
    }
  }

  private fun handleWrite(selectionKey: SelectionKey) {
    val session = selectionKey.attachment() as Session
    try {
      while (session.response?.hasRemaining() == true) {
        val bytesWritten = session.channel.write(session.response)
        if (bytesWritten < 0) {
          cleanUpSelectionKey(selectionKey)
          resetSession(session)
          return
        }
        if (bytesWritten == 0) {
          return
        }
      }
    } catch (e: IOException) {
      LOG.error("Error writing to channel", e)
      resetSession(session)
      cleanUpSelectionKey(selectionKey)
      return
    }

    resetSession(session)
    readyForRead(session.selectionKey)
  }

  private fun resetSession(session: Session) {
    for (borrowed in session.requestBuffers) {
      bufferPool.returnObject(borrowed)
    }
    session.requestBuffers.clear()
    session.response = null
  }

  private fun readyForRead(selectionKey: SelectionKey) {
    selectionKey.interestOps(SelectionKey.OP_READ)
    wakeup()
  }

  private fun tryParseRequest(session: Session): Boolean {
    // TODO implement later
    return false
  }

  private fun processRequest(session: Session) {

  }
}