package app.dashtable

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing

class Segment<K, V>(
  private val segmentSize: Int,
  private val regularSize: Int,
  private val buckets: Array<Bucket<K, V>>,
) {
  private var status: SegmentStatus = SegmentStatus.UNINITIALIZED

  constructor(segmentSize: Int, regularSize: Int, slotSize: Int):
      this(segmentSize, regularSize, Array(segmentSize) { Bucket(slotSize) })

  enum class SegmentStatus {
    UNINITIALIZED,
    IN_USED,
    OBSOLETED
  }

  fun getStatus(): SegmentStatus {
    return status
  }

  fun get(key: K, hashCode: HashCode): Entry<K, V>? {
    if (status != SegmentStatus.IN_USED) {
      return null
    }

    val index = Hashing.consistentHash(hashCode, regularSize)
    var result = buckets[index].get(key)
    if (result != null) {
      return result
    }
    if (index + 1 < regularSize) {
      result = buckets[index + 1].get(key)
      if (result != null) {
        return result
      }
    }
    for (stashIndex in regularSize until segmentSize) {
      result = buckets[stashIndex].get(key) ?: continue
      return result
    }
    return null
  }

  fun put(key: K, value: V, hashCode: HashCode): Boolean {
    if (status != SegmentStatus.IN_USED) {
      return false
    }

    val index = Hashing.consistentHash(hashCode, regularSize)
    if (buckets[index].put(key, value, hashCode) || index + 1 < regularSize && buckets[index + 1].put(key, value, hashCode)) {
      return true
    }
    for (stashIndex in regularSize until segmentSize) {
      if (buckets[stashIndex].put(key, value, hashCode)) {
        return true
      }
    }
    return false
  }

  fun remove(key: K, hashCode: HashCode): Boolean {
    if (status != SegmentStatus.IN_USED) {
      return false
    }

    val index = Hashing.consistentHash(hashCode, regularSize)
    if (buckets[index].remove(key)) {
      return true
    }
    if (index + 1 < regularSize && buckets[index + 1].remove(key)) {
      return true
    }
    for (stashIndex in regularSize until segmentSize) {
      if (buckets[stashIndex].remove(key)) {
        return true
      }
    }
    return false
  }

  fun use() {
    status = SegmentStatus.IN_USED
  }

  fun obsolete(): Array<Bucket<K, V>> {
    status = SegmentStatus.OBSOLETED
    return buckets
  }
}