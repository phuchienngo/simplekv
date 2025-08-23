package app

import app.config.Config
import app.dashtable.VectorSpeciesUtils
import app.server.Server
import com.google.common.base.Preconditions
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ServerSocket


object MainApp {
  private val LOG: Logger = LoggerFactory.getLogger(MainApp::class.java)

  @JvmStatic
  fun main(args: Array<String>) {
    val config = try {
      parseOption(args)
    } catch (e: Exception) {
      LOG.error("Failed to parse command line arguments", e)
      return
    }

    LOG.info("Application pid: {}", ProcessHandle.current().pid())
    LOG.info("Applying config: {}", config)
    val server = Server(config)

    LOG.info("Starting server on {}", config.port)
    server.start()
    LOG.info("Server is started on {}", config.port)

    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
      LOG.error("Uncaught exception in thread {}", thread.name, exception)
    }

    Runtime.getRuntime().addShutdownHook(Thread {
      LOG.info("Shutting down server")
      server.close()
      LOG.info("Server is stopped")
    })
  }

  private fun parseOption(args: Array<String>): Config {
    val options = Options()
    options.addOption(
      Option.builder("p")
        .longOpt("port")
        .hasArg()
        .argName("PORT")
        .required(true)
        .type(Int::class.java)
        .converter(String::toInt)
        .desc("Port number for the server")
        .get()
    )

    options.addOption(
      Option.builder("n")
        .longOpt("server_name")
        .hasArg()
        .argName("SERVER_NAME")
        .required(false)
        .desc("Name of the server")
        .get()
    )

    options.addOption(
      Option.builder("w")
        .longOpt("worker_num")
        .hasArg()
        .argName("WORKER_NUM")
        .required(false)
        .type(Int::class.java)
        .converter(String::toInt)
        .desc("Number of worker threads")
        .get()
    )

    options.addOption(
      Option.builder("s")
        .longOpt("selector_num")
        .hasArg()
        .argName("SELECTOR_NUM")
        .required(false)
        .type(Int::class.java)
        .converter(String::toInt)
        .desc("Number of selector threads")
        .get()
    )

    options.addOption(
      Option.builder("minbs")
        .longOpt("min_block_size")
        .hasArg()
        .argName("MIN_BLOCK_SIZE")
        .required(false)
        .type(Int::class.java)
        .converter(String::toInt)
        .desc("Minimum block size for memory allocator")
        .get()
    )

    options.addOption(
      Option.builder("maxbs")
        .longOpt("max_block_size")
        .hasArg()
        .argName("MAX_BLOCK_SIZE")
        .required(false)
        .type(Int::class.java)
        .converter(String::toInt)
        .desc("Maximum block size for memory allocator")
        .get()
    )

    options.addOption(
      Option.builder("ss")
        .longOpt("segment_size")
        .hasArg()
        .argName("SEGMENT_SIZE")
        .required(false)
        .type(Int::class.java)
        .converter(String::toInt)
        .desc("Size of segments for DashTable")
        .get()
    )

    options.addOption(
      Option.builder("rs")
        .longOpt("regular_size")
        .hasArg()
        .argName("REGULAR_SIZE")
        .required(false)
        .type(Int::class.java)
        .converter(String::toInt)
        .desc("Regular size for DashTable segments")
        .get()
    )

    options.addOption(
      Option.builder("sls")
        .longOpt("slot_size")
        .hasArg()
        .argName("SLOT_SIZE")
        .required(false)
        .type(Int::class.java)
        .converter(String::toInt)
        .desc("Size of slots in buckets")
        .get()
    )

    val parser = DefaultParser()
    val cmd = parser.parse(options, args)
    val port = cmd.getParsedOptionValue<Int>("p")
    val appName = cmd.getParsedOptionValue("n", "simplekv")
    val workerNum = cmd.getParsedOptionValue("w", 1)
    val selectorNum = cmd.getParsedOptionValue("s", 1)
    val minBlockSize = cmd.getParsedOptionValue("minbs", 256)
    val maxBlockSize = cmd.getParsedOptionValue("maxbs", 16777216)
    val segmentSize = cmd.getParsedOptionValue("ss", 60)
    val regularSize = cmd.getParsedOptionValue("rs", 54)
    val slotSize = cmd.getParsedOptionValue("sls", 16)
    Preconditions.checkArgument(port in 0..65535, "Port number must be between 0 and 65535")
    Preconditions.checkArgument(isPortAvailable(port), "Port number $port is already in use")
    Preconditions.checkArgument(workerNum > 0, "Worker number must be greater than 0")
    Preconditions.checkArgument(appName.isNotBlank(), "Server name cannot be empty")
    Preconditions.checkArgument(selectorNum > 0, "Selector number must be greater than 0")
    Preconditions.checkArgument(minBlockSize > 0, "Minimum block size must be greater than 0")
    Preconditions.checkArgument(maxBlockSize > 0, "Maximum block size must be greater than 0")
    Preconditions.checkArgument(minBlockSize < maxBlockSize, "Minimum block size must be less than maximum block size")
    Preconditions.checkArgument(maxBlockSize % minBlockSize == 0, "Maximum block size must be a multiple of minimum block size")
    Preconditions.checkArgument(isPowerOfTwo(minBlockSize), "Minimum block size must be a power of 2")
    Preconditions.checkArgument(isPowerOfTwo(maxBlockSize), "Maximum block size must be a power of 2")
    Preconditions.checkArgument(segmentSize > 0, "Segment size must be greater than 0")
    Preconditions.checkArgument(regularSize > 0, "Regular size must be greater than 0")
    Preconditions.checkArgument(slotSize > 0, "Slot size must be greater than 0")
    Preconditions.checkArgument(VectorSpeciesUtils.selectBestSpecies(slotSize) != null, "Slot size $slotSize is not supported")
    Preconditions.checkArgument(regularSize < segmentSize, "Regular size must be less than segment size")

    return Config(
      appName = appName,
      port = port,
      workerNum = workerNum,
      selectorNum = selectorNum,
      minBlockSize = minBlockSize,
      maxBlockSize = maxBlockSize,
      segmentSize = segmentSize,
      regularSize = regularSize,
      slotSize = slotSize,
    )
  }

  private fun isPowerOfTwo(n: Int): Boolean {
    return n > 0 && (n and (n - 1)) == 0
  }

  private fun isPortAvailable(port: Int): Boolean {
    return try {
      ServerSocket(port).use {
        true
      }
    } catch (_: Exception) {
      false
    }
  }
}