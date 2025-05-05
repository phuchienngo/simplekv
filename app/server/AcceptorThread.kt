package app.server

import app.config.Config
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AcceptorThread: Thread {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(AcceptorThread::class.java)
  }
  private val server: Server
  private val selectorThreads: List<SelectorThread>
  private val serverChannel: ServerSocketChannel
  private val acceptSelector: Selector
  private val isRunning: AtomicBoolean
  private val increment: AtomicLong

  constructor(config: Config, server: Server, serverChannel: ServerSocketChannel, selectorThreads: List<SelectorThread>): super("${config.appName}-AcceptorThread") {
    this.server = server
    this.serverChannel = serverChannel
    this.selectorThreads = selectorThreads
    acceptSelector = SelectorProvider.provider().openSelector()
    isRunning = AtomicBoolean(false)
    increment = AtomicLong(0)
    serverChannel.register(acceptSelector, SelectionKey.OP_ACCEPT)
  }

  override fun run() {
    if (!isRunning.compareAndSet(false, true)) {
      return
    }
    while (isRunning.get()) {
      select()
    }

    try {
      acceptSelector.close()
    } catch (e: Exception) {
      LOG.error("Got an exception while closing accept selector!", e)
    }

    for (selectorThread in selectorThreads) {
      selectorThread.wakeup()
    }
    server.stop()
  }

  private fun select() {
    try {
      acceptSelector.select()
      val selectorKeys = acceptSelector.selectedKeys()
      val iterator = selectorKeys.iterator()
      while (isRunning.get() && iterator.hasNext()) {
        val selectionKey = iterator.next()
        iterator.remove()
        when {
          !selectionKey.isValid -> continue
          selectionKey.isAcceptable -> handleAccept()
          else -> LOG.warn("Unexpected state [{}] in select!", selectionKey.interestOps())
        }
      }
    } catch (e: Exception) {
      LOG.error("Encountered an error while selecting!", e)
    }
  }

  private fun handleAccept() {
    try {
      val clientChannel = serverChannel.accept()
      clientChannel.configureBlocking(false)
      val targetIndex = increment.incrementAndGet() % selectorThreads.size
      val selectorThread = selectorThreads[targetIndex.toInt()]
      if (!selectorThread.addAcceptedConnection(clientChannel)) {
        clientChannel.close()
      }
    } catch (e: Exception) {
      LOG.error("Error accepting connection", e)
    }
  }

  fun wakeup() {
    acceptSelector.wakeup()
  }

  fun stopRunning() {
    wakeup()
    isRunning.set(false)
  }
}