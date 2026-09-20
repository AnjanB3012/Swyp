# TapChoice NFC protocol v1

This is a private demo protocol, not EMV and not a payment-network credential. It never carries a PAN, CVV, expiry date, wallet token, or Secure Element secret.

## Transport

- ISO-DEP / ISO 7816-4 short APDUs
- Private AID: `F0123456789012` (7 bytes)
- Payload encoding: compact UTF-8 JSON
- Successful response status: `9000`

Commands may include an optional one-byte `Le`; `00` means 256. Command payloads must fit short APDU `Lc` (1–255 bytes).

| Operation | Command |
|---|---|
| Select | `00 A4 04 00 07 F0 12 34 56 78 90 12 [Le]` |
| Get credential | `80 10 00 00 [Le]` |
| Authorize | `80 20 00 00 Lc <challenge JSON> [Le]` |

## Credential

`GET CREDENTIAL` returns:

```json
{"credentialId":"cred_amex_01","displayName":"Amex Blue Cash","last4":"5678","network":"Amex","publicKey":"<base64>"}
```

`publicKey` is a Base64 X.509 SubjectPublicKeyInfo DER encoding of an ECDSA P-256 key. The Android private key is generated as non-exportable key material in Android Keystore. A production system would validate an issuer/backend certificate rather than trusting this in-band public key.

## Authorization

The iPhone generates a fresh 16-byte random nonce and sends:

```json
{"amountCents":100,"currency":"USD","terminalId":"demo-terminal-01","nonce":"<base64url-no-padding>","timestamp":1700000000}
```

The exact signed bytes are UTF-8 fields joined by `LF` (`0A`), with no trailing newline:

```text
tapchoice-v1
<amountCents decimal>
<currency>
<terminalId>
<nonce>
<timestamp decimal Unix seconds>
<credentialId>
```

All string fields are restricted to alphabets that exclude newlines. The Android service signs these bytes with `SHA256withECDSA`, producing an ASN.1 DER `(r,s)` signature. It returns:

```json
{"credentialId":"cred_amex_01","signature":"<base64 DER signature>"}
```

CryptoKit verifies with `P256.Signing.PublicKey(derRepresentation:)` and `ECDSASignature(derRepresentation:)`. The terminal rejects credential changes, signatures that fail, challenges more than 120 seconds from its clock, and nonces already consumed in the current app process.

## Status words

| Status | Meaning |
|---|---|
| `9000` | Success |
| `6A82` | AID not found |
| `6A80` | Malformed or invalid payload |
| `6985` | SELECT/GET precondition not satisfied |
| `6D00` | Instruction not supported |

