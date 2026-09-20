package com.tapchoice.demo.nfc

import org.junit.Assert.*
import org.junit.Test

class ApduParserTest {
    @Test fun parsesSelectWithShortLc() {
        val bytes = Protocol.hexToBytes("00A4040007F0123456789012")
        val command = requireNotNull(ApduParser.parse(bytes))
        assertEquals(0xA4, command.ins)
        assertArrayEquals(Protocol.AID, command.data)
        assertNull(command.expectedLength)
    }

    @Test fun parsesCase2WithMaximumLe() {
        val command = requireNotNull(ApduParser.parse(Protocol.hexToBytes("8010000000")))
        assertEquals(256, command.expectedLength)
        assertTrue(command.data.isEmpty())
    }

    @Test fun rejectsTruncatedPayload() {
        assertNull(ApduParser.parse(Protocol.hexToBytes("80200000050102")))
    }
}

