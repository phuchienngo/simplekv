package app.handler

import app.core.CommandOpCodes
import app.utils.Responses
import app.core.Event
import app.core.ResponseStatus
import app.datastructure.SwissMap
import app.utils.Commands
import app.utils.Validators
import com.lmax.disruptor.dsl.Disruptor
import java.nio.ByteBuffer

class Worker(disruptor: Disruptor<Event>): AbstractWorker(disruptor) {
  private val valueMap = SwissMap<String, ByteBuffer>()
  private val extrasMap = SwissMap<String, ByteBuffer>()
  private val decoder = Charsets.US_ASCII.newDecoder()

  override fun process(event: Event) {
    when (event.header.opcode) {
      CommandOpCodes.GET.value -> processGetCommand(event, CommandOpCodes.GET)
      CommandOpCodes.GETQ.value -> processGetCommand(event, CommandOpCodes.GETQ)
      CommandOpCodes.GETK.value -> processGetCommand(event, CommandOpCodes.GETK)
      CommandOpCodes.GETKQ.value -> processGetCommand(event, CommandOpCodes.GETKQ)
      else -> processUnknownCommand(event)
    }
  }

  private fun processGetCommand(event: Event, command: CommandOpCodes) {
    if (Validators.hasExtras(event) || Validators.hasValue(event) || !Validators.hasKey(event)) {
      val response = Responses.makeError(event.header, ResponseStatus.InvalidArguments)
      event.reply(response)
      return
    }

    val key = decodeKey(event.body.key!!)
    if (!valueMap.containsKey(key)) {
      if (Commands.isQuietCommand(command)) {
        event.done()
      } else {
        val response = Responses.makeError(event.header, ResponseStatus.KeyNotFound)
        event.reply(response)
      }
      return
    }

    val value = valueMap[key]
    val extras = extrasMap[key]
    val response = Responses.makeResponse(
      event.header,
      extras,
      if (command == CommandOpCodes.GETK || command == CommandOpCodes.GETKQ) {
        event.body.key
      } else {
        null
      },
      value
    )
    event.reply(response)
  }

  private fun processUnknownCommand(event: Event) {
    val response = Responses.makeError(event.header, ResponseStatus.UnknownCommand)
    event.reply(response)
  }

  private fun decodeKey(buffer: ByteBuffer): String {
    decoder.reset()
    val charBuffer = decoder.decode(buffer)
    buffer.position(0)
    return charBuffer.toString()
  }
}