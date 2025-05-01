package app.utils

import app.core.Header
import app.core.ResponseStatus
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object Responses {
  fun makeError(header: Header, error: ResponseStatus): ByteBuffer {
    val errorMessage = error.description.toByteArray(StandardCharsets.US_ASCII)
    val errorLength = errorMessage.size
    val responseBuffer = ByteBuffer.allocate(24 + errorLength)
    responseBuffer.put(0x81.toByte()) // magic
    responseBuffer.put(0) // opcode
    responseBuffer.putShort(0) // key length
    responseBuffer.put(0) // extras length
    responseBuffer.put(0) // data type
    responseBuffer.putShort(error.code) // status
    responseBuffer.putInt(errorLength) // total body length
    responseBuffer.putInt(header.opaque) // opaque
    responseBuffer.putLong(0) // cas
    responseBuffer.put(errorMessage) // error message
    responseBuffer.flip()
    return responseBuffer
  }

  fun makeResponse(header: Header, cas: Long, extras: ByteBuffer?, key: ByteBuffer?, value: ByteBuffer?): ByteBuffer {
    val extrasLength = extras?.capacity() ?: 0
    val keyLength = key?.capacity() ?: 0
    val valueLength = value?.capacity() ?: 0
    val totalBodyLength = extrasLength + keyLength + valueLength
    val responseBuffer = ByteBuffer.allocate(24 + totalBodyLength)
    responseBuffer.put(0x81.toByte()) // magic
    responseBuffer.put(header.opcode) // opcode
    responseBuffer.putShort(keyLength.toShort()) // key length
    responseBuffer.put(extrasLength.toByte()) // extras length
    responseBuffer.put(0) // data type
    responseBuffer.putShort(0) // status
    responseBuffer.putInt(totalBodyLength) // total body length
    responseBuffer.putInt(header.opaque) // opaque
    responseBuffer.putLong(cas) // cas
    extras?.let(responseBuffer::put)
    key?.let(responseBuffer::put)
    value?.let(responseBuffer::put)
    responseBuffer.flip()
    return responseBuffer
  }
}