import Foundation
import Combine

@MainActor final class ReaderSession: ObservableObject {
    static let shared = ReaderSession()
    @Published var message = ""
    @Published var busy = false
    @Published var paymentStatus = ""

    private func backendURL() throws -> URL {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "TERMINAL_BACKEND_URL") as? String,
              !value.isEmpty, !value.contains("$("), let url = URL(string: value) else {
            throw ReaderError.message("Set TERMINAL_BACKEND_URL in Info.plist to the laptop running the Swyp backend.")
        }
        return url
    }

    func submit(credential: SwypCredential, challenge: TransactionChallenge, response: AuthorizationResponse) async throws {
        busy = true
        paymentStatus = "Authorizing payment…"
        defer { busy = false }
        let endpoint = try backendURL().appendingPathComponent("v1/terminal/payment")
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.timeoutInterval = 45
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "credentialId": credential.credentialId,
            "amountCents": challenge.amountCents,
            "currency": challenge.currency,
            "merchant": challenge.merchant,
            "terminalId": challenge.terminalId,
            "nonce": challenge.nonce,
            "timestamp": challenge.timestamp,
            "signature": response.signature,
        ])
        let (data, rawResponse) = try await URLSession.shared.data(for: request)
        guard let http = rawResponse as? HTTPURLResponse else {
            throw ReaderError.message("The Swyp backend did not respond.")
        }
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        guard (200..<300).contains(http.statusCode) else {
            throw ReaderError.message(json?["error"] as? String ?? "Payment was not accepted")
        }
        guard json?["status"] as? String == "posted" else {
            throw ReaderError.message("Payment status is uncertain. Do not tap again until the backend is checked.")
        }
        paymentStatus = "Payment approved and posted to Nessie"
        message = ""
    }

    func clear() {
        message = ""
        paymentStatus = ""
    }
}

enum BackendClient {
    static func authorizeIfConfigured(credential: SwypCredential, challenge: TransactionChallenge, response: AuthorizationResponse, completion: @escaping (Result<Void, Error>) -> Void) {
        Task { @MainActor in
            do {
                try await ReaderSession.shared.submit(credential: credential, challenge: challenge, response: response)
                completion(.success(()))
            } catch {
                ReaderSession.shared.message = error.localizedDescription
                completion(.failure(error))
            }
        }
    }
}

enum ReaderError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        if case let .message(text) = self { return text }
        return nil
    }
}
