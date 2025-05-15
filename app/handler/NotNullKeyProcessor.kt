package app.handler

import app.core.CommandOpCodes
import app.core.ErrorCode
import app.core.Event
import app.utils.Responses

class NotNullKeyProcessor(
  private val appendPrependProcessor: AppendPrependProcessor,
  private val deleteProcessor: DeleteProcessor,
  private val getProcessor: GetProcessor,
  private val incrementDecrementProcessor: IncrementDecrementProcessor,
  private val mutateProcessor: MutateProcessor,
) {

  fun handleNotNullKeyRequest(event: Event) {
    when (event.header.opcode) {
      CommandOpCodes.GET.value -> getProcessor.process(event, CommandOpCodes.GET)
      CommandOpCodes.GETQ.value -> getProcessor.process(event, CommandOpCodes.GETQ)
      CommandOpCodes.GETK.value -> getProcessor.process(event, CommandOpCodes.GETK)
      CommandOpCodes.GETKQ.value -> getProcessor.process(event, CommandOpCodes.GETKQ)
      CommandOpCodes.SET.value -> mutateProcessor.process(event, CommandOpCodes.SET)
      CommandOpCodes.SETQ.value -> mutateProcessor.process(event, CommandOpCodes.SETQ)
      CommandOpCodes.ADD.value -> mutateProcessor.process(event, CommandOpCodes.ADD)
      CommandOpCodes.ADDQ.value -> mutateProcessor.process(event, CommandOpCodes.ADDQ)
      CommandOpCodes.REPLACE.value -> mutateProcessor.process(event, CommandOpCodes.REPLACE)
      CommandOpCodes.REPLACEQ.value -> mutateProcessor.process(event, CommandOpCodes.REPLACEQ)
      CommandOpCodes.DELETE.value -> deleteProcessor.process(event, CommandOpCodes.DELETE)
      CommandOpCodes.DELETEQ.value -> deleteProcessor.process(event, CommandOpCodes.DELETEQ)
      CommandOpCodes.INCREMENT.value -> incrementDecrementProcessor.process(event, CommandOpCodes.INCREMENT)
      CommandOpCodes.INCREMENTQ.value -> incrementDecrementProcessor.process(event, CommandOpCodes.INCREMENTQ)
      CommandOpCodes.DECREMENT.value -> incrementDecrementProcessor.process(event, CommandOpCodes.DECREMENT)
      CommandOpCodes.DECREMENTQ.value -> incrementDecrementProcessor.process(event, CommandOpCodes.DECREMENTQ)
      CommandOpCodes.APPEND.value -> appendPrependProcessor.process(event, CommandOpCodes.APPEND)
      CommandOpCodes.APPENDQ.value -> appendPrependProcessor.process(event, CommandOpCodes.APPENDQ)
      CommandOpCodes.PREPEND.value -> appendPrependProcessor.process(event, CommandOpCodes.PREPEND)
      CommandOpCodes.PREPENDQ.value -> appendPrependProcessor.process(event, CommandOpCodes.PREPENDQ)
      else -> processUnknownCommand(event)
    }
  }

  private fun processUnknownCommand(event: Event) {
    val response = Responses.makeError(event.responseBuffer, event.header, ErrorCode.UnknownCommand)
    event.reply(response)
  }
}