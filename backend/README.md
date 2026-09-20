# Swyp Java backend

See [../SETUP.md](../SETUP.md) for credentials, Firebase deployment, daily scheduler setup, and commands. Runtime: Java 17, Gradle application distribution. Firebase CLI/Node dependencies here are only for security-rule deployment/testing.

For this checkout, Firebase credentials are already connected. Add the remaining Nessie/Gemini keys to `.env`, complete the Firebase console steps in `SETUP.md`, then run `./run.sh`. The launcher loads the environment and selects JDK 17.

Authenticated JSON `POST` endpoints:

| Route | Request | Behavior |
| --- | --- | --- |
| `/v1/initialize` | `{}` | Create the profile once; honor `config/app.auto-generate-transactions` for a new user |
| `/v1/cards` | `{}` | Create three simulated cards for an initialized empty wallet |
| `/v1/checkout` | `{ "image": "base64 JPEG" }` | Gemini structured checkout extraction; no image persistence |
| `/v1/settings` | `{ "utilizationLimit": 0.3 }` or `{ "activatedOffer": "id" }` | Store owner preferences |
| `/v1/credentials` | `{ "cardId": "id", "publicKey": "base64 SPKI DER", "amountCents": 2599, "merchant": "Kroger", "category": "grocery" }` | Register owner device key and two-minute confirmed checkout window |
| `/v1/payments/process` | `{ "nonce": "22-char base64url" }` | Verify/claim pending Firestore receipt, post once or block for reconciliation |

All require `Authorization: Bearer <Firebase ID token>`. `GET /health` reports only process availability, not provider credentials or connectivity.

Unit tests: `./gradlew test`. Rules tests: `npm ci && npm run test:rules`. `BankIntegrationTest` additionally runs if `FIRESTORE_EMULATOR_HOST` is set; it uses an isolated `demo-swyp-bank` Firestore project and an in-process mock Nessie HTTP server to verify generation, debt posting, replay handling and uncertain-outcome blocking. It does not use a real Nessie key.
