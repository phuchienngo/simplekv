package app.dashtable

import app.handler.CacheEntry
import com.google.common.hash.HashCode
import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorMask
import jdk.incubator.vector.VectorSpecies
import java.util.Arrays

class Bucket(slotSize: Int, private val species: VectorSpecies<Byte>) {
  private val slots = arrayOfNulls<Entry>(slotSize)
  private val fingerprints = ByteArray(slotSize)
  private val occupancy = ByteArray(slotSize)
  private val onesVec = ByteVector.broadcast(species, 1.toByte())
  private val zerosVec = ByteVector.broadcast(species, 0.toByte())

  fun put(key: ByteArray, value: CacheEntry, hashCode: HashCode, fingerprint: Byte, expireTime: Long): Boolean {
    val maybeCandidates = findCandidates(fingerprint)
    if (!maybeCandidates.anyTrue()) {
      return checkAndSetSlotEntry(key, value, hashCode, fingerprint, expireTime)
    }

    val exactSlot = findExactCandidate(maybeCandidates, key)
    if (exactSlot != -1) {
      slots[exactSlot]!!.value = value
      return true
    }
    return checkAndSetSlotEntry(key, value, hashCode, fingerprint, expireTime)
  }

  private fun checkAndSetSlotEntry(key: ByteArray, value: CacheEntry, hashCode: HashCode, fingerprint: Byte, expireTime: Long): Boolean {
    val freeSlot = firstFreeSlot()
    if (freeSlot == -1) {
      return false
    }

    slots[freeSlot] = Entry(key, value, hashCode, fingerprint, expireTime)
    fingerprints[freeSlot] = fingerprint
    occupancy[freeSlot] = 1
    return true
  }

  fun get(key: ByteArray, fingerprint: Byte, now: Long): Entry? {
    val maybeCandidates = findCandidates(fingerprint)
    if (!maybeCandidates.anyTrue()) {
      return null
    }
    val index = findExactCandidate(maybeCandidates, key)
    if (index == -1) {
      return null
    }
    val entry = slots[index]
    if (isExpiredEntry(entry!!, now)) {
      remove(key, fingerprint)
      return null
    }
    return entry
  }

  fun clear() {
    Arrays.fill(slots, null)
    Arrays.fill(occupancy, 0)
    Arrays.fill(fingerprints, 0)
  }

  fun remove(key: ByteArray, fingerprint: Byte): Boolean {
    val maybeCandidates = findCandidates(fingerprint)
    if (!maybeCandidates.anyTrue()) {
      return false
    }
    val index = findExactCandidate(maybeCandidates, key)
    if (index == -1) {
      return false
    }
    slots[index] = null
    occupancy[index] = 0
    fingerprints[index] = 0
    return true
  }

  fun entries(): List<Entry> {
    return slots.filterNotNull()
  }

  fun cleanExpiredItems(now: Long): Boolean {
    val occupancyVec = ByteVector.fromArray(species, occupancy, 0)
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
    return removed > 0
  }

  private fun isExpiredEntry(entry: Entry, now: Long): Boolean {
    return entry.expireTime != 0L && entry.expireTime <= now
  }

  private fun findCandidates(fingerprint: Byte): VectorMask<Byte> {
    val occupancyVec = ByteVector.fromArray(species, occupancy, 0)
    val occupancyMask = occupancyVec.eq(onesVec)

    val fingerprintVec = ByteVector.fromArray(species, fingerprints, 0)
    val testFingerprintVec = ByteVector.broadcast(species, fingerprint)
    val fingerprintMask = fingerprintVec.eq(testFingerprintVec)
    return occupancyMask.and(fingerprintMask)
  }

  private fun firstFreeSlot(): Int {
    val occupancyVec = ByteVector.fromArray(species, occupancy, 0)
    val freeMask = occupancyVec.eq(zerosVec)
    return if (freeMask.anyTrue()) {
      freeMask.firstTrue()
    } else {
      -1
    }
  }

  private fun findExactCandidate(maybeCandidates: VectorMask<Byte>, key: ByteArray): Int {
    val maskLong = maybeCandidates.toLong()
    var bits = maskLong

    while (bits != 0L) {
      val laneIndex = bits.countTrailingZeroBits()
      val entry = slots[laneIndex]
      if (entry != null && entry.key.contentEquals(key)) {
        return laneIndex
      }
      bits = bits and (bits - 1)
    }

    return -1
  }
}