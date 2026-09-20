import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import test from "node:test";
import { canonicalMessage, DemoPaymentRequest, verifyDemoSignature } from "../src/protocol.js";

test("canonical vector matches mobile implementations", () => {
  const request = sample();
  assert.equal(canonicalMessage(request).toString(), "tapchoice-v1\n100\nUSD\ndemo-terminal-01\nABEiM0RVZneImaq7zN3u_w\n1700000000\ncred_amex_01");
});

test("verifies P-256 DER signature", () => {
  const pair = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const request = sample();
  request.signature = sign("sha256", canonicalMessage(request), pair.privateKey).toString("base64");
  const publicKey = pair.publicKey.export({ format: "der", type: "spki" }).toString("base64");
  assert.equal(verifyDemoSignature(request, publicKey), true);
});

function sample(): DemoPaymentRequest {
  return {
    credentialId: "cred_amex_01", amountCents: 100, currency: "USD",
    terminalId: "demo-terminal-01", nonce: "ABEiM0RVZneImaq7zN3u_w",
    timestamp: 1_700_000_000, signature: "", publicKey: "",
  };
}

