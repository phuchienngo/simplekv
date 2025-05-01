package app.handler

import app.core.Event
import com.lmax.disruptor.dsl.Disruptor

class Worker(disruptor: Disruptor<Event>): AbstractWorker(disruptor) {
  override fun process(event: Event) {
    // TODO: Implement the logic to process the event
    event.done()
  }
}