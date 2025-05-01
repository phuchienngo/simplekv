package app.handler

import app.core.CommandOpCodes
import app.core.Event
import app.datastructure.SwissMap
import com.lmax.disruptor.dsl.Disruptor
import java.nio.ByteBuffer
import java.nio.charset.CharsetDecoder

class Worker(disruptor: Disruptor<Event>): AbstractWorker(disruptor), GetHandler, MutateHandler, DeleteHandler {
  override val valueMap = SwissMap<String, ByteBuffer>()
  override val extrasMap = SwissMap<String, ByteBuffer>()
  override val casMap = SwissMap<String, Long>()
  override val decoder: CharsetDecoder = Charsets.US_ASCII.newDecoder()

  override fun process(event: Event) {
    when (event.header.opcode) {
      CommandOpCodes.GET.value -> processGetCommand(event, CommandOpCodes.GET)
      CommandOpCodes.GETQ.value -> processGetCommand(event, CommandOpCodes.GETQ)
      CommandOpCodes.GETK.value -> processGetCommand(event, CommandOpCodes.GETK)
      CommandOpCodes.GETKQ.value -> processGetCommand(event, CommandOpCodes.GETKQ)
      CommandOpCodes.SET.value -> processMutateCommand(event, CommandOpCodes.SET)
      CommandOpCodes.SETQ.value -> processMutateCommand(event, CommandOpCodes.SETQ)
      CommandOpCodes.ADD.value -> processMutateCommand(event, CommandOpCodes.ADD)
      CommandOpCodes.ADDQ.value -> processMutateCommand(event, CommandOpCodes.ADDQ)
      CommandOpCodes.REPLACE.value -> processMutateCommand(event, CommandOpCodes.REPLACE)
      CommandOpCodes.REPLACEQ.value -> processMutateCommand(event, CommandOpCodes.REPLACEQ)
      CommandOpCodes.DELETE.value -> processDeleteCommand(event, CommandOpCodes.DELETE)
      CommandOpCodes.DELETEQ.value -> processDeleteCommand(event, CommandOpCodes.DELETEQ)
      else -> processUnknownCommand(event)
    }
  }
}