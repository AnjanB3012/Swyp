import SwiftUI

struct MerchantTerminalView: View {
    @StateObject private var reader = NFCReaderManager()
    @StateObject private var session = ReaderSession.shared
    @State private var merchant = "Kroger"
    @State private var amount = "25.99"
    private let navy = Color(red: 0.02, green: 0.10, blue: 0.24)
    private let teal = Color(red: 0.00, green: 0.48, blue: 0.45)
    private let mist = Color(red: 0.93, green: 0.98, blue: 0.97)

    var body: some View {
        ZStack {
            Color(red: 0.97, green: 0.98, blue: 0.99).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    HStack(spacing: 12) {
                        Image(systemName: "creditcard.fill").font(.system(size: 32)).foregroundStyle(teal)
                        Text("Swyp").font(.system(size: 34, weight: .bold))
                        Spacer()
                        Image(systemName: "wave.3.right.circle.fill").font(.system(size: 38)).foregroundStyle(teal)
                    }
                    Text("Tap to pay").font(.system(size: 42, weight: .bold))
                    Text("Enter the sale, then hold the prepared Android phone near the top of this iPhone.").foregroundStyle(.secondary)
                    VStack(alignment: .leading, spacing: 18) {
                        Text("SALE").font(.caption.weight(.semibold)).tracking(2).foregroundStyle(teal)
                        TextField("Merchant name", text: $merchant)
                            .textInputAutocapitalization(.words).font(.title3.weight(.semibold)).padding(14)
                            .background(.white, in: RoundedRectangle(cornerRadius: 14))
                        HStack(alignment: .firstTextBaseline) {
                            Text("$").font(.system(size: 38, weight: .semibold))
                            TextField("0.00", text: $amount).keyboardType(.decimalPad).font(.system(size: 48, weight: .bold))
                        }
                        Text("USD").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    }.padding(22).background(mist, in: RoundedRectangle(cornerRadius: 24))
                    Button(action: start) {
                        Label(reader.isScanning ? "Hold phones together…" : "Tap to Pay", systemImage: "wave.3.right")
                            .font(.headline).frame(maxWidth: .infinity).padding(12)
                    }.buttonStyle(.borderedProminent).tint(teal).disabled(reader.isScanning || session.busy)
                    VStack(alignment: .leading, spacing: 10) {
                        Text(phaseText).font(.headline)
                        if let card = reader.approvedCredential {
                            Text("\(card.displayName) · •••• \(card.last4)").foregroundStyle(.secondary)
                        }
                        if !session.paymentStatus.isEmpty {
                            Label(session.paymentStatus, systemImage: "checkmark.circle.fill").foregroundStyle(teal)
                        }
                        if !session.message.isEmpty { Text(session.message).foregroundStyle(.red) }
                    }.padding(20).frame(maxWidth: .infinity, alignment: .leading).background(.white, in: RoundedRectangle(cornerRadius: 20))
                    Text("Swyp uses a private signed NFC credential and posts the approved transaction through the configured backend.")
                        .font(.footnote).foregroundStyle(.secondary)
                }.padding(26)
            }
        }.foregroundStyle(navy)
            .onChange(of: merchant) { _ in session.clear() }
            .onChange(of: amount) { _ in session.clear() }
    }

    private var phaseText: String {
        switch reader.phase {
        case .idle: "Prepare a card in Pay on the Android app."
        case .waiting: "Hold the Android phone near the top of this iPhone."
        case .reading: "Reading Swyp credential…"
        case .authorizing: "Verifying and posting purchase…"
        case .approved: "Payment approved"
        case let .failed(message): message
        }
    }

    private func start() {
        let cleanMerchant = merchant.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanMerchant.isEmpty, cleanMerchant.count <= 80 else { session.message = "Enter a merchant name up to 80 characters."; return }
        guard let value = Decimal(string: amount, locale: Locale(identifier: "en_US_POSIX")), value > 0, value <= 1_000_000 else {
            session.message = "Enter a valid USD amount."; return
        }
        let cents = value * 100
        var input = cents
        var rounded = Decimal()
        NSDecimalRound(&rounded, &input, 0, .plain)
        guard cents == rounded else { session.message = "Use at most two decimal places."; return }
        session.clear()
        reader.begin(merchant: cleanMerchant, amountCents: NSDecimalNumber(decimal: cents).int64Value)
    }
}
