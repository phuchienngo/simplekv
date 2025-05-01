package app.core

import java.nio.ByteBuffer

data class Body(
  val extras: ByteBuffer?,
  val key: ByteBuffer?,
  val value: ByteBuffer?
)