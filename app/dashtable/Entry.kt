package app.dashtable

import com.google.common.hash.HashCode

data class Entry<K, V>(val key: K, var value: V, val hashCode: HashCode)