package app.server

import com.lmax.disruptor.EventFactory

data class Container<T>(var value: T? = null) {
  companion object {
    val FACTORY = EventFactory { Container<Any>() }
  }
}
