import SwiftUI

struct MerchantTerminalView: View {
    @StateObject private var nfc = NFCReaderManager()
    @State private var amountCents: Int64 = 100
    private let amounts: [Int64] = [100, 500, 2000]

    var body: some View {
        ZStack {
            Color(red: 0.045, green: 0.07, blue: 0.055).ignoresSafeArea()
            VStack(spacing: 0) {
                header
                Spacer()
                status
                Spacer()
                controls
            }
            .padding(24)
        }
        .preferredColorScheme(.dark)
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text("TAPCHOICE")
                    .font(.caption.weight(.bold)).tracking(2.2).foregroundStyle(.mint)
                Text("MERCHANT 01")
                    .font(.caption2.monospaced()).foregroundStyle(.secondary)
            }
            Spacer()
            Circle().fill(Color.green).frame(width: 9, height: 9)
        }
    }

    @ViewBuilder private var status: some View {
        switch nfc.phase {
        case .approved:
            approvedView
        case let .failed(message):
            phaseView(symbol: "exclamationmark", title: "TRY AGAIN", subtitle: message)
        case .reading:
            phaseView(symbol: "wave.3.right", title: "READING CREDENTIAL…", subtitle: "Keep both devices together")
        case .authorizing:
            phaseView(symbol: "checkmark.shield", title: "AUTHORIZING…", subtitle: "Verifying signed challenge")
        case .waiting:
            phaseView(symbol: "wave.3.right.circle", title: money(amountCents), subtitle: "Hold customer device\nnear the top of this iPhone")
        case .idle:
            Image(systemName: "wave.3.right.circle.fill")
                .font(.system(size: 112, weight: .ultraLight))
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(.mint)
                .accessibilityLabel("Tap to Pay")
        }
    }

    private func phaseView(symbol: String, title: String, subtitle: String) -> some View {
        VStack(spacing: 22) {
            Image(systemName: symbol).font(.system(size: 48, weight: .light)).foregroundStyle(.mint)
            Text(title).font(.system(size: 36, weight: .black, design: .rounded)).multilineTextAlignment(.center)
            Text(subtitle).font(.title3).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
    }

    private var approvedView: some View {
        VStack(spacing: 18) {
            Image(systemName: "checkmark.circle.fill").font(.system(size: 62)).foregroundStyle(.green)
            Text("APPROVED").font(.system(size: 42, weight: .black, design: .rounded)).foregroundStyle(.green)
            Text(money(amountCents)).font(.system(size: 34, weight: .bold, design: .rounded))
            if let credential = nfc.approvedCredential {
                VStack(spacing: 5) {
                    Text(credential.displayName).font(.title3.weight(.semibold))
                    Text("•••• \(credential.last4)").font(.body.monospaced()).foregroundStyle(.secondary)
                }
                .padding(.top, 5)
            }
        }
    }

    @ViewBuilder private var controls: some View {
        if nfc.phase == .approved || isFailure {
            Button("NEW TRANSACTION") { nfc.reset() }
                .buttonStyle(TerminalButtonStyle())
        } else if !nfc.isScanning {
            VStack(spacing: 18) {
                HStack(spacing: 9) {
                    ForEach(amounts, id: \.self) { amount in
                        Button(money(amount)) { amountCents = amount }
                            .buttonStyle(AmountButtonStyle(selected: amountCents == amount))
                    }
                }
                Button("PAY") { nfc.begin(amountCents: amountCents) }
                    .buttonStyle(TerminalButtonStyle())
            }
        } else {
            Text("NFC SESSION ACTIVE").font(.caption.monospaced()).foregroundStyle(.secondary).padding(.bottom, 18)
        }
    }

    private var isFailure: Bool {
        if case .failed = nfc.phase { true } else { false }
    }

    private func money(_ cents: Int64) -> String {
        (Double(cents) / 100.0).formatted(.currency(code: "USD"))
    }
}

private struct TerminalButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline.weight(.bold)).tracking(1.2)
            .frame(maxWidth: .infinity).padding(.vertical, 18)
            .background(configuration.isPressed ? Color.green.opacity(0.7) : Color.green)
            .foregroundStyle(Color.black).clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

private struct AmountButtonStyle: ButtonStyle {
    let selected: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.monospaced().weight(.semibold))
            .frame(maxWidth: .infinity).padding(.vertical, 12)
            .background(selected ? Color.mint.opacity(0.2) : Color.white.opacity(0.06))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(selected ? Color.mint : .clear))
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

#Preview { MerchantTerminalView() }
