package app.handler

import app.core.CommandOpCodes
import app.core.ErrorCode
import app.server.Message
import app.utils.Responses
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class Router(
  private val workers: List<Worker>,
  private val hashFunction: HashFunction
) {
  private val isStarted = AtomicBoolean(false)
  private val version = StandardCharsets.US_ASCII.encode("1.0.0")

  fun start() {
    if (!isStarted.compareAndSet(false, true)) {
      return
    }

    for (worker in workers) {
      worker.start()
    }
  }

  fun stop() {
    if (!isStarted.compareAndSet(true, false)) {
      return
    }
    for (worker in workers) {
      worker.stop()
    }
  }

  fun handle(message: Message) {
    if (message.body.key == null) {
      handleNullKeyRequest(message)
    } else {
      handleRequest(message)
    }
  }

  private fun handleNullKeyRequest(message: Message) {
    when (message.header.opcode) {
      CommandOpCodes.NOOP.value -> {
        val response = Responses.makeResponse(message.header, 0, null, null, null)
        message.reply(response)
      }
      CommandOpCodes.VERSION.value -> {
        val buffer = version.duplicate()
        val response = Responses.makeResponse(message.header, 0, null, null, buffer)
        message.reply(response)
      }
      CommandOpCodes.QUIT.value,
      CommandOpCodes.QUITQ.value,
      CommandOpCodes.FLUSH.value,
      CommandOpCodes.FLUSHQ.value,
      CommandOpCodes.STAT.value -> {
        val response = Responses.makeError(message.header, ErrorCode.NotSupported)
        message.reply(response)
      }
      else -> {
        val response = Responses.makeError(message.header, ErrorCode.UnknownCommand)
        message.reply(response)
      }
    }
  }

  private fun handleRequest(message: Message) {
    val key = message.body.key!!.duplicate()
    val hash = hashFunction.hashBytes(key)
    val workerIndex = Hashing.consistentHash(hash, workers.size)

    workers[workerIndex].dispatch(message)
  }
}