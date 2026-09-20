# Run Swyp

## This checkout: Firebase credentials connected

Configured and checked on 2026-09-19 for project **`swyp-84f73`**:

- Android's existing `app/google-services.json` values have been copied to ignored `android app/local.properties`.
- The supplied iOS `GoogleService-Info.plist` has been copied into the reader target and registered in **Copy Bundle Resources**. No additional plist import is needed on this machine.
- `backend/.env` points to the supplied service-account JSON in the repository root. That file and all credential configuration files are excluded from Git; the private JSON and `.env` have owner-only file permissions.
- The default Native-mode Firestore database exists. Deployed Firestore rules exactly match `backend/firestore.rules`; redeployment is not currently needed.
- Both apps were rebuilt with the configuration. The Java launcher started and returned `{"status":"ok"}` from `/health`, then was stopped.

**Remaining steps before creating your first account:**

1. Open [Firebase Authentication providers](https://console.firebase.google.com/project/swyp-84f73/authentication/providers) and enable **Email/Password**. The live configuration currently has this provider disabled.
2. Open [Google Cloud IAM](https://console.cloud.google.com/iam-admin/iam?project=swyp-84f73). Grant **Cloud Datastore User** (`roles/datastore.user`) to `firebase-adminsdk-fbsvc@swyp-84f73.iam.gserviceaccount.com`. The supplied key authenticates, but Firestore data reads currently return `PERMISSION_DENIED`. This role supplies the server's required Firestore read/write data permissions; do not loosen the client security rules to work around it. See [Firestore IAM roles](https://firebase.google.com/docs/firestore/security/iam).
3. Configure the remaining Nessie and Gemini secrets without putting them in shell history:

   ```sh
   ./backend/configure-env.sh
   ```

   The command prompts privately, saves both values in ignored `backend/.env`, and applies owner-only permissions. The other Firebase values are already set. Get the Nessie key from [Nessie](https://prod.nessieisreal.com) and create a separate Gemini API key in [Google AI Studio](https://aistudio.google.com/app/apikey). Do not reuse a Firebase client API key as the Gemini server key.
4. In Firestore, check/create **`config/app`** and choose Boolean **`auto-generate-transactions`**: `true` for generated history, `false` for an empty wallet. The field could not be checked because of the IAM denial. Add the Nessie key before signing up with generation enabled.
5. For the physical iPhone, select your Apple development team in Xcode. The Firebase bundle ID already matches `com.swyp.reader`.

**Start the backend** from the repository root:

```sh
./backend/run.sh
```

The launcher loads `.env`, selects JDK 17, builds the distribution, and starts the server. It currently can use the JDK downloaded at `/tmp/swyp-jdk17/amazon-corretto-17.jdk/Contents/Home`. Install a permanent JDK 17 if that temporary directory is removed. It does not install cron jobs or remain running after you close its terminal.

**Run Android:** open `android app/` in Android Studio, select Gradle JDK **17** (the path above is available now), select your emulator or phone, and press Run. The rebuilt APK is at `android app/app/build/outputs/apk/debug/app-debug.apk`.

- Emulator is already configured: `SWYP_BACKEND_URL=http://10.0.2.2:8080`.
- Physical Android: change that line in `android app/local.properties` to `SWYP_BACKEND_URL=http://172.16.2.130:8080`, then rebuild. `172.16.2.130` is the laptop's Wi-Fi IP observed during setup; use the current value from `ipconfig getifaddr en0` if it changes. Both devices need a network path to the laptop.

**Run iOS:** open `ios reader/TapChoiceTerminal.xcodeproj`, use scheme **TapChoiceTerminal**, choose your signing team and physical iPhone, and press Run. The reader does not use Firebase sign-in. Physical phones are required for NFC.

The remaining sections explain the general setup and optional deals/location configuration.

## 1. Required accounts and tools

- Android Studio, **JDK 17**, Android SDK 36, and a physical Android 11+ phone with NFC/HCE for tap testing. An emulator can exercise UI and checkout flows, but cannot perform the two-phone NFC exchange.
- Xcode and a physical NFC-capable iPhone running iOS 16+; an Apple signing team with the NFC Tag Reading capability. The simulator builds the reader UI but does not scan NFC.
- A Firebase project with Authentication and Firestore, a Nessie API key, and a Gemini API key. Gemini is the only LLM integration.
- Node 20/22 for the optional Firebase CLI and rules tests. The Java runtime handles all application backend work.

The checked-in Gradle wrappers use Gradle 8.11.1. Run them with JDK 17, not Java 25. Set `JAVA_HOME` to your installed JDK 17. On macOS with a registered JDK:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
```

## 2. Firebase

1. Create a Firebase project and enable **Authentication → Sign-in method → Email/Password**. Configure password policy and email templates for verification/reset. Create Firestore in Native mode.
2. Register Android package **`com.swyp.app`**. Download `google-services.json` to `android app/app/google-services.json` (ignored by Git). This project initializes Firebase explicitly: copy the three public configuration values listed below into `local.properties`. It does not automatically run the Google Services Gradle plugin.
3. Register iOS bundle **`com.swyp.reader`** if you want to keep the existing Firebase app registration. The reader no longer authenticates with Firebase and does not require `GoogleService-Info.plist` at runtime; it submits a signed NFC proof to the Java backend.
4. Create a service-account key for local backend development. Keep it **outside the repository**. Set `GOOGLE_APPLICATION_CREDENTIALS` to its absolute path and `GOOGLE_CLOUD_PROJECT` to the project ID. For deployment, prefer an attached service account rather than a JSON key.
5. Publish the rules from `backend/firestore.rules` before using the apps. From `backend/`:

```sh
npm ci
npx firebase login
npx firebase deploy --only firestore --project YOUR_PROJECT_ID
```

6. In Firestore Console create document **`config/app`**, with the Boolean field **`auto-generate-transactions`**:

```json
{"auto-generate-transactions": true}
```

- `true`: after the Android user completes the required profile, the backend creates a synthetic persona, three simulated cards, approximately 18 months of purchases, recurring bills, statement snapshots, and simulated utilization indicators. Android initiates the job; the backend performs the Nessie requests to protect the API key. Keep the backend running. Generation makes hundreds of API calls and can take several minutes.
- `false` or missing: creates an empty wallet after profile setup, with no cards, transactions, bills, or score history. **Create cards** in Cards creates simulated cards without generating historical purchases.
- The flag is sampled once when `users/{uid}` is first created. Changing it does not overwrite existing wallets. Use a new Firebase Auth user to test the other mode.
- Generation failures set `status: needs_attention`. Inspect backend logs/Nessie before repairing the state. Interrupted jobs are intentionally not replayed automatically.

The service account writes account data and payment receipts. Clients can read only their own wallet; they cannot create receipts or change balances, scores, flags, verified deals, or settlement status.

## 3. Java backend and API keys

Copy `backend/.env.example` to `backend/.env`, edit it (quote paths containing spaces), then export its values in the shell. The Java app does **not** silently read `.env` files.

```sh
cd backend
set -a
source .env
set +a
./gradlew test installDist
./build/install/swyp-backend/bin/swyp-backend
```

| Variable | Where to obtain it | Required for |
| --- | --- | --- |
| `GOOGLE_APPLICATION_CREDENTIALS` | Absolute path to Firebase service-account JSON | Backend Firebase authorization/data access |
| `GOOGLE_CLOUD_PROJECT` | Firebase project ID | All backend requests |
| `NESSIE_API_KEY` | Sign in at https://prod.nessieisreal.com | Card/customer/merchant creation, history and tap purchases |
| `NESSIE_BASE_URL` | Default `https://prod-api.nessieisreal.com` | Nessie API server; do not use the docs website |
| `GEMINI_API_KEY` | Google AI Studio | Checkout extraction and deal extraction |
| `GEMINI_MODEL` | Default `gemini-2.5-flash`; choose a model enabled on your account | Gemini calls |
| `PORT` | Default `8080` | Local Java HTTP listener |
| `DEALS_ENABLED` | `true` / `false` | In-process daily UTC deal collection |

The backend listens on the laptop's interfaces. `GET /health` is a basic process check. Android endpoints require a Firebase ID token. `/v1/terminal/payment` accepts only a fresh, signed proof for a card armed by its authenticated Android owner. Use HTTPS behind a reverse proxy/tunnel for deployment. The checked-in iOS HTTP exception exists for the same-Wi-Fi local run only.

Nessie and Gemini keys are server environment variables, **never mobile BuildConfig values or iOS bundle resources**. Firebase client configuration is public project configuration, not a service-account secret.

Nessie's current OpenAPI has some omissions/differences from its older official SDKs. Purchases use `POST /accounts/{id}/purchases` with `purchase_date`, as documented by the official SDK. Created-object and acknowledgement-only responses are handled; ambiguous creation outcomes stop for reconciliation. You must still perform a live smoke test with your own key before the demo.

## 4. Android app

Open **`android app/`** as the Android Studio project. Create its ignored `local.properties` using `local.properties.example`:

```properties
sdk.dir=/absolute/path/to/Android/sdk
SWYP_BACKEND_URL=http://10.0.2.2:8080
FIREBASE_API_KEY=google-services-json.client.api_key.current_key
FIREBASE_PROJECT_ID=google-services-json.project_info.project_id
FIREBASE_APP_ID=google-services-json.client.client_info.mobilesdk_app_id
```

Those right-hand sides describe where to find the values; replace them with the actual strings. Environment variables with the same names override the file. Rebuild after changing them.

- **Android emulator:** `10.0.2.2` reaches the laptop backend.
- **Physical Android phone:** use `http://YOUR_LAPTOP_LAN_IP:8080` on the same Wi-Fi, or an HTTPS tunnel. `127.0.0.1` refers to the phone. On macOS, `ipconfig getifaddr en0` often gives the Wi-Fi address. Permit port 8080 in your firewall.
- Firebase Auth/Firestore use the real Firebase project unless you explicitly add emulator configuration. The test emulator configuration is used only by rules tests.

```sh
cd 'android app'
./gradlew testDebugUnitTest assembleDebug lintDebug
```

APK: `android app/app/build/outputs/apk/debug/app-debug.apk`.

Sign up or sign in; password reset sends a Firebase email. Signup also requests an email verification email. Swyp requires a full name, age, phone number, and home ZIP before wallet setup. Existing accounts missing any of those fields are sent to the same profile form after sign-in. Account setup status and failures appear in the app. There is no fake successful login when Firebase is missing.

**Checkout capture**

- Edit Quick Settings and add **Scan with Swyp**. Open a checkout in another app, pull down the panel, and tap the tile.
- The first tile use after install, reboot, screen lock, or manually stopping capture requires Android's screen-capture consent. On Android 14+, Swyp requests the full display directly, so supported devices omit the app-versus-entire-screen choice. The system consent itself cannot be bypassed, and some device manufacturers can override the full-display request.
- Swyp keeps that approved capture session available through a required low-priority foreground-service notification. Later Quick Settings taps in the same session quietly capture the current display without opening Swyp or asking again. Use **Stop** on the persistent notification to revoke the session.
- Swyp returns to the screen you were viewing and posts **Send** and **Dismiss** notification actions. **Send** uploads the private temporary image to Gemini through the backend, reads every visible bill item, checks matching deals, and compares cards. **Dismiss** or swiping the notification deletes the image without analysis. The screenshot is never written to Firestore or the backend's disk.
- The result notification says **Use _card nickname_ •••• _last four_** and summarizes the merchant, total, visible item count, and the first matching deal (plus a count when more match). Tapping that result opens Pay with the recommended card selected.
- The Scan tab's camera and photo flow still lets you review the detected items, matching offers, merchant, total, and category before comparing cards.
- Open **Pay** from the bottom navigation, choose any eligible card, and tap **Ready to pay**. This prepares that card for two minutes; the iPhone supplies the merchant and amount and Android signs both values.
- While prepared, Pay displays a prominent **Ready to tap** tile with the selected nickname and last four digits.
- Open **Cards** and tap a card to view only that card's transactions.
- Camera and photo import are also available. Camera capture uses a private temporary cache file shared with the system camera through FileProvider; it is deleted after the result is decoded or cancelled. Imported images are scaled to a maximum dimension of 1600 pixels before analysis. Android protected/secure surfaces may appear blank and cannot be bypassed.

## 5. iPhone reader

Open **`ios reader/TapChoiceTerminal.xcodeproj`**, choose the **TapChoiceTerminal** scheme (the reference project's internal target name), and select your Apple signing team. Bundle ID is `com.swyp.reader`; change it and the Firebase app registration together if needed.

- Enable **Near Field Communication Tag Reading** under Signing & Capabilities. Existing entitlements include ISO7816 AID `F0123456789012`.
- Set `TERMINAL_BACKEND_URL` in `TapChoiceTerminal/Info.plist` to `http://YOUR_LAPTOP_LAN_IP:8080`. The checked-in local value is `http://172.16.2.130:8080`; update it whenever the laptop IP changes. Keep iPhone and laptop on the same Wi-Fi and allow the Local Network permission when prompted.
- Install on the physical iPhone. There is no login screen: enter the merchant name and amount, then tap **Tap to Pay**.
- On Android, open **Pay**, choose a card, and tap **Ready to pay**. Hold the Android NFC antenna near the top of the iPhone within two minutes.
- The iPhone verifies the Android signature, then sends the signed merchant, amount, card ID, nonce, and timestamp to `/v1/terminal/payment`. The backend resolves the card owner through a server-only credential record created by **Ready to pay**, checks the two-minute authorization window, posts to Nessie, and writes the transaction to that card. No collection-group index is required.
- A `6985` status means Android has no prepared card or the two-minute window expired. Prepare the card again in Android Pay and retry.

Build and protocol tests:

```sh
xcodebuild -project 'ios reader/TapChoiceTerminal.xcodeproj' \
  -scheme TapChoiceTerminal -sdk iphonesimulator \
  -derivedDataPath /tmp/swyp-ios-build CODE_SIGNING_ALLOWED=NO build
swift test --package-path 'ios reader'
```

## 6. Daily deals and store dwell alerts

In Firestore, add administrator-curated documents under `dealSources/`:

```json
{"url":"https://www.kroger.com/pr/digital-coupons","enabled":true}
```

Allowed hosts: `www.kroger.com`, `www.walmart.com`, `www.target.com`, `www.capitalone.com`. Configure currently permitted public promotion URLs. The scraper respects server errors, disables redirects, caps content size, and does not bypass login, anti-bot blocks, or scrape private personalized offers. Check the site's terms and robots policy before enabling a source. Ordinary sales without explicit card-linked terms are excluded; it is valid to find zero offers.

- `DEALS_ENABLED=true`: refreshes daily at **00:00 UTC** while the Java process is running.
- For laptop cron, leave that variable false and run the installed CLI once daily (use absolute paths, including a launcher script that exports the environment and `JAVA_HOME`):

```cron
0 8 * * * /absolute/path/to/run-swyp-deals.sh
```

Example `run-swyp-deals.sh` outside Git, with your actual paths:

```sh
#!/bin/sh
set -a
. '/absolute/path/to/backend/.env'
set +a
export JAVA_HOME='/absolute/path/to/jdk17'
exec '/absolute/path/to/backend/build/install/swyp-backend/bin/swyp-backend' --deals-once
```

The laptop must be awake. Cron uses the laptop's timezone; missed runs are not caught up. Do not enable both schedulers.

Gemini extracts offers into **`dealCandidates`** with source URL, evidence quote, expiry, product, minimum spend, cap, and additional rebate. A curator checks actual eligibility, activation, caps, reward stacking and terms, then copies a valid record into **`deals/{same-id}`** with `verified: true`. This review prevents scraped or generated claims from silently affecting financial recommendations. Scraping is automatic; offer publication currently requires review.

Published schema:

```json
{
  "id":"offer-id", "title":"Actual verified offer title", "merchant":"Kroger",
  "product":"savor", "bonusPercent":5, "minimumCents":5000, "capCents":1000,
  "expiresOn":"2026-12-31", "sourceUrl":"https://www.kroger.com/ACTUAL_OFFER",
  "verified":true, "requiresActivation":true,
  "itemKeywords":["ketchup","tomato ketchup"]
}
```

This is a **schema illustration, not a real Kroger offer**. Do not publish it as a real promotion. `product` is `savor`, `quicksilver`, `venture` or `any`. Publish only offers that are additional cash rebates and compatible with the base reward assumptions. The app counts at most one offer per purchase, does not stack multiple deals, and only counts activation-required offers after the user confirms activation.

The backend seeds these store documents on every startup (or once with `./gradlew run --args=--seed-stores`):

```json
{"merchant":"Kroger","name":"Kroger University City","address":"903 University City Blvd, Blacksburg, VA 24060","latitude":37.2354614,"longitude":-80.4352291,"radiusMeters":180}
{"merchant":"Walmart","name":"Walmart Supercenter","address":"2400 N Franklin St, Christiansburg, VA 24073","latitude":37.1605205,"longitude":-80.4257804,"radiusMeters":220}
```

Nearby renders the configured stores on an OpenStreetMap map and lists their addresses. In **Nearby → Allow location**, grant precise foreground location, then **Allow all the time** in Android settings. The permission prompt card disappears once precise location is granted. After two minutes inside a configured geofence, Swyp notifies about verified, unexpired offers, at most once per store per six hours. Up to 100 configured stores are registered. Raw location is not uploaded. Turn alerts off in-app to remove geofences. Re-enable after device reboot, reinstall or app-data reset. Android power management can delay geofence delivery.

## 7. Verification and device walkthrough

```sh
cd backend
./gradlew test
npm ci
npm run test:rules
```

Rules tests use isolated project `demo-swyp`, not your production project. Run the Firebase CLI with a supported Java runtime (new emulator releases may require Java 21+; Java 25 can run the emulator even though Gradle here requires Java 17).

1. Set the Firestore generation flag true; launch backend, create a new Android user, and complete the required profile.
2. Wait until Cards shows three cards and generated history; inspect the associated Nessie customer/accounts/purchases.
3. Scan a receipt containing several visible items. Confirm all lines appear, then compare a Kroger checkout and Walmart at the same amount; Savor's grocery category excludes Walmart and Target.
4. Lower the utilization target or use a larger purchase to see forecast reservations make a card ineligible. The app can return **no eligible card**.
5. In Android Pay, prepare an eligible card. Enter a merchant and small amount on iPhone, tap, and verify the request becomes `posted`, a Nessie purchase ID appears, and the Android card balance increases once.
6. Test false generation with a second fresh user: the wallet must be empty. Add cards explicitly if desired.
7. Test the screenshot tile on a real device and a store dwell alert using an actual verified offer.

## 8. Failure recovery

- **No Firebase configuration:** app shows setup instructions; no fake data or sign-in success.
- **Backend unreachable:** verify laptop IP, port, firewall, Firebase service-account project and key variables.
- **`needs_attention` during generation:** keep partial records for inspection. Compare Nessie to Firestore; do not blindly delete the user document and recreate duplicate remote accounts.
- **`processing` after a backend crash or `needs_reconciliation`:** inspect the Nessie account's purchases for description `swyp:<nonce>`. If present, use an administrator transaction to attach its ID, mark the receipt posted, increment debt only if its `transactions/{nonce}` ledger entry does not already exist, write that ledger entry, and clear card `posting`. If absent, confirm the remote outcome with Nessie before resetting to pending and clearing the lock. There is no automatic exactly-once guarantee across Nessie and Firestore.
- **Status `6985`:** prepare a card again in Android Pay. The prior two-minute NFC authorization was missing, consumed, or expired.
- **No live deals:** inspect `dealSources.lastError`, Gemini access, public page accessibility, and the review queue. The app deliberately does not fabricate fallback offers.
