# Optional demo backend

The NFC apps work without this service. If the iOS app is configured with a backend URL, this service re-verifies the signed challenge and can optionally create a Stripe **test-mode** PaymentIntent. It never receives or stores PAN, CVV, expiry, or real wallet credentials.

```bash
npm install
npm test
npm run build
ALLOW_IN_BAND_KEYS=true npm run dev
```

For a stronger demo, set `DEMO_CREDENTIALS_JSON` to a server-side mapping of credential IDs to Base64 SPKI public keys and omit `ALLOW_IN_BAND_KEYS`. To enable Stripe, also map each ID to a Stripe test PaymentMethod and set a test secret key:

```json
{"cred_amex_01":{"publicKey":"BASE64_SPKI","stripePaymentMethod":"pm_card_visa"}}
```

Use only Stripe test keys and test PaymentMethods. The in-band-key mode proves message integrity but, like the standalone NFC demo, does not establish issuer trust.
