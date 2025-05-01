package app.core

enum class CommandOpCodes(val value: Byte) {
    GET(0x00),
    SET(0x01),
    ADD(0x02),
    REPLACE(0x03),
    DELETE(0x04),
    INCREMENT(0x05),
    DECREMENT(0x06),
    GETQ(0x09),
    NOOP(0x0A),
    VERSION(0x0B),
    GETK(0x0C),
    GETKQ(0x0D),
    APPEND(0x0E),
    PREPEND(0x0F),
    SETQ(0x11),
    ADDQ(0x12),
    REPLACEQ(0x13),
    DELETEQ(0x14),
    INCREMENTQ(0x15),
    DECREMENTQ(0x16),
    APPENDQ(0x19),
    PREPENDQ(0x1A)
}