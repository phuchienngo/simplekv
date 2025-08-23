package app.dashtable

import app.handler.CacheEntry
import com.google.common.base.Preconditions
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing

class Segment(
  private val segmentSize: Int,
  private val regularSize: Int,
  private val slotSize: Int
) {
  private var status: SegmentStatus = SegmentStatus.UNINITIALIZED
  private val buckets: Array<Bucket>
  init {
    val species = VectorSpeciesUtils.selectBestSpecies(slotSize)
    Preconditions.checkArgument(species != null)
    buckets = Array(segmentSize) { Bucket(slotSize, species!!) }
  }

  enum class SegmentStatus {
    UNINITIALIZED,
    IN_USED,
    OBSOLETED
  }

  fun getStatus(): SegmentStatus {
    return status
  }

  fun get(key: ByteArray, hashCode: HashCode, fingerprint: Byte, now: Long): Entry? {
    if (status != SegmentStatus.IN_USED) {
      return null
    }

    val index = Hashing.consistentHash(hashCode, regularSize)
    var result = buckets[index].get(key, fingerprint, now)
    if (result != null) {
      return result
    }
    if (index + 1 < regularSize) {
      result = buckets[index + 1].get(key, fingerprint, now)
      if (result != null) {
        return result
      }
    }
    for (stashIndex in regularSize until segmentSize) {
      result = buckets[stashIndex].get(key, fingerprint, now) ?: continue
      return result
    }
    return null
  }

  fun put(key: ByteArray, value: CacheEntry, hashCode: HashCode, fingerprint: Byte, expireTime: Long): Boolean {
    if (status != SegmentStatus.IN_USED) {
      return false
    }

    val index = Hashing.consistentHash(hashCode, regularSize)
    if (buckets[index].put(key, value, hashCode, fingerprint, expireTime)
      || index + 1 < regularSize && buckets[index + 1].put(key, value, hashCode, fingerprint, expireTime)
    ) {
      return true
    }
    for (stashIndex in regularSize until segmentSize) {
      if (buckets[stashIndex].put(key, value, hashCode, fingerprint, expireTime)) {
        return true
      }
    }
    return false
  }

  fun remove(key: ByteArray, fingerprint: Byte, hashCode: HashCode): Boolean {
    if (status != SegmentStatus.IN_USED) {
      return false
    }

    val index = Hashing.consistentHash(hashCode, regularSize)
    if (buckets[index].remove(key, fingerprint)) {
      return true
    }
    if (index + 1 < regularSize && buckets[index + 1].remove(key, fingerprint)) {
      return true
    }
    for (stashIndex in regularSize until segmentSize) {
      if (buckets[stashIndex].remove(key, fingerprint)) {
        return true
      }
    }
    return false
  }

  fun use() {
    status = SegmentStatus.IN_USED
  }

  fun obsolete(): Array<Bucket> {
    status = SegmentStatus.OBSOLETED
    return buckets
  }

  fun cleanExpiredEntries(now: Long): Boolean {
    var isSuccess = false
    for (bucket in buckets) {
      isSuccess = isSuccess || bucket.cleanExpiredItems(now)
    }
    return isSuccess
  }
}