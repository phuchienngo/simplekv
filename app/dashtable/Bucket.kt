package app.dashtable

import com.google.common.hash.HashCode

class Bucket<K, V>(slotSize: Int) {
  private var slots = arrayOfNulls<Entry<K, V>>(slotSize)
  private var slotInUsed = 0

  fun put(key: K, value: V, hashCode: HashCode, expireTime: Long): Boolean {
    val existingIndex = getEntryIndex(key)
    if (existingIndex != -1) {
      slots[existingIndex]!!.value = value
      return true
    }
    if (slotInUsed >= slots.size) {
      return false
    }
    slots[slotInUsed++] = Entry(key, value, hashCode, expireTime)
    return true
  }

  fun get(key: K, now: Long): Entry<K, V>? {
    val index = getEntryIndex(key)
    if (index == -1) {
      return null
    }
    val entry = slots[index]
    if (isExpiredEntry(entry!!, now)) {
      remove(key)
      return null
    }
    return entry
  }

  fun clear() {
    for (i in 0 until slots.size) {
      slots[i] = null
    }
    slotInUsed = 0
    slots = arrayOfNulls(0)
  }

  fun remove(key: K): Boolean {
    val index = getEntryIndex(key)
    if (index == -1) {
      return false
    }
    if (index < slotInUsed - 1) {
      for (i in index until slotInUsed - 1) {
        slots[i] = slots[i + 1]
      }
    }
    slots[--slotInUsed] = null
    return true
  }

  private fun getEntryIndex(key: K): Int {
    return (0 until slotInUsed).firstOrNull { index ->
      slots[index]?.key == key
    } ?: -1
  }

  fun entries(): List<Entry<K, V>> {
    return slots.filterNotNull()
  }

  fun cleanExpiredItems(now: Long): Boolean {
    var newSize = 0
    for (i in 0 until slotInUsed) {
      val entry = slots[i] ?: continue
      if (isExpiredEntry(entry, now)) {
        slots[i] = null
      } else {
        if (newSize != i) {
          slots[newSize] = entry
          slots[i] = null
        }
        newSize++
      }
    }
    val removed = slotInUsed - newSize
    slotInUsed = newSize
    return removed > 0
  }

  private fun isExpiredEntry(entry: Entry<K, V>, now: Long): Boolean {
    return entry.expireTime != 0L && entry.expireTime <= now
  }
}