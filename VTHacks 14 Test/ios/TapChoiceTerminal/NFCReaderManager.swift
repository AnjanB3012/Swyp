import CoreNFC
import Foundation
import Security

enum TerminalPhase: Equatable {
    case idle
    case waiting
    case reading
    case authorizing
    case approved
    case failed(String)
}

private enum NFCExchangeError: LocalizedError {
    case unavailable
    case unsupportedTag
    case status(UInt8, UInt8)
    case decoding
    case randomGeneration

    var errorDescription: String? {
        switch self {
        case .unavailable: "NFC scanning is unavailable on this device"
        case .unsupportedTag: "Hold an Android NFC device near the top of this iPhone"
        case let .status(sw1, sw2): String(format: "Credential returned status %02X%02X", sw1, sw2)
        case .decoding: "The credential response was invalid"
        case .randomGeneration: "Could not create a transaction challenge"
        }
    }
}

/// Reads only the private TapChoice demo AID. No payment-network APDUs are used.
final class NFCReaderManager: NSObject, ObservableObject, NFCTagReaderSessionDelegate {
    @Published private(set) var phase: TerminalPhase = .idle
    @Published private(set) var approvedCredential: DemoCredential?

    private var session: NFCTagReaderSession?
    private var amountCents: Int64 = 100
    private var consumedNonces = Set<String>()
    private let terminalId = "demo-terminal-01"

    var isScanning: Bool {
        [.waiting, .reading, .authorizing].contains(phase)
    }

    func begin(amountCents: Int64) {
        guard NFCTagReaderSession.readingAvailable else {
            phase = .failed(NFCExchangeError.unavailable.localizedDescription)
            return
        }
        self.amountCents = amountCents
        approvedCredential = nil
        phase = .waiting
        guard let session = NFCTagReaderSession(pollingOption: [.iso14443], delegate: self) else {
            phase = .failed(NFCExchangeError.unavailable.localizedDescription)
            return
        }
        session.alertMessage = "Hold the customer Android device near the top of this iPhone."
        self.session = session
        session.begin()
    }

    func reset() {
        session?.invalidate()
        session = nil
        approvedCredential = nil
        phase = .idle
    }

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        DispatchQueue.main.async {
            self.session = nil
            guard case .approved = self.phase else {
                if let readerError = error as? NFCReaderError,
                   readerError.code == .readerSessionInvalidationErrorUserCanceled {
                    self.phase = .idle
                } else if self.isScanning {
                    self.phase = .failed(error.localizedDescription)
                }
                return
            }
        }
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard tags.count == 1 else {
            session.alertMessage = "Present one customer device."
            session.restartPolling()
            return
        }
        guard case let .iso7816(tag) = tags[0] else {
            session.invalidate(errorMessage: NFCExchangeError.unsupportedTag.localizedDescription)
            DispatchQueue.main.async { self.phase = .failed(NFCExchangeError.unsupportedTag.localizedDescription) }
            return
        }
        session.connect(to: tags[0]) { [weak self] error in
            guard let self else { return }
            if let error { self.fail(error, session: session); return }
            DispatchQueue.main.async { self.phase = .reading }
            self.selectApplication(on: tag, session: session)
        }
    }

    private func selectApplication(on tag: NFCISO7816Tag, session: NFCTagReaderSession) {
        guard let aid = Data(hex: DemoProtocol.aidHex) else {
            fail(NFCExchangeError.decoding, session: session); return
        }
        let apdu = NFCISO7816APDU(
            instructionClass: 0x00, instructionCode: 0xA4,
            p1Parameter: 0x04, p2Parameter: 0x00,
            data: aid, expectedResponseLength: 256
        )
        send(apdu, to: tag) { [weak self] result in
            switch result {
            case .success: self?.readCredential(on: tag, session: session)
            case let .failure(error): self?.fail(error, session: session)
            }
        }
    }

    private func readCredential(on tag: NFCISO7816Tag, session: NFCTagReaderSession) {
        let apdu = NFCISO7816APDU(
            instructionClass: 0x80, instructionCode: DemoProtocol.getCredentialINS,
            p1Parameter: 0x00, p2Parameter: 0x00,
            data: Data(), expectedResponseLength: 256
        )
        send(apdu, to: tag) { [weak self] result in
            guard let self else { return }
            do {
                let data = try result.get()
                let credential = try JSONDecoder().decode(DemoCredential.self, from: data)
                let challenge = try self.makeChallenge()
                DispatchQueue.main.async { self.phase = .authorizing }
                self.authorize(credential: credential, challenge: challenge, on: tag, session: session)
            } catch {
                self.fail(error is DecodingError ? NFCExchangeError.decoding : error, session: session)
            }
        }
    }

    private func authorize(
        credential: DemoCredential,
        challenge: TransactionChallenge,
        on tag: NFCISO7816Tag,
        session: NFCTagReaderSession
    ) {
        do {
            let challengeData = try JSONEncoder().encode(challenge)
            guard challengeData.count <= 255 else { throw ProtocolError.invalidPayload }
            let apdu = NFCISO7816APDU(
                instructionClass: 0x80, instructionCode: DemoProtocol.authorizeINS,
                p1Parameter: 0x00, p2Parameter: 0x00,
                data: challengeData, expectedResponseLength: 256
            )
            send(apdu, to: tag) { [weak self] result in
                guard let self else { return }
                do {
                    let data = try result.get()
                    let response = try JSONDecoder().decode(AuthorizationResponse.self, from: data)
                    guard !self.consumedNonces.contains(challenge.nonce) else {
                        throw ProtocolError.replayedChallenge
                    }
                    try SignatureVerifier.verify(response: response, credential: credential, challenge: challenge)
                    BackendClient.authorizeIfConfigured(
                        credential: credential, challenge: challenge, response: response
                    ) { backendResult in
                        switch backendResult {
                        case .success:
                            self.consumedNonces.insert(challenge.nonce)
                            // Keep the reader session alive while the phones are still touching.
                            // Invalidating immediately can return NFC ownership to Wallet while the
                            // Android device remains in the field, causing an unwanted Wallet handoff.
                            session.alertMessage = "Approved — separate the devices."
                            DispatchQueue.main.async {
                                self.approvedCredential = credential
                                self.phase = .approved
                            }
                            self.invalidateAfterTagRemoval(tag, session: session)
                        case let .failure(error):
                            self.fail(error, session: session)
                        }
                    }
                } catch {
                    self.fail(error is DecodingError ? NFCExchangeError.decoding : error, session: session)
                }
            }
        } catch {
            fail(error, session: session)
        }
    }

    private func send(
        _ apdu: NFCISO7816APDU,
        to tag: NFCISO7816Tag,
        completion: @escaping (Result<Data, Error>) -> Void
    ) {
        tag.sendCommand(apdu: apdu) { data, sw1, sw2, error in
            if let error { completion(.failure(error)); return }
            guard (UInt16(sw1) << 8 | UInt16(sw2)) == DemoProtocol.success else {
                completion(.failure(NFCExchangeError.status(sw1, sw2))); return
            }
            completion(.success(data))
        }
    }

    private func makeChallenge() throws -> TransactionChallenge {
        var bytes = [UInt8](repeating: 0, count: 16)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
            throw NFCExchangeError.randomGeneration
        }
        let nonce = Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return TransactionChallenge(
            amountCents: amountCents,
            currency: "USD",
            terminalId: terminalId,
            nonce: nonce,
            timestamp: Int64(Date().timeIntervalSince1970)
        )
    }

    private func invalidateAfterTagRemoval(
        _ tag: NFCISO7816Tag,
        session: NFCTagReaderSession,
        attempt: Int = 0
    ) {
        guard tag.isAvailable, attempt < 150 else {
            session.invalidate()
            return
        }
        DispatchQueue.global(qos: .userInitiated).asyncAfter(deadline: .now() + 0.2) { [weak self] in
            self?.invalidateAfterTagRemoval(tag, session: session, attempt: attempt + 1)
        }
    }

    private func fail(_ error: Error, session: NFCTagReaderSession) {
        let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        session.invalidate(errorMessage: message)
        DispatchQueue.main.async { self.phase = .failed(message) }
    }
}
