package app

import app.config.Config
import app.server.Server
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.slf4j.Logger
import org.slf4j.LoggerFactory


object MainApp {
  private val LOG: Logger = LoggerFactory.getLogger(MainApp::class.java)

  @JvmStatic
  fun main(args: Array<String>) {
    val configResult = parseOption(args)
    if (configResult.isFailure) {
      LOG.error("Failed to parse command line arguments")
      return
    }
    val config = configResult.getOrNull()!!
    val (_, port, workerNum, selectorNum) = config
    LOG.info("Application pid: {}", ProcessHandle.current().pid())
    LOG.info("Setting up {} worker(s), {} selector(s)", workerNum, selectorNum)
    val server = Server(config)

    LOG.info("Starting server on {}", port)
    server.start()
    LOG.info("Server is started on {}", port)

    Runtime.getRuntime().addShutdownHook(Thread {
      LOG.info("Shutting down server")
      server.stop()
      LOG.info("Server is stopped")
    })
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
        .longOpt("server_name")
        .hasArg()
        .argName("SERVER_NAME")
        .required(true)
        .build()
    )

    options.addOption(
      Option.builder("w")
        .longOpt("worker_num")
        .hasArg()
        .argName("WORKER_NUM")
        .required(true)
        .type(Number::class.java)
        .build()
    )

    options.addOption(
      Option.builder("s")
        .longOpt("selector_num")
        .hasArg()
        .argName("SELECTOR_NUM")
        .required(true)
        .type(Number::class.java)
        .build()
    )

    try {
      val parser = DefaultParser()
      val cmd = parser.parse(options, args)
      val port = cmd.getOptionValue("p").toInt()
      val appName = cmd.getOptionValue("n")
      val workerNum = cmd.getOptionValue("w").toInt()
      val selectorNum = cmd.getOptionValue("s").toInt()
      if (port < 0 || port > 65535) {
        throw IllegalArgumentException("Port number must be between 0 and 65535")
      }
      if (workerNum <= 0) {
        throw IllegalArgumentException("Worker number must be greater than 0")
      }
      if (appName.isBlank()) {
        throw IllegalArgumentException("Server name cannot be empty")
      }
      if (selectorNum <= 0) {
        throw IllegalArgumentException("Selector number must be greater than 0")
      }
      val config = Config(
        appName = appName,
        port = port,
        workerNum = workerNum,
        selectorNum = selectorNum
      )
      return Result.success(config)
    } catch (e: Exception) {
      LOG.error("Error parsing command line arguments", e)
      return Result.failure(e)
    }
  }
}