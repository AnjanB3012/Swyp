import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { hashPayload, signPayload, generateNonce } from "../../shared/crypto.js";
import { decideNegotiation } from "../../shared/llmClient.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SELLER_URL = "http://localhost:4001/negotiate";
const SELLER_ANS_NAME = "ans://seller-local.test";
const ITEM = "concert-ticket";

// Hardcoded buyer preferences for now — a config step later can make these adjustable.
const BUYER_TARGET_PRICE = 40;
const BUYER_CEILING_PRICE = 55;

const identity = JSON.parse(fs.readFileSync(path.join(__dirname, "identity.json"), "utf8"));

function buildMessage(type, price) {
  const payload = { item: ITEM, price };
  return {
    type,
    from_ans_name: identity.ansName,
    to_ans_name: SELLER_ANS_NAME,
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
  console.log("[BUYER] Deciding opening offer...");
  const openingDecision = await decideNegotiation({
    role: "buyer",
    item: ITEM,
    incomingOffer: null,
    targetPrice: BUYER_TARGET_PRICE,
    floorOrCeiling: BUYER_CEILING_PRICE,
  });
  console.log(`[BUYER] LLM reasoning: ${openingDecision.reasoning}`);

  const offerMessage = buildMessage("offer", openingDecision.price);

  console.log(`[BUYER] Sending offer @ ${openingDecision.price}...`);
  const offerResult = await postNegotiate(offerMessage);
  console.log(`[BUYER] Offer response (HTTP ${offerResult.status}):`, JSON.stringify(offerResult.body));

  const sellerResponse = offerResult.body.ok ? offerResult.body.response : null;

  // One follow-up round: only if the seller countered, not on accept/reject.
  if (sellerResponse && sellerResponse.type === "counter") {
    const sellerCounterPrice = sellerResponse.payload.price;
    console.log(`[BUYER] Seller countered @ ${sellerCounterPrice}. Deciding follow-up...`);

    const followUpDecision = await decideNegotiation({
      role: "buyer",
      item: ITEM,
      incomingOffer: sellerCounterPrice,
      targetPrice: BUYER_TARGET_PRICE,
      floorOrCeiling: BUYER_CEILING_PRICE,
    });
    console.log(`[BUYER] LLM reasoning: ${followUpDecision.reasoning}`);

    const followUpPrice =
      followUpDecision.action === "counter"
        ? followUpDecision.price
        : followUpDecision.action === "accept"
          ? sellerCounterPrice
          : null;
    const followUpMessage = buildMessage(followUpDecision.action, followUpPrice);

    console.log(
      `[BUYER] Sending ${followUpDecision.action}${followUpPrice != null ? " @ " + followUpPrice : ""}...`
    );
    const followUpResult = await postNegotiate(followUpMessage);
    console.log(`[BUYER] Follow-up response (HTTP ${followUpResult.status}):`, JSON.stringify(followUpResult.body));
  }

  // Attack demo: replay the original signed offer message (same nonce) a second time.
  console.log("[BUYER] Replaying the original offer (attack demo)...");
  const replayResult = await postNegotiate(offerMessage);
  console.log(`[BUYER] Replay response (HTTP ${replayResult.status}):`, JSON.stringify(replayResult.body));
}

main().catch((err) => {
  console.error("[BUYER] Error:", err);
  process.exit(1);
});
