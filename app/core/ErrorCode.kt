package app.core

enum class ErrorCode(val code: Short, val description: String) {
  NoError(0x0000, "No error"),
  KeyNotFound(0x0001, "Key not found"),
  KeyExists(0x0002, "Key exists"),
  ValueTooLarge(0x0003, "Value too large"),
  InvalidArguments(0x0004, "Invalid arguments"),
  ItemNotStored(0x0005, "Item not stored"),
  IncrDecrOnNonNumericValue(0x0006, "Incr/Decr on non-numeric value"),
  UnknownCommand(0x0081, "Unknown command"),
  OutOfMemory(0x0082, "Out of memory"),
  NotSupported(0x0083, "Not supported"),
  InternalError(0x0084, "Internal error"),
  Busy(0x0085, "Busy"),
  TemporaryFailure(0x0086, "Temporary failure"),
}