package app.utils

import app.core.CommandOpCodes

object Commands {
  fun isQuietCommand(command: CommandOpCodes): Boolean {
    return command == CommandOpCodes.GETQ
        || command == CommandOpCodes.GETKQ
        || command == CommandOpCodes.SETQ
        || command == CommandOpCodes.ADDQ
        || command == CommandOpCodes.REPLACEQ
        || command == CommandOpCodes.APPENDQ
        || command == CommandOpCodes.PREPENDQ
        || command == CommandOpCodes.DELETEQ
        || command == CommandOpCodes.FLUSHQ
        || command == CommandOpCodes.INCREMENTQ
        || command == CommandOpCodes.DECREMENTQ
        || command == CommandOpCodes.QUITQ
  }
}