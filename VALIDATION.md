# Validation record

Validated on 2026-09-19 using JDK 21, Android SDK 36, Xcode 27, and the local Firestore emulator.

| Check | Result |
| --- | --- |
| Android `assembleDebug` | Passed; debug APK generated |
| Android `testDebugUnitTest` | 12 tests passed: recommendation/forecast behavior and NFC protocol parsing/canonical data |
| Android `lintDebug` | Passed with no errors; non-blocking dependency/KTX suggestions remain |
| Java backend tests | Passed with Java 21 running Java 17-compatible code |
| Java unit tests | 4 tests passed: signed amount integrity, canonical validation, acknowledgement-only Nessie responses, no automatic write retry |
| Firestore + mock Nessie integration | 1 test passed: required profile gate, empty wallet, 18-month generation, three cards/bills, signed terminal purchase, one balance increment, replay idempotence and uncertain-outcome locking |
| Firestore security rules | 5 tests passed: client receipt denial, cross-user isolation, server-only balances/config, malformed receipt denial, anonymous access denial |
| Live Firestore nearby seed | Passed: Kroger University City and Walmart Supercenter records written to project `swyp-84f73` |
| iOS physical-device target build | Passed with signing disabled |
| Swift package tests | 2 tests passed: Android/Swift canonical vector and P-256 DER signature interoperability |
| Whitespace and source credential checks | Passed |

The integration test uses a mock Nessie HTTP server, not a real Nessie account. Firebase Admin connectivity and the two store writes were tested live; actual generation against Nessie, Gemini extraction quality, delivered auth emails, scraped retailer availability and daily scheduling have **not** been tested live. Neither app has been exercised on physical devices during this implementation. NFC exchange, camera/screen capture, permission flows, and actual geofence dwell delivery require the device walkthrough in `SETUP.md`.

No real payment capabilities are implemented or implied. Offer publication currently includes a curator verification step; the daily job populates its review queue automatically. Signed taps are settled synchronously by the backend. These scope boundaries are described in `ARCHITECTURE.md` and `SETUP.md`.
