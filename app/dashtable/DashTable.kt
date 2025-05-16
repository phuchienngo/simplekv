package app.dashtable

import com.google.common.hash.Hashing

class DashTable<V>(segmentSize: Int, regularSize: Int, slotSize: Int) {
  private val directory = SegmentDirectory<String, V>(segmentSize, regularSize, slotSize)
  private val hashing = Hashing.farmHashFingerprint64()

  fun get(key: String): V? {
    return directory.get(key, hashing.hashString(key, Charsets.US_ASCII))?.value
  }

  fun put(key: String, value: V): Boolean {
    return directory.put(key, value, hashing.hashString(key, Charsets.US_ASCII))
  }

  fun remove(key: String): V? {
    val value = get(key) ?: return null
    directory.remove(key, hashing.hashString(key, Charsets.US_ASCII))
    return value
  }

  fun containsKey(key: String): Boolean {
    return directory.get(key, hashing.hashString(key, Charsets.US_ASCII)) != null
  }
}