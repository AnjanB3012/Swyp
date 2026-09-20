package com.swyp.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature

class CryptoManager {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun alias(credentialId: String) = "swyp_$credentialId"

    private fun ensureKey(credentialId: String) {
        val alias = alias(credentialId)
        if (keyStore.containsAlias(alias)) return
        val generator =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val spec =
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    /** X.509 SubjectPublicKeyInfo DER, accepted by CryptoKit's derRepresentation initializer. */
    fun publicKeyDer(credentialId: String): ByteArray {
        ensureKey(credentialId)
        return keyStore.getCertificate(alias(credentialId)).publicKey.encoded
    }

    /** SHA256withECDSA returns an ASN.1 DER encoded (r,s) signature. */
    fun sign(credentialId: String, message: ByteArray): ByteArray {
        ensureKey(credentialId)
        val privateKey = keyStore.getKey(alias(credentialId), null)
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey as java.security.PrivateKey)
            update(message)
            sign()
        }
    }
}
