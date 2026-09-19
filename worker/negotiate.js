import fs from "node:fs";
import { verifySignature, hashPayload, signPayload, generateKeyPair, generateNonce } from "../shared/crypto.js";
import { ConnectionRegistry, AUTO_TRIP_REASONS } from "../supervisor/connectionRegistry.js";
import { AuditLog } from "../supervisor/auditLog.js";

const [INVALID_SIGNATURE, REPLAYED_NONCE, QUOTE_HASH_MISMATCH] = AUTO_TRIP_REASONS;

export class NonceStore {
  constructor() {
    this.seen = new Set();
  }

  has(nonce) {
    return this.seen.has(nonce);
  }

  add(nonce) {
    this.seen.add(nonce);
  }
}

export function verifyIncomingMessage(message, senderPublicKeyPem, nonceStore, connectionRegistry) {
  const gates = [];

  // 1. SIGNATURE GATE
  if (!verifySignature(message.payload, message.signature, senderPublicKeyPem)) {
    gates.push({ name: "signature", result: "fail" });
    connectionRegistry.recordViolation(message.from_ans_name, INVALID_SIGNATURE);
    return { ok: false, reason: INVALID_SIGNATURE, gates };
  }
  gates.push({ name: "signature", result: "pass" });

  // 2. REPLAY GATE
  if (nonceStore.has(message.nonce)) {
    gates.push({ name: "replay", result: "fail" });
    connectionRegistry.recordViolation(message.from_ans_name, REPLAYED_NONCE);
    return { ok: false, reason: REPLAYED_NONCE, gates };
  }
  nonceStore.add(message.nonce);
  gates.push({ name: "replay", result: "pass" });

  // 3. QUOTE-HASH GATE
  if (hashPayload(message.payload) !== message.payload_hash) {
    gates.push({ name: "quote_hash", result: "fail" });
    connectionRegistry.recordViolation(message.from_ans_name, QUOTE_HASH_MISMATCH);
    return { ok: false, reason: QUOTE_HASH_MISMATCH, gates };
  }
  gates.push({ name: "quote_hash", result: "pass" });

  // 4. CONNECTION GATE
  if (!connectionRegistry.isAllowed(message.from_ans_name)) {
    gates.push({ name: "connection", result: "fail" });
    return { ok: false, reason: "connection_tripped", gates };
  }
  gates.push({ name: "connection", result: "pass" });

  return { ok: true, reason: null, gates };
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

  const testLogPath = "logs/test-negotiate-audit.log";
  if (fs.existsSync(testLogPath)) {
    fs.unlinkSync(testLogPath);
  }

  const auditLog = new AuditLog(testLogPath);
  const connectionRegistry = new ConnectionRegistry(auditLog);
  const nonceStore = new NonceStore();

  const { publicKey, privateKey } = generateKeyPair();
  const fromAnsName = "ans://seller-test.example.com";

  function makeValidMessage() {
    const payload = { item: "ticket", price: 50 };
    const signature = signPayload(payload, privateKey);
    const payload_hash = hashPayload(payload);
    const nonce = generateNonce();
    return {
      type: "offer",
      from_ans_name: fromAnsName,
      to_ans_name: "ans://buyer-test.example.com",
      nonce,
      timestamp: new Date().toISOString(),
      payload,
      payload_hash,
      signature,
    };
  }

  // TEST A: valid message passes
  const messageA = makeValidMessage();
  const resultA = verifyIncomingMessage(messageA, publicKey, nonceStore, connectionRegistry);
  check("TEST A: valid message passes", resultA.ok === true && resultA.reason === null);

  // TEST B: replay the same message
  const resultB = verifyIncomingMessage(messageA, publicKey, nonceStore, connectionRegistry);
  check("TEST B: replayed message rejected", resultB.ok === false && resultB.reason === "replayed_nonce");

  // TEST C: tampered signature
  const messageC = makeValidMessage();
  const flippedChar = messageC.signature[0] === "A" ? "B" : "A";
  messageC.signature = flippedChar + messageC.signature.slice(1);
  const resultC = verifyIncomingMessage(messageC, publicKey, nonceStore, connectionRegistry);
  check("TEST C: tampered signature rejected", resultC.ok === false && resultC.reason === "invalid_signature");

  // TEST D: tampered payload_hash
  const messageD = makeValidMessage();
  messageD.payload_hash = "0".repeat(64);
  const resultD = verifyIncomingMessage(messageD, publicKey, nonceStore, connectionRegistry);
  check("TEST D: tampered payload_hash rejected", resultD.ok === false && resultD.reason === "quote_hash_mismatch");

  // TEST E: connection tripped
  connectionRegistry.trip(fromAnsName, "manual_test_trip");
  const messageE = makeValidMessage();
  const resultE = verifyIncomingMessage(messageE, publicKey, nonceStore, connectionRegistry);
  check("TEST E: tripped connection rejected", resultE.ok === false && resultE.reason === "connection_tripped");

  process.exit(failed ? 1 : 0);
}
