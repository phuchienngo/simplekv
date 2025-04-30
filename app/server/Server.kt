package app.server

import java.io.IOException
import java.net.InetAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.atomic.AtomicBoolean

class Server(
  private val address: InetAddress,
  private val workerNum: Int
) {
  private val isRunning = AtomicBoolean(false)
  private lateinit var serverChannel: ServerSocketChannel
  private lateinit var acceptThread: AcceptThread
  private lateinit var selectorThreads: List<SelectorThread>

  @Throws(IOException::class)
  fun start() {

  }

  fun stop() {
    if (!isRunning.compareAndSet(true, false)) {
      return
    }
    acceptThread.stopRunning()
    for (selectorThread in selectorThreads) {
      selectorThread.stopRunning()
    }
  }
}