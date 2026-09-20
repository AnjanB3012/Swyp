package com.swyp.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.swyp.app.SwypCard
import com.swyp.app.crypto.CryptoManager
import org.json.JSONObject

object ArmedPayment {
    data class Session(val card: SwypCard, val expires: Long)

    @Volatile private var session: Session? = null

    @Synchronized
    fun arm(card: SwypCard) {
        session = Session(card, android.os.SystemClock.elapsedRealtime() + 120000)
    }

    @Synchronized
    fun current(): Session? =
        session?.takeIf { android.os.SystemClock.elapsedRealtime() < it.expires }

    @Synchronized
    fun consume(expected: Session): Boolean {
        if (current() != expected) return false
        session = null
        return true
    }

    @Synchronized
    fun clear() {
        session = null
    }
}

class SwypHceService : HostApduService() {
    private val crypto by lazy { CryptoManager() }
    private var selected = false
    private var session: ArmedPayment.Session? = null
    private var lastChallenge: ByteArray? = null
    private var lastResponse: ByteArray? = null

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        return try {
            if (FirebaseAuth.getInstance().currentUser == null)
                return Protocol.SW_CONDITIONS_NOT_SATISFIED
            val c = commandApdu?.let(ApduParser::parse) ?: return Protocol.SW_INVALID_DATA
            when {
                c.cla == 0 && c.ins == 0xA4 -> {
                    selected = c.p1 == 4 && c.p2 == 0 && c.data.contentEquals(Protocol.AID)
                    session = null
                    lastChallenge = null
                    lastResponse = null
                    if (selected) Protocol.SW_SUCCESS else Protocol.SW_NOT_FOUND
                }
                !selected -> Protocol.SW_CONDITIONS_NOT_SATISFIED
                c.cla == 0x80 && c.ins == 0x10 && c.p1 == 0 && c.p2 == 0 && c.data.isEmpty() -> {
                    val armed =
                        ArmedPayment.current() ?: return Protocol.SW_CONDITIONS_NOT_SATISFIED
                    session = armed
                    val card = armed.card
                    // Keep credential response below the ISO 7816 short response limit (256 bytes).
                    Protocol.response(
                        JSONObject()
                            .put("credentialId", card.id)
                            .put("displayName", card.name)
                            .put("last4", card.last4)
                            .put("network", "SWYP")
                            .put(
                                "publicKey",
                                Base64.encodeToString(crypto.publicKeyDer(card.id), Base64.NO_WRAP),
                            )
                            .toString()
                            .toByteArray()
                    )
                }
                c.cla == 0x80 && c.ins == 0x20 && c.p1 == 0 && c.p2 == 0 -> {
                    if (lastChallenge?.contentEquals(c.data) == true)
                        return lastResponse ?: Protocol.SW_CONDITIONS_NOT_SATISFIED
                    val armed = session ?: return Protocol.SW_CONDITIONS_NOT_SATISFIED
                    val j = JSONObject(String(c.data))
                    val challenge =
                        TransactionChallenge(
                            j.getLong("amountCents"),
                            j.getString("currency"),
                            j.getString("merchant"),
                            j.getString("terminalId"),
                            j.getString("nonce"),
                            j.getLong("timestamp"),
                        )
                    if (
                        !CanonicalMessage.validate(challenge) ||
                            challenge.currency != "USD" ||
                            kotlin.math.abs(
                                System.currentTimeMillis() / 1000 - challenge.timestamp
                            ) > 120 ||
                            !ArmedPayment.consume(armed)
                    )
                        return Protocol.SW_CONDITIONS_NOT_SATISFIED
                    val response =
                        Protocol.response(
                            JSONObject()
                                .put("credentialId", armed.card.id)
                                .put(
                                    "signature",
                                    Base64.encodeToString(
                                        crypto.sign(
                                            armed.card.id,
                                            CanonicalMessage.encode(challenge, armed.card.id),
                                        ),
                                        Base64.NO_WRAP,
                                    ),
                                )
                                .toString()
                                .toByteArray()
                        )
                    lastChallenge = c.data
                    lastResponse = response
                    response
                }
                else -> Protocol.SW_INSTRUCTION_NOT_SUPPORTED
            }
        } catch (_: Exception) {
            Protocol.SW_INVALID_DATA
        }
    }

    override fun onDeactivated(reason: Int) {
        selected = false
        session = null
        lastChallenge = null
        lastResponse = null
    }
}
