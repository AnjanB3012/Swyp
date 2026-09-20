import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { hashPayload, signPayload, generateNonce } from "../../shared/crypto.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SELLER_URL = "http://localhost:4001/negotiate";

const identity = JSON.parse(fs.readFileSync(path.join(__dirname, "identity.json"), "utf8"));

function buildOfferMessage() {
  const payload = { item: "concert-ticket", price: 45 };
  return {
    type: "offer",
    from_ans_name: identity.ansName,
    to_ans_name: "ans://seller-local.test",
    nonce: generateNonce(),
    timestamp: new Date().toISOString(),
    payload,
    payload_hash: hashPayload(payload),
    signature: signPayload(payload, identity.privateKey),
  };
}

async function postNegotiate(message) {
  const res = await fetch(SELLER_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(message),
  });
  const body = await res.json();
  return { status: res.status, body };
}

async function main() {
  const offerMessage = buildOfferMessage();

  console.log("[BUYER] Sending offer...");
  const offerResult = await postNegotiate(offerMessage);
  console.log(`[BUYER] Offer response (HTTP ${offerResult.status}):`, JSON.stringify(offerResult.body));

  // Attack demo: replay the exact same message (same nonce) a second time.
  console.log("[BUYER] Replaying the same offer (attack demo)...");
  const replayResult = await postNegotiate(offerMessage);
  console.log(`[BUYER] Replay response (HTTP ${replayResult.status}):`, JSON.stringify(replayResult.body));
}

main().catch((err) => {
  console.error("[BUYER] Error:", err);
  process.exit(1);
});
