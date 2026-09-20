package com.tapchoice.demo.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Base64
import android.util.Log
import com.tapchoice.demo.crypto.CryptoManager
import com.tapchoice.demo.data.DemoCardRepository
import com.tapchoice.demo.model.DemoCard
import com.tapchoice.demo.state.PaymentSessionStore
import com.tapchoice.demo.state.TapState
import org.json.JSONObject
import kotlin.math.abs

/**
 * Custom demo credential service. This is deliberately not an EMV/payment AID and never handles
 * PAN, CVV, expiry, wallet tokens, or issuer credentials.
 */
class DemoHostApduService : HostApduService() {
    private val crypto by lazy { CryptoManager() }
    private var applicationSelected = false
    private var sessionCard: DemoCard? = null

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val command = commandApdu?.let(ApduParser::parse)
            ?: return Protocol.SW_INVALID_DATA
        Log.d("TapChoiceHCE", "APDU cla=%02X ins=%02X bytes=%d".format(command.cla, command.ins, commandApdu.size))

        return try {
            when {
                command.cla == Protocol.CLA_STANDARD && command.ins == Protocol.INS_SELECT ->
                    handleSelect(command)
                command.cla == Protocol.CLA_DEMO && command.ins == Protocol.INS_GET_CREDENTIAL ->
                    handleGetCredential(command)
                command.cla == Protocol.CLA_DEMO && command.ins == Protocol.INS_AUTHORIZE ->
                    handleAuthorize(command)
                else -> Protocol.SW_INSTRUCTION_NOT_SUPPORTED
            }
        } catch (_: Exception) {
            PaymentSessionStore.update(TapState.ERROR)
            Protocol.SW_INVALID_DATA
        }
    }

    private fun handleSelect(command: CommandApdu): ByteArray {
        if (command.p1 != 0x04 || command.p2 != 0x00 || !command.data.contentEquals(Protocol.AID)) {
            applicationSelected = false
            sessionCard = null
            return Protocol.SW_NOT_FOUND
        }
        applicationSelected = true
        sessionCard = null
        PaymentSessionStore.update(TapState.READING)
        return Protocol.SW_SUCCESS
    }

    private fun handleGetCredential(command: CommandApdu): ByteArray {
        if (!applicationSelected) return Protocol.SW_CONDITIONS_NOT_SATISFIED
        if (command.p1 != 0 || command.p2 != 0 || command.data.isNotEmpty()) {
            return Protocol.SW_INVALID_DATA
        }
        val card = DemoCardRepository.selected(this)
        sessionCard = card
        val payload = JSONObject()
            .put("credentialId", card.id)
            .put("displayName", card.displayName)
            .put("last4", card.last4)
            .put("network", card.network)
            .put(
                "publicKey",
                Base64.encodeToString(crypto.publicKeyDer(card.id), Base64.NO_WRAP),
            )
        return Protocol.response(Protocol.utf8(payload.toString()))
    }

    private fun handleAuthorize(command: CommandApdu): ByteArray {
        val card = sessionCard ?: return Protocol.SW_CONDITIONS_NOT_SATISFIED
        if (!applicationSelected || command.p1 != 0 || command.p2 != 0 || command.data.isEmpty()) {
            return Protocol.SW_INVALID_DATA
        }
        PaymentSessionStore.update(TapState.AUTHORIZING)
        val json = JSONObject(String(command.data, Charsets.UTF_8))
        val challenge = TransactionChallenge(
            amountCents = json.getLong("amountCents"),
            currency = json.getString("currency"),
            terminalId = json.getString("terminalId"),
            nonce = json.getString("nonce"),
            timestamp = json.getLong("timestamp"),
        )
        val now = System.currentTimeMillis() / 1_000
        if (!CanonicalMessage.validate(challenge) || abs(now - challenge.timestamp) > 120) {
            PaymentSessionStore.update(TapState.ERROR)
            return Protocol.SW_INVALID_DATA
        }
        val signature = crypto.sign(card.id, CanonicalMessage.encode(challenge, card.id))
        val response = JSONObject()
            .put("credentialId", card.id)
            .put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))
        PaymentSessionStore.update(TapState.PAID)
        return Protocol.response(Protocol.utf8(response.toString()))
    }

    override fun onDeactivated(reason: Int) {
        Log.d("TapChoiceHCE", "NFC link deactivated, reason=$reason")
        applicationSelected = false
        sessionCard = null
        if (PaymentSessionStore.state.value != TapState.PAID) {
            PaymentSessionStore.update(TapState.READY)
        }
    }
}
