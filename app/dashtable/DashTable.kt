package app.dashtable

import app.handler.CacheEntry
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import java.time.Clock

class DashTable(segmentSize: Int, regularSize: Int, slotSize: Int, clock: Clock) {
  private val directory = SegmentDirectory(segmentSize, regularSize, slotSize, clock)
  private val hashing = Hashing.sipHash24()

  fun get(key: ByteArray): CacheEntry? {
    val hashCode = hashing.hashBytes(key)
    val fingerprint = calcFingerprint(hashCode)
    return directory.get(key, hashCode, fingerprint)?.value
  }

  fun put(key: ByteArray, value: CacheEntry, expireTime: Long): Boolean {
    val hashCode = hashing.hashBytes(key)
    val fingerprint = calcFingerprint(hashCode)
    return directory.put(key, value, hashCode, fingerprint, expireTime)
  }

  fun remove(key: ByteArray) {
    val hashCode = hashing.hashBytes(key)
    val fingerprint = calcFingerprint(hashCode)
    directory.remove(key, hashCode, fingerprint)
  }

  fun containsKey(key: ByteArray): Boolean {
    val hashCode = hashing.hashBytes(key)
    val fingerprint = calcFingerprint(hashCode)
    return directory.get(key, hashCode, fingerprint) != null
  }

  private fun calcFingerprint(hashCode: HashCode): Byte {
    val hash64 = hashCode.asLong()
    return ((hash64 ushr 56) and 0xFF).toByte()
  }
}