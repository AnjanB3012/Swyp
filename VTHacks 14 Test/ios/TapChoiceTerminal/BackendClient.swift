import Foundation

private struct BackendPaymentRequest: Encodable {
    let credentialId: String
    let amountCents: Int64
    let currency: String
    let terminalId: String
    let nonce: String
    let timestamp: Int64
    let signature: String
    let publicKey: String
}

private struct BackendPaymentResponse: Decodable {
    let approved: Bool
    let reason: String?
}

enum BackendClient {
    /// With no DEMO_BACKEND_URL, NFC approval remains completely standalone.
    static func authorizeIfConfigured(
        credential: DemoCredential,
        challenge: TransactionChallenge,
        response: AuthorizationResponse,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "DEMO_BACKEND_URL") as? String,
              !value.isEmpty,
              let baseURL = URL(string: value) else {
            completion(.success(()))
            return
        }
        var request = URLRequest(url: baseURL.appendingPathComponent("demo-payment"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        do {
            request.httpBody = try JSONEncoder().encode(BackendPaymentRequest(
                credentialId: credential.credentialId,
                amountCents: challenge.amountCents,
                currency: challenge.currency,
                terminalId: challenge.terminalId,
                nonce: challenge.nonce,
                timestamp: challenge.timestamp,
                signature: response.signature,
                publicKey: credential.publicKey
            ))
        } catch {
            completion(.failure(error)); return
        }
        URLSession.shared.dataTask(with: request) { data, _, error in
            if let error { completion(.failure(error)); return }
            do {
                let result = try JSONDecoder().decode(BackendPaymentResponse.self, from: data ?? Data())
                if result.approved { completion(.success(())) }
                else { completion(.failure(BackendError.declined(result.reason ?? "declined"))) }
            } catch { completion(.failure(error)) }
        }.resume()
    }
}

private enum BackendError: LocalizedError {
    case declined(String)
    var errorDescription: String? {
        if case let .declined(reason) = self { "Demo payment declined: \(reason)" } else { nil }
    }
}
