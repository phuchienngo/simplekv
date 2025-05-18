package app.dashtable

import com.google.common.hash.HashCode

class Bucket<K, V>(slotSize: Int) {
  private var slots = arrayOfNulls<Entry<K, V>>(slotSize)
  private var slotInUsed = 0

  fun put(key: K, value: V, hashCode: HashCode): Boolean {
    val existingIndex = getEntryIndex(key)
    if (existingIndex != -1) {
      slots[existingIndex]!!.value = value
      return true
    }
    if (slotInUsed >= slots.size) {
      return false
    }
    slots[slotInUsed++] = Entry(key, value, hashCode)
    return true
  }

  fun get(key: K): Entry<K, V>? {
    val index = getEntryIndex(key)
    return if (index != -1) {
      slots[index]
    } else {
      null
    }
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
}