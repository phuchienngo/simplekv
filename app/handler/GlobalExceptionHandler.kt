package app.handler

import app.core.Event
import app.core.ErrorCode
import app.utils.Responses
import com.lmax.disruptor.ExceptionHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GlobalExceptionHandler: ExceptionHandler<Event> {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(ExceptionHandler::class.java)
    val INSTANCE = GlobalExceptionHandler()
  }
  override fun handleEventException(t: Throwable, sequence: Long, event: Event) {
    LOG.error("Uncaught error when processing event at sequence {}", sequence, t)
    if (!event.message.isLoaded()) {
      LOG.warn("Invalid state of event {}, skipping", event)
      return
    }
    LOG.debug("Using internal error response for event {}", event)
    val response = Responses.makeError(event.header, ErrorCode.InternalError)
    event.reply(response)
  }

  override fun handleOnStartException(t: Throwable) {
    LOG.error("Error during disruptor startup", t)
  }

  override fun handleOnShutdownException(t: Throwable) {
    LOG.error("Error during disruptor shutdown", t)
  }
}