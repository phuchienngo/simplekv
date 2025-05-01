package app.server

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.SocketAddress
import java.nio.channels.ServerSocketChannel
import java.util.concurrent.atomic.AtomicBoolean

class Server(
  private val bindingAddress: SocketAddress,
  private val selectorNum: Int,
  private val selectorHandler: (Message) -> Unit,
) {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(Server::class.java)
  }
  private val isRunning = AtomicBoolean(true)
  private lateinit var serverChannel: ServerSocketChannel
  private lateinit var acceptThread: AcceptThread
  private lateinit var selectorThreads: MutableList<SelectorThread>

  @Throws(IOException::class)
  fun start() {
    serverChannel = ServerSocketChannel.open()
    serverChannel.configureBlocking(false)
    serverChannel.socket().bind(bindingAddress)
    selectorThreads = mutableListOf()

    for (i in 0 until selectorNum) {
      val selectorThread = SelectorThread(this, "SelectorThread-$i", selectorHandler)
      selectorThread.start()
      selectorThreads.add(selectorThread)
    }

    acceptThread = AcceptThread(this, serverChannel, selectorThreads)
    acceptThread.start()
    LOG.info("Server started on {}", bindingAddress)
  }

  fun stop() {
    if (!isRunning.compareAndSet(true, false)) {
      return
    }
    LOG.info("Stopping server")
    acceptThread.stopRunning()
    for (selectorThread in selectorThreads) {
      selectorThread.stopRunning()
    }
    serverChannel.close()
  }
}