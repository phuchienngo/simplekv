package app

import app.core.Event
import app.handler.Router
import app.handler.Worker
import app.server.Server
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.lmax.disruptor.SleepingWaitStrategy
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
    val coreNumber = Runtime.getRuntime().availableProcessors()
    val hashFunction = Hashing.crc32c()
    val router = setupRouter(coreNumber, hashFunction)
    val server = Server(
      "simple-kv",
      InetSocketAddress(port),
      coreNumber,
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
      SleepingWaitStrategy()
    )
    val worker = Worker(disruptor)
    disruptor.handleEventsWith(worker)
    return worker
  }
}