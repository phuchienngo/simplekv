package app.handler

import app.core.CommandOpCodes
import app.core.Event
import app.datastructure.SwissMap
import java.nio.ByteBuffer

class Worker(workerName: String, isSingleProducer: Boolean): AbstractWorker(workerName, isSingleProducer),
  GetHandler,
  MutateHandler,
  DeleteHandler,
  IncrementDecrementHandler,
  AppendPrependHandler {
  override val valueMap = SwissMap<String, ByteBuffer>(10_000_000, 0.75f)
  override val extrasMap = SwissMap<String, ByteBuffer>(10_000_000, 0.75f)
  override val casMap = SwissMap<String, Long>(10_000_000, 0.75f)

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
      CommandOpCodes.INCREMENT.value -> processIncrementDecrementCommand(event, CommandOpCodes.INCREMENT)
      CommandOpCodes.INCREMENTQ.value -> processIncrementDecrementCommand(event, CommandOpCodes.INCREMENTQ)
      CommandOpCodes.DECREMENT.value -> processIncrementDecrementCommand(event, CommandOpCodes.DECREMENT)
      CommandOpCodes.DECREMENTQ.value -> processIncrementDecrementCommand(event, CommandOpCodes.DECREMENTQ)
      CommandOpCodes.APPEND.value -> processAppendPrependCommand(event, CommandOpCodes.APPEND)
      CommandOpCodes.APPENDQ.value -> processAppendPrependCommand(event, CommandOpCodes.APPENDQ)
      CommandOpCodes.PREPEND.value -> processAppendPrependCommand(event, CommandOpCodes.PREPEND)
      CommandOpCodes.PREPENDQ.value -> processAppendPrependCommand(event, CommandOpCodes.PREPENDQ)
      else -> processUnknownCommand(event)
    }
  }
}