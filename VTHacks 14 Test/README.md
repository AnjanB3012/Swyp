# TapChoice

TapChoice is a two-device hackathon prototype: an Android phone recommends a demo card and exposes a custom credential through Host Card Emulation; an iPhone reads and verifies a signed transaction over Core NFC.

This project deliberately uses a private, non-payment AID (`F0123456789012`). It does not emulate EMV, access wallet credentials, or represent a bank-issued card.

## Repository

- `android/` – Kotlin, Jetpack Compose, Android HCE
- `ios/` – SwiftUI, Core NFC, CryptoKit
- `backend/` – optional TypeScript demo authorization service (no PAN data)
- `docs/` – protocol, architecture, setup, and judging script

Start with [docs/setup.md](docs/setup.md).

