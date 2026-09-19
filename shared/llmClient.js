import OpenAI from "openai";

const client = new OpenAI({
  apiKey: process.env.ARC_API_KEY,
  baseURL: process.env.ARC_BASE_URL,
});

const MODEL = process.env.ARC_MODEL || "gpt-oss-120b";

function buildSystemPrompt({ role, item, targetPrice, floorOrCeiling }) {
  if (role === "seller") {
    return (
      `You are the seller agent negotiating the sale of a "${item}". ` +
      `Your ideal price is ${targetPrice}. Your absolute minimum acceptable price is ${floorOrCeiling} — ` +
      `never accept or counter below that. Negotiate reasonably: don't immediately jump to your minimum, ` +
      `try to land closer to your ideal price first.`
    );
  }

  return (
    `You are the buyer agent negotiating the purchase of a "${item}". ` +
    `Your ideal price is ${targetPrice}. Your absolute maximum acceptable price is ${floorOrCeiling} — ` +
    `never offer or accept above that. Negotiate reasonably: don't immediately jump to your maximum, ` +
    `try to land closer to your ideal price first.`
  );
}

function buildUserPrompt({ incomingOffer }) {
  const instructions =
    'Respond ONLY with a JSON object (no markdown fences, no preamble, no explanation outside the JSON) ' +
    'of the shape: {"action": "offer" | "accept" | "counter" | "reject", "price": <number or null>, ' +
    '"reasoning": "<one short sentence>"}. ' +
    '"offer" is only valid when there is no incoming offer (opening move). ' +
    '"accept" and "reject" must have price: null. "counter" must include a price.';

  if (incomingOffer === null) {
    return `There is no incoming offer yet — you are opening the negotiation. ${instructions}`;
  }

  return `The other party's current offer is ${incomingOffer}. ${instructions}`;
}

function stripJsonFences(text) {
  return text
    .trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/, "")
    .trim();
}

function isValidDecision(decision) {
  if (!decision || typeof decision !== "object") return false;
  if (!["offer", "accept", "counter", "reject"].includes(decision.action)) return false;
  if (decision.action === "counter" && typeof decision.price !== "number") return false;
  if (decision.action === "offer" && typeof decision.price !== "number") return false;
  if (typeof decision.reasoning !== "string") return false;
  return true;
}

function fallbackDecision({ role, incomingOffer, targetPrice, floorOrCeiling }) {
  console.warn("[llmClient] Falling back to deterministic default decision (LLM call or parse failed).");

  if (role === "seller") {
    if (incomingOffer < floorOrCeiling) {
      return { action: "reject", price: null, reasoning: "Fallback: offer below minimum acceptable price." };
    }
    return { action: "accept", price: incomingOffer, reasoning: "Fallback: offer meets minimum acceptable price." };
  }

  return { action: "offer", price: targetPrice, reasoning: "Fallback: opening at target price." };
}

export async function decideNegotiation({ role, item, incomingOffer, targetPrice, floorOrCeiling }) {
  const system = buildSystemPrompt({ role, item, targetPrice, floorOrCeiling });
  const user = buildUserPrompt({ incomingOffer });

  let rawText;
  try {
    const response = await client.chat.completions.create({
      model: MODEL,
      max_tokens: 300,
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
    });
    rawText = response.choices[0].message.content;
  } catch (err) {
    console.warn(`[llmClient] LLM API call failed: ${err.message}`);
    return fallbackDecision({ role, incomingOffer, targetPrice, floorOrCeiling });
  }

  let decision;
  try {
    decision = JSON.parse(stripJsonFences(rawText));
  } catch {
    console.warn(`[llmClient] Failed to parse LLM response as JSON: ${rawText}`);
    return fallbackDecision({ role, incomingOffer, targetPrice, floorOrCeiling });
  }

  if (!isValidDecision(decision)) {
    console.warn(`[llmClient] LLM response missing required fields: ${JSON.stringify(decision)}`);
    return fallbackDecision({ role, incomingOffer, targetPrice, floorOrCeiling });
  }

  return decision;
}
