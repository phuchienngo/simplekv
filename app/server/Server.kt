package app.server

import app.config.Config
import app.handler.MainHandler
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.util.concurrent.atomic.AtomicBoolean

class Server(private val config: Config) {
  private val isRunning = AtomicBoolean(false)
  private val serverChannel: ServerSocketChannel = ServerSocketChannel.open()
  private val acceptor: AcceptorThread
  private val selectors: List<SelectorThread>
  private val workers: List<Worker>

  init {
    serverChannel.configureBlocking(false)
    workers = initWorkers()
    selectors = initSelectors()
    acceptor = AcceptorThread(config, this, serverChannel, selectors)
  }

  private fun initWorkers(): List<Worker> {
    return (0 until config.workerNum).map { index ->
      return@map Worker(
        config,
        index,
        MainHandler(config)
      )
    }
  }

  private fun initSelectors(): List<SelectorThread> {
    return (0 until config.selectorNum).map { index ->
      return@map SelectorThread(
        config,
        this,
        workers,
        index
      )
    }
  }

  fun start() {
    if (!isRunning.compareAndSet(false, true)) {
      return
    }
    val bindingAddress = InetSocketAddress(config.port)
    serverChannel.socket().bind(bindingAddress)
    for (worker in workers) {
      worker.start()
    }
    for (selectorThread in selectors) {
      selectorThread.start()
    }
    acceptor.start()
  }

  fun stop() {
    if (!isRunning.compareAndSet(true, false)) {
      return
    }
    acceptor.stopRunning()
    for (worker in workers) {
      worker.stopRunning()
    }
    for (selectorThread in selectors) {
      selectorThread.stopRunning()
    }
    serverChannel.close()
    Thread.sleep(200)
    for (worker in workers) {
      worker.interrupt()
    }
    for (selectorThread in selectors) {
      selectorThread.interrupt()
    }
    acceptor.interrupt()
  }
}