import CryptoKit
import Foundation

public enum SignatureVerifier {
    /// Android sends SubjectPublicKeyInfo DER and SHA256withECDSA ASN.1 DER signatures.
    public static func verify(
        response: AuthorizationResponse,
        credential: DemoCredential,
        challenge: TransactionChallenge,
        now: Int64 = Int64(Date().timeIntervalSince1970)
    ) throws {
        guard response.credentialId == credential.credentialId else {
            throw ProtocolError.credentialMismatch
        }
        guard abs(now - challenge.timestamp) <= 120 else {
            throw ProtocolError.expiredChallenge
        }
        guard let keyData = Data(base64Encoded: credential.publicKey),
              let key = try? P256.Signing.PublicKey(derRepresentation: keyData) else {
            throw ProtocolError.invalidPublicKey
        }
        guard let signatureData = Data(base64Encoded: response.signature),
              let signature = try? P256.Signing.ECDSASignature(derRepresentation: signatureData) else {
            throw ProtocolError.invalidSignature
        }
        let message = try CanonicalMessage.encode(challenge: challenge, credentialId: credential.credentialId)
        guard key.isValidSignature(signature, for: message) else {
            throw ProtocolError.invalidSignature
        }
    }
}

