import crypto from "node:crypto";

/**
 * Recursively sorts object keys so that logically identical objects always
 * produce the same JSON string, regardless of key insertion order.
 */
function sortKeysDeep(value) {
  if (Array.isArray(value)) {
    return value.map(sortKeysDeep);
  }
  if (value !== null && typeof value === "object") {
    const sorted = {};
    for (const key of Object.keys(value).sort()) {
      sorted[key] = sortKeysDeep(value[key]);
    }
    return sorted;
  }
  return value;
}

function stableStringify(payload) {
  return JSON.stringify(sortKeysDeep(payload));
}

export function generateKeyPair() {
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ed25519", {
    publicKeyEncoding: { type: "spki", format: "pem" },
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
  });
  return { publicKey, privateKey };
}

export function signPayload(payload, privateKeyPem) {
  const data = Buffer.from(stableStringify(payload));
  const signature = crypto.sign(null, data, privateKeyPem);
  return signature.toString("base64");
}

export function verifySignature(payload, signatureBase64, publicKeyPem) {
  try {
    const data = Buffer.from(stableStringify(payload));
    const signature = Buffer.from(signatureBase64, "base64");
    return crypto.verify(null, data, publicKeyPem, signature);
  } catch {
    return false;
  }
}

export function hashPayload(payload) {
  return crypto.createHash("sha256").update(stableStringify(payload)).digest("hex");
}

export function generateNonce() {
  return crypto.randomBytes(16).toString("hex");
}

if (import.meta.url === `file://${process.argv[1]}`) {
  let failed = false;

  function check(label, condition) {
    if (condition) {
      console.log(`PASS: ${label}`);
    } else {
      console.log(`FAIL: ${label}`);
      failed = true;
    }
  }

  const { publicKey, privateKey } = generateKeyPair();
  check("generateKeyPair produces PEM strings", publicKey.includes("PUBLIC KEY") && privateKey.includes("PRIVATE KEY"));

  const original = { price: 50, item: "ticket" };
  const signature = signPayload(original, privateKey);
  check("signPayload produces a base64 signature", typeof signature === "string" && signature.length > 0);

  const validVerification = verifySignature(original, signature, publicKey);
  check("verifySignature accepts a valid signature", validVerification === true);

  const tampered = { ...original, price: 999 };
  const invalidVerification = verifySignature(tampered, signature, publicKey);
  check("verifySignature rejects a tampered payload", invalidVerification === false);

  const hash1 = hashPayload(original);
  const hash2 = hashPayload(original);
  check("hashPayload is deterministic", hash1 === hash2);

  process.exit(failed ? 1 : 0);
}
