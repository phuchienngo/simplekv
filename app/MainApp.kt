package app

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object MainApp {
  private val LOG: Logger = LoggerFactory.getLogger(MainApp::class.java)

  @JvmStatic
  fun main(args: Array<String>) {
    LOG.info("Hello")
  }
}