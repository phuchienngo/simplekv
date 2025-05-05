package app.server

import app.core.Body
import app.core.Header
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

class Message(
  val channelSocket: SocketChannel,
  val selectionKey: SelectionKey,
  val selectorThread: SelectorThread,
) {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(Message::class.java)
  }

  private var state = State.READING_HEADER
  private var buffer: ByteBuffer = selectorThread.borrowHeaderBuffer()
  lateinit var header: Header
  lateinit var body: Body
  private lateinit var responseBuffer: ByteBuffer

  @JvmSynthetic
  internal fun isLoaded(): Boolean {
    return state == State.READ_BODY_COMPLETE
  }

  @JvmSynthetic
  internal fun read(): Boolean {
    if (state == State.READING_HEADER) {
      if (!internalRead()) {
        return false
      }

      if (!buffer.hasRemaining()) {
        buffer.flip()
        header = parseHeader(buffer)
        selectorThread.returnHeaderBuffer(buffer)
        if (!header.isMagicRequest()) {
          LOG.error("Invalid magic number in request: {}", header.magic)
          return false
        }
        buffer = ByteBuffer.allocateDirect(header.totalBodyLength)
        state = State.READING_BODY
      }
    }

    if (state == State.READING_BODY) {
      if (!internalRead()) {
        return false
      }

      if (!buffer.hasRemaining()) {
        selectionKey.interestOps(0)
        state = State.READ_BODY_COMPLETE
        buffer.flip()
        val extrasLength = header.extrasLength.toInt()
        val keyLength = header.keyLength.toInt()
        val valueLength = header.valueLength
        body = Body(
          slice(buffer, 0, extrasLength),
          slice(buffer,  extrasLength, keyLength),
          slice(buffer, extrasLength + keyLength, valueLength)
        )
      }
    }

    return true
  }

  private fun slice(buffer: ByteBuffer, index: Int, length: Int): ByteBuffer? {
    if (index < 0 || length <= 0) {
      return null
    }
    return buffer.slice(index, length)
  }

  @JvmSynthetic
  internal fun write(): Boolean {
    if (state == State.WRITING) {
      try {
        while (responseBuffer.hasRemaining()) {
          val bytesWritten = channelSocket.write(responseBuffer)
          if (bytesWritten == 0) {
            return true
          }
          if (bytesWritten < 0) {
            return false
          }
        }
        prepareRead()
      } catch (e: Exception) {
        LOG.error("Error writing to channel", e)
        return false
      }
    }

    return true
  }

  @JvmSynthetic
  internal fun requestInterestChange() {
    if (Thread.currentThread() === selectorThread) {
      changeSelectInterests()
    } else {
      selectorThread.requestInterestChange(this)
    }
  }

  fun reply(buffer: ByteBuffer) {
    responseBuffer = buffer
    state = State.AWAITING_REGISTER_WRITE
    requestInterestChange()
  }

  fun done() {
    state = State.AWAITING_REGISTER_READ
    requestInterestChange()
  }

  private fun changeSelectInterests() {
    try {
      when (state) {
        State.AWAITING_REGISTER_WRITE -> {
          selectionKey.interestOps(SelectionKey.OP_WRITE)
          state = State.WRITING
        }
        State.AWAITING_REGISTER_READ -> prepareRead()
        State.AWAITING_CLOSE -> close()
        else -> LOG.warn("Unexpected state [{}] in changeSelectInterests!", state)
      }
    } catch (e: Exception) {
      LOG.error("Error changing select interests", e)
      try {
        close()
      } catch (_: Exception) {
        // ignored
      }
    }
  }

  fun close() {
    selectorThread.returnHeaderBuffer(buffer)
    selectionKey.cancel()
    try {
      channelSocket.close()
    } catch (e: Exception) {
      LOG.error("Error closing channel", e)
    }
  }

  private fun prepareRead() {
    selectionKey.interestOps(SelectionKey.OP_READ)
    buffer = selectorThread.borrowHeaderBuffer()
    state = State.READING_HEADER
  }

  private fun parseHeader(buffer: ByteBuffer): Header {
    return Header(
      buffer.get(),
      buffer.get(),
      buffer.short,
      buffer.get(),
      buffer.get(),
      buffer.short,
      buffer.int,
      buffer.int,
      buffer.long
    )
  }

  private fun internalRead(): Boolean {
    try {
      var totalBytesRead = 0
      var bytesRead: Int
      do {
        bytesRead = channelSocket.read(buffer)
        totalBytesRead += 0.coerceAtLeast(bytesRead)
      } while (bytesRead > 0 && buffer.hasRemaining())
      return totalBytesRead >= 0 || bytesRead >= 0
    } catch (e: Exception) {
      LOG.error("Encountered error while reading from channel", e)
      return false
    }
  }

  private enum class State {
    READING_HEADER,
    READING_BODY,
    READ_BODY_COMPLETE,
    AWAITING_REGISTER_WRITE,
    WRITING,
    AWAITING_REGISTER_READ,
    AWAITING_CLOSE
  }
}