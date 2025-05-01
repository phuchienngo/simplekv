package app.server

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

class Message(
  val channelSocket: SocketChannel,
  val selectionKey: SelectionKey,
  val selectorThread: SelectorThread
) {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(Message::class.java)
  }

  private var state = State.READING_HEADER
  private var buffer: ByteBuffer = ByteBuffer.allocate(24)
  private var responseBuffer: ByteBuffer? = null

  private var header: Header? = null
  private var body: Body? = null

  fun isLoaded(): Boolean {
    return state == State.READ_BODY_COMPLETE
  }

  fun read(): Boolean {
    if (state == State.READING_HEADER) {
      if (!internalRead()) {
        return false
      }

      if (!buffer.hasRemaining()) {
        buffer.flip()
        header = parseHeader(buffer)
        if (!header!!.isMagicRequest()) {
          state = State.AWAITING_CLOSE
          LOG.error("Invalid magic number in request: {}", header!!.magic)
          return false
        }
        buffer = ByteBuffer.allocate(header!!.totalBodyLength)
        state = State.READING_BODY
      }
    }

    if (state == State.READING_BODY) {
      if (!internalRead() || header == null) {
        return false
      }

      if (!buffer.hasRemaining()) {
        buffer.flip()
        state = State.READ_BODY_COMPLETE
        val extrasLength = header!!.extrasLength
        val keyLength = header!!.keyLength
        val valueLength = header!!.valueLength
        body = Body(
          if (extrasLength > 0) {
            val extras = ByteArray(extrasLength.toInt())
            buffer.get(extras)
            extras
          } else {
            null
          },
          if (keyLength > 0) {
            val key = ByteArray(keyLength.toInt())
            buffer.get(key)
            key
          } else {
            null
          },
          if (valueLength > 0) {
            val value = ByteArray(valueLength)
            buffer.get(value)
            value
          } else {
            null
          }
        )
      }
    }

    return true
  }

  fun write(): Boolean {
    if (state == State.WRITING) {
      try {
        channelSocket.write(responseBuffer)
      } catch (e: IOException) {
        LOG.error("Error writing to channel", e)
        return false
      }

      if (responseBuffer?.hasRemaining() != true) {
        prepareRead()
      }
    }

    LOG.error("Unexpected state for writing: {}", state)
    return false
  }

  fun requestInterestChange() {
    if (Thread.currentThread() === selectorThread) {
      changeSelectInterests()
    } else {
      selectorThread.requestInterestChange(this)
    }
  }

  // after process call this method
  fun registerForWriteResponse() {
    state = State.AWAITING_REGISTER_WRITE
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
        else -> {}
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

  private fun close() {
    selectionKey.cancel()
    try {
      channelSocket.close()
    } catch (e: IOException) {
      LOG.error("Error closing channel", e)
    }
  }

  private fun prepareRead() {
    selectionKey.interestOps(SelectionKey.OP_READ)
    buffer = ByteBuffer.allocate(24)
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
      return channelSocket.read(buffer) >= 0
    } catch (e: IOException) {
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

  data class Header(
    val magic: Byte,
    val opcode: Byte,
    val keyLength: Short,
    val extrasLength: Byte,
    val dataType: Byte,
    val status: Short, // vBucketId
    val totalBodyLength: Int,
    val opaque: Int,
    val cas: Long
  ) {
    fun isMagicRequest(): Boolean {
      return (magic.toInt() and 0xFF) == 0x80
    }

    fun isMagicResponse(): Boolean {
      return (magic.toInt() and 0xFF) == 0x81
    }

    val valueLength: Int
      get() = totalBodyLength - keyLength - extrasLength
  }

  data class Body(
    val extras: ByteArray?,
    val key: ByteArray?,
    val value: ByteArray?
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Body

      if (!extras.contentEquals(other.extras)) return false
      if (!key.contentEquals(other.key)) return false
      if (!value.contentEquals(other.value)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = extras?.contentHashCode() ?: 0
      result = 31 * result + (key?.contentHashCode() ?: 0)
      result = 31 * result + (value?.contentHashCode() ?: 0)
      return result
    }
  }
}