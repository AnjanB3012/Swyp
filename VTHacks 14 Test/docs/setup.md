# Setup and physical-device test

## Android customer device

Requirements: NFC-capable Android phone with HCE, Android Studio, SDK 36, and JDK 17 or 21.

1. Open `android/` in Android Studio.
2. Let Gradle sync, select the physical phone, and run the `app` configuration.
3. Ensure NFC is enabled. Keep TapChoice foregrounded for the clearest demo feedback.
4. The default Kroger/$200 scenario automatically selects Amex Blue Cash and persists that ID for HCE.

CLI verification:

```bash
cd android
JAVA_HOME=/path/to/jdk-21 ./gradlew testDebugUnitTest assembleDebug
```

The debug APK is produced at `android/app/build/outputs/apk/debug/app-debug.apk`.

## iPhone merchant terminal

Core NFC tag reading requires a physical NFC-capable iPhone; the simulator only verifies compilation and UI.

1. Open `ios/TapChoiceTerminal.xcodeproj` in Xcode.
2. Select the app target, choose your Apple Development team, and keep **Near Field Communication Tag Reading** enabled.
3. Confirm the NFC entitlement contains the `TAG` reader format and `Info.plist` contains ISO 7816 identifier `F0123456789012`.
4. Connect a physical iPhone, select it as the run destination, and Run.
5. Select `$1.00`, tap **PAY**, then place the upper backs of the phones together.

CLI checks:

```bash
cd ios
swift test
xcodebuild -project TapChoiceTerminal.xcodeproj \
  -scheme TapChoiceTerminal -sdk iphonesimulator \
  -derivedDataPath /tmp/tapchoice-derived CODE_SIGNING_ALLOWED=NO build
```

## Troubleshooting

- If Android never appears: verify NFC is on, the phone supports HCE, and `F0123456789012` appears in `res/xml/apduservice.xml`.
- If iPhone immediately rejects: confirm the AID in the entitlement and both protocol implementations match exactly.
- If verification fails: set both phone clocks automatically; the allowed skew is 120 seconds.
- If the link drops: remove cases, keep both devices still, and move the iPhone's top edge slowly over the upper Android back.
- The iPhone simulator cannot perform the physical NFC exchange.

## Optional backend / Stripe test mode

The default empty `DEMO_BACKEND_URL` keeps approval entirely on-device. To add server gating, run the service in `backend/` as described in its README and set `DEMO_BACKEND_URL` in the iOS `Info.plist` to its reachable HTTPS base URL. When configured, the terminal shows approval only after the backend re-verifies the signature and approves; with `STRIPE_SECRET_KEY` configured, that means a successful Stripe test-mode PaymentIntent.
