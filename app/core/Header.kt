package app.core

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
