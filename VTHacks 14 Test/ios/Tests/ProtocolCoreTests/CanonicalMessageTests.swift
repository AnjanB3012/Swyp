import CryptoKit
import XCTest
@testable import ProtocolCore

final class CanonicalMessageTests: XCTestCase {
    func testCanonicalVectorMatchesAndroid() throws {
        let challenge = TransactionChallenge(
            amountCents: 100,
            currency: "USD",
            terminalId: "demo-terminal-01",
            nonce: "ABEiM0RVZneImaq7zN3u_w",
            timestamp: 1_700_000_000
        )
        let actual = try CanonicalMessage.encode(challenge: challenge, credentialId: "cred_amex_01")
        XCTAssertEqual(
            String(decoding: actual, as: UTF8.self),
            "tapchoice-v1\n100\nUSD\ndemo-terminal-01\nABEiM0RVZneImaq7zN3u_w\n1700000000\ncred_amex_01"
        )
    }

    func testDERKeyAndSignatureRoundTrip() throws {
        let privateKey = P256.Signing.PrivateKey()
        let challenge = TransactionChallenge(
            amountCents: 100, currency: "USD", terminalId: "demo-terminal-01",
            nonce: "ABEiM0RVZneImaq7zN3u_w", timestamp: 1_700_000_000
        )
        let message = try CanonicalMessage.encode(challenge: challenge, credentialId: "cred_amex_01")
        let signature = try privateKey.signature(for: message)
        let credential = DemoCredential(
            credentialId: "cred_amex_01", displayName: "Demo", last4: "5678", network: "Demo",
            publicKey: privateKey.publicKey.derRepresentation.base64EncodedString()
        )
        let response = AuthorizationResponse(
            credentialId: credential.credentialId,
            signature: signature.derRepresentation.base64EncodedString()
        )
        XCTAssertNoThrow(try SignatureVerifier.verify(
            response: response, credential: credential, challenge: challenge, now: 1_700_000_000
        ))
    }
}
