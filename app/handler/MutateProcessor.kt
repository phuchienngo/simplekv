package app.handler

import app.allocator.MemoryAllocator
import app.core.CommandOpCodes
import app.core.Event
import app.core.ErrorCode
import app.dashtable.DashTable
import app.utils.Commands
import app.utils.Responses
import app.utils.Validators
import java.nio.ByteBuffer
import java.time.Clock

class MutateProcessor(
  private val dashTable: DashTable<CacheEntry>,
  private val memoryAllocator: MemoryAllocator,
  private val clock: Clock,
): BaseProcessor() {
  override fun process(event: Event, command: CommandOpCodes) {
    if (!Validators.hasExtras(event) || !Validators.hasKey(event)) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.InvalidArguments)
      event.reply(response)
      return
    }
    val key = decodeKey(event.body.key!!)
    var cacheEntry = dashTable.get(key)
    val currentCas = cacheEntry?.cas
    val requestCas = event.header.cas
    if (requestCas != 0L && requestCas != currentCas) {
      val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyExists)
      event.reply(response)
      return
    }
    val extras = event.body.extras
    val value = event.body.value ?: ByteBuffer.allocate(0)
    val now = System.currentTimeMillis()
    if (cacheEntry == null) {
      cacheEntry = CacheEntry(null, null, null)
    }

    val ttl = extractTTLFromExtras(extras)
    when (command) {
      CommandOpCodes.SET, CommandOpCodes.SETQ -> {
        addOrUpdateAndReply(command, event, key, value, extras, now, ttl, cacheEntry)
      }
      CommandOpCodes.ADD, CommandOpCodes.ADDQ -> {
        if (dashTable.containsKey(key)) {
          val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyExists)
          event.reply(response)
          return
        }
        addOrUpdateAndReply(command, event, key, value, extras, now, ttl, cacheEntry)
      }
      CommandOpCodes.REPLACE, CommandOpCodes.REPLACEQ -> {
        if (!dashTable.containsKey(key)) {
          val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.KeyNotFound)
          event.reply(response)
          return
        }
        addOrUpdateAndReply(command, event, key, value, extras, now, ttl, cacheEntry)
      }
      else -> {} // never reached
    }
  }

  private fun addOrUpdateAndReply(command: CommandOpCodes, event: Event, key: String, value: ByteBuffer?, extras: ByteBuffer?, cas: Long, ttl: Int, cacheEntry: CacheEntry) {
    cacheEntry.value?.let(memoryAllocator::freeBlock)
    cacheEntry.extra?.let(memoryAllocator::freeBlock)
    cacheEntry.value = value?.let {
      return@let memoryAllocator.allocateBlock(it.remaining()).apply {
        buffer.put(it.duplicate())
        buffer.flip()
      }
    }
    cacheEntry.extra = extras?.let {
      return@let memoryAllocator.allocateBlock(it.remaining()).apply {
        buffer.put(it.duplicate())
        buffer.flip()
      }
    }
    cacheEntry.cas = cas
    val expirationTime = if (ttl > 0L) {
      clock.millis() + ttl * 1000L
    } else {
      0L
    }
    dashTable.put(key, cacheEntry, expirationTime)
    if (Commands.isQuietCommand(command)) {
      event.done()
    } else {
      val response = Responses.makeResponse(event.responseBuffer, event.header, cas, null, null, null)
      event.reply(response)
    }
  }

  private fun extractTTLFromExtras(extras: ByteBuffer?): Int {
    if (extras == null) {
      return 0
    }
    val duplicated = extras.duplicate()
    if (duplicated.remaining() < 8) {
      return 0
    }
    duplicated.int // skip 4 bytes
    return duplicated.int
  }
}