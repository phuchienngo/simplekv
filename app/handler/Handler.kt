package app.handler

import app.core.Event

interface Handler {
  fun handle(event: Event)
}