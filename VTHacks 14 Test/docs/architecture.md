# Architecture

## Prototype

```text
Recommendation engine
        ↓ selected credential ID
Android app + Android Keystore
        ↓ HostApduService / custom private AID
ISO-DEP physical NFC tap
        ↓ Core NFC APDUs
iPhone terminal + CryptoKit verification
        ↓
Approved demo transaction
```

The Android UI and HCE service share the selected card through persistent app preferences. The recommendation is written immediately, so the reader receives it without a second customer action. One card-specific non-exportable P-256 private key is created lazily in Android Keystore.

The public key is exchanged in-band for a self-contained judging demo. That proves possession but does not establish issuer trust.

## Production concept

```text
Issuer/network-approved credential
        ↓ secure provisioning and lifecycle controls
Hardware-backed credential storage
        ↓ certified contactless acceptance protocol
Acquirer / network / issuer authorization
```

A production version would require issuer and network approval, certified kernels and terminals, risk controls, attestation, backend-issued certificates, revocation, secure provisioning, and PCI/compliance work. It would not convert this private AID into an EMV card or extract credentials from Apple Pay or Google Wallet.

## Recommendation

For each card:

```text
rewardValue = amount × groceryRewardPercent
projectedUtilization = (balance + amount) / creditLimit
penalty = max(projectedUtilization − 30%, 0) × 200
score = rewardValue − penalty
```

For the included $200 Kroger example, Chase earns more rewards but crosses 30% utilization; Amex remains at 21% and wins the deterministic score.

