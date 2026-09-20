package com.tapchoice.demo.nfc

import java.nio.charset.StandardCharsets

object Protocol {
    val AID: ByteArray = hexToBytes("F0123456789012")
    const val CLA_STANDARD = 0x00
    const val CLA_DEMO = 0x80
    const val INS_SELECT = 0xA4
    const val INS_GET_CREDENTIAL = 0x10
    const val INS_AUTHORIZE = 0x20

    val SW_SUCCESS = byteArrayOf(0x90.toByte(), 0x00)
    val SW_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    val SW_INVALID_DATA = byteArrayOf(0x6A, 0x80.toByte())
    val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())
    val SW_INSTRUCTION_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)

    fun response(data: ByteArray, status: ByteArray = SW_SUCCESS): ByteArray = data + status
    fun utf8(value: String): ByteArray = value.toByteArray(StandardCharsets.UTF_8)

    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}

data class CommandApdu(
    val cla: Int,
    val ins: Int,
    val p1: Int,
    val p2: Int,
    val data: ByteArray,
    val expectedLength: Int?,
)

object ApduParser {
    /** Parses ISO 7816 short APDUs (cases 1, 2S, 3S, and 4S). */
    fun parse(bytes: ByteArray): CommandApdu? {
        if (bytes.size < 4) return null
        val cla = bytes[0].toInt() and 0xFF
        val ins = bytes[1].toInt() and 0xFF
        val p1 = bytes[2].toInt() and 0xFF
        val p2 = bytes[3].toInt() and 0xFF
        if (bytes.size == 4) return CommandApdu(cla, ins, p1, p2, byteArrayOf(), null)
        if (bytes.size == 5) {
            val le = bytes[4].toInt() and 0xFF
            return CommandApdu(cla, ins, p1, p2, byteArrayOf(), if (le == 0) 256 else le)
        }

        val lc = bytes[4].toInt() and 0xFF
        if (lc == 0 || bytes.size != 5 + lc && bytes.size != 6 + lc) return null
        val data = bytes.copyOfRange(5, 5 + lc)
        val le = if (bytes.size == 6 + lc) {
            val raw = bytes.last().toInt() and 0xFF
            if (raw == 0) 256 else raw
        } else null
        return CommandApdu(cla, ins, p1, p2, data, le)
    }
}

