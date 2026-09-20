package com.swyp.app.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CanonicalMessageTest {
    @Test
    fun canonicalVectorMatchesProtocol() {
        val challenge =
            TransactionChallenge(
                100,
                "USD",
                "Kroger",
                "swyp-terminal-01",
                "ABEiM0RVZneImaq7zN3u_w",
                1_700_000_000,
            )
        val actual = String(CanonicalMessage.encode(challenge, "cred_amex_01"), Charsets.UTF_8)
        assertEquals(
            "swyp-v2\n100\nUSD\nKroger\nswyp-terminal-01\nABEiM0RVZneImaq7zN3u_w\n1700000000\ncred_amex_01",
            actual,
        )
    }

    @Test
    fun rejectsDelimiterInjection() {
        val challenge =
            TransactionChallenge(100, "USD", "Kroger", "bad\nterminal", "ABEiM0RVZneImaq7zN3u_w", 1)
        assertFalse(CanonicalMessage.validate(challenge))
    }
}
