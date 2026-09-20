package com.swyp.app.nfc

import java.nio.charset.StandardCharsets

data class TransactionChallenge(
    val amountCents: Long,
    val currency: String,
    val merchant: String,
    val terminalId: String,
    val nonce: String,
    val timestamp: Long,
)

object CanonicalMessage {
    private val terminalPattern = Regex("[A-Za-z0-9._-]{1,64}")
    private val noncePattern = Regex("[A-Za-z0-9_-]{22}")

    fun validate(challenge: TransactionChallenge): Boolean =
        challenge.amountCents in 1..100_000_000 &&
            challenge.currency.matches(Regex("[A-Z]{3}")) &&
            challenge.merchant.isNotBlank() &&
            challenge.merchant.length <= 80 &&
            challenge.merchant.all { it >= ' ' && it != '\u007f' } &&
            challenge.terminalId.matches(terminalPattern) &&
            challenge.nonce.matches(noncePattern) &&
            challenge.timestamp > 0

    fun encode(challenge: TransactionChallenge, credentialId: String): ByteArray {
        require(validate(challenge))
        require(credentialId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        return listOf(
                "swyp-v2",
                challenge.amountCents.toString(),
                challenge.currency,
                challenge.merchant,
                challenge.terminalId,
                challenge.nonce,
                challenge.timestamp.toString(),
                credentialId,
            )
            .joinToString("\n")
            .toByteArray(StandardCharsets.UTF_8)
    }
}
