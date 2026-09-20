package com.tapchoice.demo.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CanonicalMessageTest {
    @Test fun canonicalVectorMatchesProtocol() {
        val challenge = TransactionChallenge(100, "USD", "demo-terminal-01", "ABEiM0RVZneImaq7zN3u_w", 1_700_000_000)
        val actual = String(CanonicalMessage.encode(challenge, "cred_amex_01"), Charsets.UTF_8)
        assertEquals("tapchoice-v1\n100\nUSD\ndemo-terminal-01\nABEiM0RVZneImaq7zN3u_w\n1700000000\ncred_amex_01", actual)
    }

    @Test fun rejectsDelimiterInjection() {
        val challenge = TransactionChallenge(100, "USD", "bad\nterminal", "ABEiM0RVZneImaq7zN3u_w", 1)
        assertFalse(CanonicalMessage.validate(challenge))
    }
}

