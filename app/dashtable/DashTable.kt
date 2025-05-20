package app.dashtable

import com.google.common.hash.Hashing
import java.time.Clock

class DashTable<V>(segmentSize: Int, regularSize: Int, slotSize: Int, clock: Clock) {
  private val directory = SegmentDirectory<String, V>(segmentSize, regularSize, slotSize, clock)
  private val hashing = Hashing.farmHashFingerprint64()

  fun get(key: String): V? {
    return directory.get(key, hashing.hashString(key, Charsets.US_ASCII))?.value
  }

  fun put(key: String, value: V, expireTime: Long): Boolean {
    return directory.put(key, value, hashing.hashString(key, Charsets.US_ASCII), expireTime)
  }

  fun remove(key: String) {
    directory.remove(key, hashing.hashString(key, Charsets.US_ASCII))
  }

  fun containsKey(key: String): Boolean {
    return directory.get(key, hashing.hashString(key, Charsets.US_ASCII)) != null
  }
}