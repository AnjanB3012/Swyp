# Swyp

A native Android wallet that compares card rewards with forecast spending, reads full checkout bills for matching deals, and recommends a card without leaving the current screen. It includes an iPhone NFC reader and a Java backend using Firebase, Nessie, and Gemini.

- **`android app/`** — Kotlin / Jetpack Compose, Firebase Auth and Firestore, required account profile setup, checkout photo and full-display capture, notification actions, recommendation engine, Android Keystore signing, NFC HCE, mapped nearby stores, and store dwell alerts.
- **`ios reader/`** — SwiftUI / Core NFC reader with merchant and amount entry; no reader sign-in is required.
- **`backend/`** — Java 17 service, Firebase Admin, Nessie adapter, synthetic history generation, Gemini extraction, daily deal scraper, Firestore rules and tests.

**Start with [SETUP.md](SETUP.md)** for keys, Firebase configuration, device setup, exact commands, and the walkthrough. [ARCHITECTURE.md](ARCHITECTURE.md) explains the data model, transaction boundary, forecast, and limitations.

This is a functional hackathon implementation with simulated cards and purchases. It does not issue real credit cards, obtain real credit scores, or pay at retail terminals. A bank token alone cannot replace the demo protocol: retail payments require an issuer/network-supported wallet and payment processing integration.
