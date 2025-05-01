package app.core

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