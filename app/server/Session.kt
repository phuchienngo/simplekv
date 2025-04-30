package app.server

import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

data class Session(
  val channel: SocketChannel,
  val selectionKey: SelectionKey,
  val selectorThread: SelectorThread,
  val requestBuffers: MutableList<ByteBuffer>,
  var response: ByteBuffer? = null
)