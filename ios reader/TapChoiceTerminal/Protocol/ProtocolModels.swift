import Foundation

public enum SwypProtocol {
    public static let aidHex = "F0123456789012"
    public static let success: UInt16 = 0x9000
    public static let getCredentialINS: UInt8 = 0x10
    public static let authorizeINS: UInt8 = 0x20
}

public struct SwypCredential: Codable, Equatable, Sendable {
    public let credentialId: String
    public let displayName: String
    public let last4: String
    public let network: String
    public let publicKey: String

    public init(credentialId: String, displayName: String, last4: String, network: String, publicKey: String) {
        self.credentialId = credentialId
        self.displayName = displayName
        self.last4 = last4
        self.network = network
        self.publicKey = publicKey
    }
}

public struct TransactionChallenge: Codable, Equatable, Sendable {
    public let amountCents: Int64
    public let currency: String
    public let merchant: String
    public let terminalId: String
    public let nonce: String
    public let timestamp: Int64

    public init(amountCents: Int64, currency: String, merchant: String, terminalId: String, nonce: String, timestamp: Int64) {
        self.amountCents = amountCents
        self.currency = currency
        self.merchant = merchant
        self.terminalId = terminalId
        self.nonce = nonce
        self.timestamp = timestamp
    }
}

public struct AuthorizationResponse: Codable, Equatable, Sendable {
    public let credentialId: String
    public let signature: String

    public init(credentialId: String, signature: String) {
        self.credentialId = credentialId
        self.signature = signature
    }
}

public enum CanonicalMessage {
    private static let credentialPattern = try! NSRegularExpression(pattern: "^[A-Za-z0-9_-]{1,64}$")
    private static let terminalPattern = try! NSRegularExpression(pattern: "^[A-Za-z0-9._-]{1,64}$")
    private static let noncePattern = try! NSRegularExpression(pattern: "^[A-Za-z0-9_-]{22}$")

    public static func encode(challenge: TransactionChallenge, credentialId: String) throws -> Data {
        guard challenge.amountCents > 0 && challenge.amountCents <= 100_000_000,
              challenge.currency.range(of: "^[A-Z]{3}$", options: .regularExpression) != nil,
              !challenge.merchant.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              challenge.merchant.count <= 80,
              !challenge.merchant.unicodeScalars.contains(where: { $0.value < 32 || $0.value == 127 }),
              matches(terminalPattern, challenge.terminalId),
              matches(noncePattern, challenge.nonce),
              challenge.timestamp > 0,
              matches(credentialPattern, credentialId) else {
            throw ProtocolError.invalidPayload
        }
        let fields = [
            "swyp-v2",
            String(challenge.amountCents),
            challenge.currency,
            challenge.merchant,
            challenge.terminalId,
            challenge.nonce,
            String(challenge.timestamp),
            credentialId,
        ]
        return Data(fields.joined(separator: "\n").utf8)
    }

    private static func matches(_ expression: NSRegularExpression, _ value: String) -> Bool {
        let range = NSRange(value.startIndex..<value.endIndex, in: value)
        return expression.firstMatch(in: value, range: range)?.range == range
    }
}

public enum ProtocolError: LocalizedError {
    case invalidPayload
    case invalidPublicKey
    case invalidSignature
    case credentialMismatch
    case expiredChallenge
    case replayedChallenge

    public var errorDescription: String? {
        switch self {
        case .invalidPayload: "Invalid protocol payload"
        case .invalidPublicKey: "Credential key is invalid"
        case .invalidSignature: "Transaction signature could not be verified"
        case .credentialMismatch: "Credential changed during authorization"
        case .expiredChallenge: "Transaction challenge expired"
        case .replayedChallenge: "Transaction challenge was already used"
        }
    }
}

public extension Data {
    init?(hex: String) {
        guard hex.count.isMultiple(of: 2) else { return nil }
        var bytes = [UInt8]()
        bytes.reserveCapacity(hex.count / 2)
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        self.init(bytes)
    }
}
