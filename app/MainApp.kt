package app

import app.config.Config
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
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.ThreadFactory


object MainApp {
  private val LOG: Logger = LoggerFactory.getLogger(MainApp::class.java)

  @JvmStatic
  fun main(args: Array<String>) {
    val configResult = parseOption(args)
    if (configResult.isFailure) {
      LOG.error("Failed to parse command line arguments")
      return
    }
    val (serverName, port, workerNum) = configResult.getOrNull()!!
    LOG.info("Application pid: {}", ProcessHandle.current().pid())
    LOG.info("Setting up {} worker(s)", workerNum)
    val hashFunction = Hashing.crc32c()
    val router = setupRouter(serverName, workerNum, hashFunction)
    val server = Server(
      serverName,
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

  private fun setupRouter(serverName: String, workerNum: Int, hashFunction: HashFunction): Router {
    val threadFactory = ThreadFactoryBuilder()
      .setNameFormat("$serverName-worker-%d")
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

  private fun parseOption(args: Array<String>): Result<Config> {
    val options = Options()
    options.addOption(
      Option.builder("p")
        .longOpt("port")
        .hasArg()
        .argName("PORT")
        .required(true)
        .type(Number::class.java)
        .build()
    )

    options.addOption(
      Option.builder("n")
        .longOpt("name")
        .hasArg()
        .argName("NAME")
        .required(true)
        .build()
    )

    options.addOption(
      Option.builder("w")
        .longOpt("workers")
        .hasArg()
        .argName("NUM")
        .required(true)
        .type(Number::class.java)
        .build()
    )

    try {
      val parser = DefaultParser()
      val cmd = parser.parse(options, args)
      val port = cmd.getOptionValue("p").toInt()
      val serverName = cmd.getOptionValue("n")
      val workerNum = cmd.getOptionValue("w").toInt()
      if (port < 0 || port > 65535) {
        throw IllegalArgumentException("Port number must be between 0 and 65535")
      }
      if (workerNum <= 0) {
        throw IllegalArgumentException("Worker number must be greater than 0")
      }
      if (serverName.isBlank()) {
        throw IllegalArgumentException("Server name cannot be empty")
      }
      val config = Config(
        serverName = serverName,
        port = port,
        workerNum = workerNum
      )
      return Result.success(config)
    } catch (e: Exception) {
      LOG.error("Error parsing command line arguments", e)
      return Result.failure(e)
    }
  }
}