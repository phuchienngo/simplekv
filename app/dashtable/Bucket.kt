package app.dashtable

import app.handler.CacheEntry
import com.google.common.hash.HashCode
import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorMask
import jdk.incubator.vector.VectorSpecies
import java.util.Arrays

class Bucket(private val slotSize: Int, private val species: VectorSpecies<Byte>) {
  private val slots = arrayOfNulls<Entry>(slotSize)
  private val fingerprints = ByteArray(slotSize)
  private val occupancy = ByteArray(slotSize)
  private val onesVec = ByteVector.broadcast(species, 1.toByte())
  private val zerosVec = ByteVector.broadcast(species, 0.toByte())

  fun put(key: ByteArray, value: CacheEntry, hashCode: HashCode, fingerprint: Byte, expireTime: Long): Boolean {
    val upperbound = species.loopBound(slotSize)
    for (offset in 0 until upperbound step species.length()) {
      if (tryReplaceSlot(fingerprint, offset, key, value, hashCode, expireTime)) {
        return true
      }
    }
    val remaining = slotSize - upperbound
    if (remaining > 0) {
      for (i in upperbound until slotSize) {
        val entry = slots[i]
        if (entry != null && contentEquals(entry.key, key)) {
          entry.value = value
          return true
        }
      }
    }

    for (offset in 0 until upperbound step species.length()) {
      if (checkAndSetSlotEntry(offset, key, value, hashCode, fingerprint, expireTime)) {
        return true
      }
    }

    if (remaining > 0) {
      for (i in upperbound until slotSize) {
        if (1.compareTo(occupancy[i]) == 0) {
          continue
        }
        slots[i] = Entry(key, value, hashCode, fingerprint, expireTime)
        fingerprints[i] = fingerprint
        occupancy[i] = 1
        return true
      }
    }

    return false
  }

  private fun tryReplaceSlot(fingerprint: Byte, offset: Int, key: ByteArray, value: CacheEntry, hashCode: HashCode, expireTime: Long): Boolean {
    val maybeCandidates = findCandidatesVector(fingerprint, offset)
    if (!maybeCandidates.anyTrue()) {
      return checkAndSetSlotEntry(offset, key, value, hashCode, fingerprint, expireTime)
    }

    val exactSlot = findExactCandidateVector(maybeCandidates, key)
    if (exactSlot != -1) {
      slots[exactSlot + offset]!!.value = value
      return true
    }
    return false
  }

  private fun checkAndSetSlotEntry(offset: Int, key: ByteArray, value: CacheEntry, hashCode: HashCode, fingerprint: Byte, expireTime: Long): Boolean {
    var freeSlot = firstFreeSlot(offset)
    if (freeSlot == -1) {
      return false
    }
    freeSlot += offset
    slots[freeSlot] = Entry(key, value, hashCode, fingerprint, expireTime)
    fingerprints[freeSlot] = fingerprint
    occupancy[freeSlot] = 1
    return true
  }

  fun get(key: ByteArray, fingerprint: Byte, now: Long): Entry? {
    val upperbound = species.loopBound(slotSize)
    for (offset in 0 until upperbound step species.length()) {
      val maybeCandidates = findCandidatesVector(fingerprint, offset)
      if (!maybeCandidates.anyTrue()) {
        continue
      }
      val index = findExactCandidateVector(maybeCandidates, key)
      if (index < 0) {
        continue
      }

      val entry = slots[index + offset]
      if (isExpiredEntry(entry!!, now)) {
        remove(key, fingerprint)
        return null
      }
      return entry
    }
    val remaining = slotSize - upperbound
    if (remaining <= 0) {
      return null
    }

    for (i in upperbound until slotSize) {
      val entry = slots[i]
      if (entry != null && contentEquals(entry.key, key)) {
        if (isExpiredEntry(entry, now)) {
          remove(key, fingerprint)
          return null
        }
        return entry
      }
    }

    return null
  }

  fun clear() {
    Arrays.fill(slots, null)
    Arrays.fill(occupancy, 0)
    Arrays.fill(fingerprints, 0)
  }

  fun remove(key: ByteArray, fingerprint: Byte): Boolean {
    val upperbound = species.loopBound(slotSize)
    for (offset in 0 until upperbound step species.length()) {
      val maybeCandidates = findCandidatesVector(fingerprint, offset)
      if (!maybeCandidates.anyTrue()) {
        continue
      }
      val index = findExactCandidateVector(maybeCandidates, key)
      if (index == -1) {
        continue
      }
      slots[index + offset] = null
      occupancy[index + offset] = 0
      fingerprints[index + offset] = 0
      return true
    }
    val remaining = slotSize - upperbound
    if (remaining <= 0) {
      return false
    }

    for (i in upperbound until slotSize) {
      val entry = slots[i]
      if (entry != null && contentEquals(entry.key, key)) {
        slots[i] = null
        occupancy[i] = 0
        fingerprints[i] = 0
        return true
      }
    }

    return false
  }

  fun entries(): List<Entry> {
    return slots.filterNotNull()
  }

  fun cleanExpiredItems(now: Long): Boolean {
    val upperbound = species.loopBound(slotSize)
    var totalRemoved = 0
    for (offset in 0 until upperbound step species.length()) {
      totalRemoved += cleanExpiredItemsOffset(now, offset)
    }

    val remaining = slotSize - upperbound
    if (remaining > 0) {
      for (i in upperbound until slotSize) {
        val entry = slots[i]
        if (entry != null && isExpiredEntry(entry, now)) {
          slots[i] = null
          occupancy[i] = 0
          fingerprints[i] = 0
          totalRemoved += 1
        }
      }
    }
    return totalRemoved > 0
  }

  private fun cleanExpiredItemsOffset(now: Long, offset: Int): Int {
    val occupancyVec = ByteVector.fromArray(species, occupancy, offset)
    val occupancyMask = occupancyVec.eq(onesVec)
    val maskLong = occupancyMask.toLong()
    var bits = maskLong
    var removed = 0

    while (bits != 0L) {
      val laneIndex = bits.countTrailingZeroBits()
      val entry = slots[laneIndex]
      if (entry != null && isExpiredEntry(entry, now)) {
        slots[laneIndex] = null
        occupancy[laneIndex] = 0
        fingerprints[laneIndex] = 0
        removed += 1
      }
      bits = bits and (bits - 1)
    }
    return removed
  }

  private fun isExpiredEntry(entry: Entry, now: Long): Boolean {
    return entry.expireTime != 0L && entry.expireTime <= now
  }

  private fun findCandidatesVector(fingerprint: Byte, offset: Int): VectorMask<Byte> {
    val occupancyVec = ByteVector.fromArray(species, occupancy, offset)
    val occupancyMask = occupancyVec.eq(onesVec)

    val fingerprintVec = ByteVector.fromArray(species, fingerprints, offset)
    val testFingerprintVec = ByteVector.broadcast(species, fingerprint)
    val fingerprintMask = fingerprintVec.eq(testFingerprintVec)
    return occupancyMask.and(fingerprintMask)
  }

  private fun firstFreeSlot(offset: Int): Int {
    val occupancyVec = ByteVector.fromArray(species, occupancy, offset)
    val freeMask = occupancyVec.eq(zerosVec)
    return if (freeMask.anyTrue()) {
      freeMask.firstTrue()
    } else {
      -1
    }
  }

  private fun findExactCandidateVector(maybeCandidates: VectorMask<Byte>, key: ByteArray): Int {
    val maskLong = maybeCandidates.toLong()
    var bits = maskLong

    while (bits != 0L) {
      val laneIndex = bits.countTrailingZeroBits()
      val entry = slots[laneIndex]
      if (entry != null && contentEquals(entry.key, key)) {
        return laneIndex
      }
      bits = bits and (bits - 1)
    }

    return -1
  }

  private fun contentEquals(array1: ByteArray, array2: ByteArray): Boolean {
    if (array1.size != array2.size) {
      return false
    }
    if (array1 === array2) {
      return true
    }
    if (array1.isEmpty()) {
      return true
    }

    val upperbound = species.loopBound(array1.size)
    for (offset in 0 until upperbound step species.length()) {
      val vec1 = ByteVector.fromArray(species, array1, offset)
      val vec2 = ByteVector.fromArray(species, array2, offset)
      if (vec1.eq(vec2).allTrue()) {
        continue
      }
      return false
    }

    val remaining = array2.size - upperbound
    if (remaining <= 0) {
      return true
    }
    for (i in upperbound until remaining) {
      if (array1[i] != array2[i]) {
        return false
      }
    }

    return true
  }
}