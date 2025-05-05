package app.server

import app.config.Config
import app.handler.Router
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.util.concurrent.atomic.AtomicBoolean

class Server(
  private val config: Config,
  private val router: Router,
) {
  private val isRunning = AtomicBoolean(false)
  private val serverChannel: ServerSocketChannel = ServerSocketChannel.open()
  private val acceptor: AcceptorThread
  private val selectors: List<SelectorThread>

  init {
    serverChannel.configureBlocking(false)
    selectors = (0 until config.selectorNum).map { index ->
      return@map SelectorThread(
        config,
        this,
        router,
        index
      )
    }
    acceptor = AcceptorThread(config, this, serverChannel, selectors)
  }

  fun start() {
    if (!isRunning.compareAndSet(false, true)) {
      return
    }
    router.start()
    val bindingAddress = InetSocketAddress(config.port)
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
}