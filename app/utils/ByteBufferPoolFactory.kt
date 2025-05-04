package app.utils

import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.PooledObjectFactory
import org.apache.commons.pool2.impl.DefaultPooledObject
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.ByteBuffer

class ByteBufferPoolFactory(
  private val bufferSize: Int,
  private val direct: Boolean
): PooledObjectFactory<ByteBuffer> {
  private fun cleanDirectBuffer(buffer: ByteBuffer) {
    try {
      val invokeCleaner = MethodHandles.lookup().findVirtual(
        ByteBuffer::class.java,
        "invokeCleaner",
        MethodType.methodType(Void.TYPE)
      )
      invokeCleaner.invoke(buffer)
    } catch (_: Exception) {
      try {
        val cleanerMethod = buffer.javaClass.getMethod("cleaner")
        cleanerMethod.isAccessible = true
        val cleaner = cleanerMethod.invoke(buffer)
        if (cleaner != null) {
          val cleanMethod = cleaner.javaClass.getMethod("clean")
          cleanMethod.invoke(cleaner)
        }
      } catch (_: Exception) {
        // silently continue
      }
    }
  }

  override fun activateObject(pooledObject: PooledObject<ByteBuffer>) {
    pooledObject.`object`.clear()
  }

  override fun destroyObject(pooledObject: PooledObject<ByteBuffer>) {
    val buffer = pooledObject.`object`
    if (buffer.isDirect) {
      cleanDirectBuffer(buffer)
    }
  }

  override fun makeObject(): PooledObject<ByteBuffer> {
    val buffer = if (direct) {
      ByteBuffer.allocateDirect(bufferSize)
    } else {
      ByteBuffer.allocate(bufferSize)
    }
    return DefaultPooledObject(buffer)
  }

  override fun passivateObject(pooledObject: PooledObject<ByteBuffer>) {
    pooledObject.`object`.clear()
  }

  override fun validateObject(pooledObject: PooledObject<ByteBuffer>): Boolean {
    val buffer = pooledObject.`object`
    return buffer.capacity() == bufferSize && buffer.isDirect == direct
  }
}