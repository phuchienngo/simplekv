package app

import app.core.Event
import app.handler.GlobalExceptionHandler
import app.handler.Router
import app.handler.Worker
import app.server.Server
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.lmax.disruptor.YieldingWaitStrategy
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.ThreadFactory

object MainApp {
  private val LOG: Logger = LoggerFactory.getLogger(MainApp::class.java)

  @JvmStatic
  fun main(args: Array<String>) {
    val port = 11211
    val workerNum = 2
    LOG.info("Application pid: {}", ProcessHandle.current().pid())
    LOG.info("Setting up {} worker(s)", workerNum)
    val hashFunction = Hashing.crc32c()
    val router = setupRouter(workerNum, hashFunction)
    val server = Server(
      "simple-kv",
      InetSocketAddress(port),
      workerNum,
      router
    )

    LOG.info("Starting server on {}", port)
    server.start()
    LOG.info("Server is started on {}", port)

    Runtime.getRuntime().addShutdownHook(Thread {
      LOG.info("Shutting down server")
      server.stop()
      LOG.info("Server is stopped")
    })
  }

  private fun setupRouter(workerNum: Int, hashFunction: HashFunction): Router {
    val threadFactory = ThreadFactoryBuilder()
      .setNameFormat("simple-kv-worker-%d")
      .build()
    val workers = (0 until workerNum).map {
      return@map createWorker(threadFactory)
    }
    return Router(workers, hashFunction)
  }

  private fun createWorker(threadFactory: ThreadFactory): Worker {
    val disruptor = Disruptor(
      Event.FACTORY,
      1024,
      threadFactory,
      ProducerType.MULTI,
      YieldingWaitStrategy()
    )
    val worker = Worker(disruptor)
    disruptor.handleEventsWith(worker)
    disruptor.setDefaultExceptionHandler(GlobalExceptionHandler.INSTANCE)
    return worker
  }
}