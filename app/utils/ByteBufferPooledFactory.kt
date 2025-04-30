package app.utils

import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.PooledObjectFactory
import org.apache.commons.pool2.impl.DefaultPooledObject
import java.nio.ByteBuffer

class ByteBufferPooledFactory(
  private val bufferSize: Int,
  private val direct: Boolean
): PooledObjectFactory<ByteBuffer> {
  override fun activateObject(`object`: PooledObject<ByteBuffer>) {
    `object`.getObject().position(0)
  }

  override fun destroyObject(`object`: PooledObject<ByteBuffer>) {
    // Do nothing
  }

  override fun makeObject(): PooledObject<ByteBuffer> {
    val buffer = if (direct) {
      ByteBuffer.allocateDirect(bufferSize)
    } else {
      ByteBuffer.allocate(bufferSize)
    }
    return DefaultPooledObject(buffer)
  }

  override fun passivateObject(`object`: PooledObject<ByteBuffer>) {
    `object`.`object`.clear()
  }

  override fun validateObject(`object`: PooledObject<ByteBuffer>): Boolean {
    val buffer = `object`.getObject()
    return buffer.capacity() == bufferSize
  }
}