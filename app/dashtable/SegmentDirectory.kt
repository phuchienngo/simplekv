package app.dashtable

import com.google.common.hash.HashCode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.pow

class SegmentDirectory<K, V>(
  private val segmentSize: Int,
  private val regularSize: Int,
  private val slotSize: Int,
) {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(SegmentDirectory::class.java)
  }
  private var segments = Array<Segment<K, V>>(1) {
    val segment = Segment<K, V>(segmentSize, regularSize, slotSize)
    segment.use()
    return@Array segment
  }

  fun put(key: K, value: V, hashCode: HashCode): Boolean {
    var bitIndex = -1
    var segmentIndex = 0
    while (bitIndex < hashCode.bits()
      && segmentIndex < segments.size
      && segments[segmentIndex].getStatus() != Segment.SegmentStatus.UNINITIALIZED
    ) {
      val segment = segments[segmentIndex]
      when (segment.getStatus()) {
        Segment.SegmentStatus.IN_USED -> {
          if (segment.put(key, value, hashCode)) {
            return true
          }
          splitSegment(segment, segmentIndex, bitIndex + 1)
        }
        Segment.SegmentStatus.OBSOLETED -> {
          segmentIndex = if (isBitSet(hashCode, ++bitIndex)) {
            2 * segmentIndex + 2
          } else {
            2 * segmentIndex + 1
          }
        }
        else -> {
          LOG.error("Invalid segment status: {}", segment.getStatus())
          break
        }
      }
    }
    return false
  }

  fun get(key: K, hashCode: HashCode): Entry<K, V>? {
    var bitIndex = -1
    var segmentIndex = 0
    while (bitIndex < hashCode.bits()
      && segmentIndex < segments.size
      && segments[segmentIndex].getStatus() != Segment.SegmentStatus.UNINITIALIZED
    ) {
      val segment = segments[segmentIndex]
      if (segment.getStatus() == Segment.SegmentStatus.IN_USED) {
        return segment.get(key, hashCode)
      }
      segmentIndex = if (isBitSet(hashCode, ++bitIndex)) {
        2 * segmentIndex + 2
      } else {
        2 * segmentIndex + 1
      }
    }
    return null
  }

  fun remove(key: K, hashCode: HashCode): Boolean {
    var bitIndex = -1
    var segmentIndex = 0
    while (bitIndex < hashCode.bits()
      && segmentIndex < segments.size
      && segments[segmentIndex].getStatus() != Segment.SegmentStatus.UNINITIALIZED
    ) {
      val segment = segments[segmentIndex]
      if (segment.getStatus() == Segment.SegmentStatus.IN_USED) {
        return segment.remove(key, hashCode)
      }
      segmentIndex = if (isBitSet(hashCode, ++bitIndex)) {
        2 * segmentIndex + 2
      } else {
        2 * segmentIndex + 1
      }
    }
    return false
  }

  private fun isBitSet(hashCode: HashCode, bitIndex: Int): Boolean {
    if (bitIndex in 0..63) {
      return ((hashCode.padToLong() shr bitIndex) and 1L) == 1L
    }

    val bytes = hashCode.asBytes()
    val byteIndex = bitIndex / 8

    if (byteIndex >= bytes.size) {
      throw IndexOutOfBoundsException("Bit index out of bounds")
    }

    val bitPosition = bitIndex % 8
    return ((bytes[byteIndex].toInt() shr bitPosition) and 1) == 1
  }

  private fun splitSegment(segment: Segment<K, V>, segmentIndex: Int, bitIndex: Int) {
    val oldBuckets = segment.obsolete()
    val offBitIndex = segmentIndex * 2 + 1
    val onBitIndex = segmentIndex * 2 + 2
    val offBitSegment = segments.getOrNull(offBitIndex) ?: Segment(segmentSize, regularSize, slotSize)
    val onBitSegment = segments.getOrNull(onBitIndex) ?: Segment(segmentSize, regularSize, slotSize)
    offBitSegment.use()
    onBitSegment.use()

    if (onBitIndex >= segments.size) {
      val newSegments = Array(segments.size + 2.0.pow(bitIndex.toDouble()).toInt()) {
        return@Array when {
          it < segments.size -> segments[it]
          it == offBitIndex -> offBitSegment
          it == onBitIndex -> onBitSegment
          else -> Segment(segmentSize, regularSize, slotSize)
        }
      }
      segments = newSegments
    }
    for (i in 0 until oldBuckets.size) {
      rehashing(oldBuckets[i], bitIndex, onBitSegment, offBitSegment)
    }
  }

  private fun rehashing(bucket: Bucket<K, V>, bitIndex: Int, onBitSegment: Segment<K, V>, offBitSegment: Segment<K, V>) {
    for (entry in bucket.entries()) {
      if (isBitSet(entry.hashCode, bitIndex)) {
        onBitSegment.put(entry.key, entry.value, entry.hashCode)
      } else {
        offBitSegment.put(entry.key, entry.value, entry.hashCode)
      }
    }
    bucket.clear()
  }
}