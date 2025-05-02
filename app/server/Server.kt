package app.server

import app.handler.Router
import com.lmax.disruptor.YieldingWaitStrategy
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import java.io.IOException
import java.net.SocketAddress
import java.nio.channels.ServerSocketChannel
import java.util.concurrent.atomic.AtomicBoolean

class Server(
  serverName: String,
  private val bindingAddress: SocketAddress,
  selectorNum: Int,
  private val router: Router,
) {
  private val isRunning = AtomicBoolean(false)
  private val serverChannel: ServerSocketChannel = ServerSocketChannel.open()
  private val acceptor: AcceptorThread
  private val selectors: List<SelectorThread>

  init {
    serverChannel.configureBlocking(false)
    selectors = (0 until selectorNum).map { index ->
      return@map SelectorThread(
        "$serverName-selector-$index",
        this,
        router,
        createDisruptor()
      )
    }
    acceptor = AcceptorThread("$serverName-acceptor", this, serverChannel, selectors)
  }

  @Throws(IOException::class)
  fun start() {
    if (!isRunning.compareAndSet(false, true)) {
      return
    }
    router.start()
    serverChannel.socket().bind(bindingAddress)
    for (selectorThread in selectors) {
      selectorThread.start()
    }
    acceptor.start()
  }

  fun stop() {
    if (!isRunning.compareAndSet(true, false)) {
      return
    }
    router.stop()
    acceptor.stopRunning()
    for (selectorThread in selectors) {
      selectorThread.stopRunning()
    }
    serverChannel.close()
    Thread.sleep(200)
    for (selectorThread in selectors) {
      selectorThread.interrupt()
    }
    acceptor.interrupt()
  }

  private fun createDisruptor(): Disruptor<Container<Any>> {
    return Disruptor(
      Container.FACTORY,
      1024,
      null,
      ProducerType.MULTI,
      YieldingWaitStrategy()
    )
  }
}