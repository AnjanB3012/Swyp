import { createPublicKey, verify } from "node:crypto";

export interface DemoPaymentRequest {
  credentialId: string;
  amountCents: number;
  currency: string;
  terminalId: string;
  nonce: string;
  timestamp: number;
  signature: string;
  publicKey: string;
}

const id = /^[A-Za-z0-9_-]{1,64}$/;
const terminal = /^[A-Za-z0-9._-]{1,64}$/;
const nonce = /^[A-Za-z0-9_-]{22}$/;

export function canonicalMessage(request: DemoPaymentRequest): Buffer {
  if (!Number.isSafeInteger(request.amountCents) || request.amountCents < 1 || request.amountCents > 100_000_000 ||
      !/^[A-Z]{3}$/.test(request.currency) || !terminal.test(request.terminalId) ||
      !nonce.test(request.nonce) || !Number.isSafeInteger(request.timestamp) || request.timestamp < 1 ||
      !id.test(request.credentialId)) {
    throw new Error("invalid_payload");
  }
  return Buffer.from([
    "tapchoice-v1", String(request.amountCents), request.currency, request.terminalId,
    request.nonce, String(request.timestamp), request.credentialId,
  ].join("\n"), "utf8");
}

export function verifyDemoSignature(request: DemoPaymentRequest, trustedPublicKeyBase64: string): boolean {
  const key = createPublicKey({
    key: Buffer.from(trustedPublicKeyBase64, "base64"),
    format: "der",
    type: "spki",
  });
  return verify("sha256", canonicalMessage(request), key, Buffer.from(request.signature, "base64"));
}

