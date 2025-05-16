package app.dashtable

import com.google.common.hash.HashCode

class Bucket<K, V>(private val slot: Int) {
  private var slots = arrayOfNulls<Entry<K, V>>(slot)
  private var size = 0

  fun put(key: K, value: V, hashCode: HashCode): Boolean {
    val existingIndex = getEntryIndex(key)
    if (existingIndex != -1) {
      slots[existingIndex]!!.value = value
    } else {
      if (size >= slot) {
        return false
      }
      slots[size++] = Entry(key, value, hashCode)
    }

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
    for (i in 0 until size) {
      slots[i] = null
    }
    size = 0
    slots = arrayOfNulls(0)
  }

  fun remove(key: K): Boolean {
    val index = getEntryIndex(key)
    if (index != -1) {
      slots[index] = null
      size--
      return true
    }
    return false
  }

  private fun getEntryIndex(key: K): Int {
    return (0 until size).firstOrNull { index ->
      slots[index]?.key == key
    } ?: -1
  }

  fun entries(): List<Entry<K, V>> {
    return slots.filterNotNull()
  }
}