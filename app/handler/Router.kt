package app.handler

import app.server.Message
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import java.util.concurrent.atomic.AtomicBoolean

class Router(
  private val workers: List<Worker>,
  private val hashFunction: HashFunction
) {
  private val isStarted = AtomicBoolean(false)

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
    // TODO: Handle null key request
  }

  private fun handleRequest(message: Message) {
    val key = message.body.key!!
    val hash = hashFunction.hashBytes(key)
    val workerIndex = Hashing.consistentHash(hash, workers.size)

    workers[workerIndex].dispatch(message)
  }
}