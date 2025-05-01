package app.core

enum class ResponseStatus(val value: Short) {
  NoError(0x0000),
  KeyNotFound(0x0001),
  KeyExists(0x0002),
  ValueTooLarge(0x0003),
  InvalidArguments(0x0004),
  ItemNotStored(0x0005),
  IncrDecrOnNonNumericValue(0x0006),
  UnknownCommand(0x0081),
  OutOfMemory(0x0082),
  NotSupported(0x0083),
  InternalError(0x0084),
  Busy(0x0085),
  TemporaryFailure(0x0086),
}