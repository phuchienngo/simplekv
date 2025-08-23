package app.dashtable

import app.handler.CacheEntry
import com.google.common.hash.HashCode

data class Entry(val key: ByteArray, var value: CacheEntry, val hashCode: HashCode, val fingerprint: Byte, val expireTime: Long) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    return other as? Entry != null
        && key.contentEquals(other.key)
        && value == other.value
        && expireTime == other.expireTime
        && fingerprint == other.fingerprint
        && hashCode == other.hashCode
  }

  override fun hashCode(): Int {
    var result = expireTime.hashCode()
    result = 31 * result + key.contentHashCode()
    result = 31 * result + fingerprint.hashCode()
    result = 31 * result + value.hashCode()
    result = 31 * result + hashCode.hashCode()
    return result
  }
}