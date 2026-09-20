import express from "express";
import { DemoPaymentRequest, verifyDemoSignature } from "./protocol.js";

type CredentialConfig = { publicKey: string; stripePaymentMethod?: string };
const credentials: Record<string, CredentialConfig> = JSON.parse(process.env.DEMO_CREDENTIALS_JSON ?? "{}");
const allowInBandKeys = process.env.ALLOW_IN_BAND_KEYS === "true";
const consumed = new Map<string, number>();

export const app = express();
app.use(express.json({ limit: "16kb" }));

app.post("/demo-payment", async (req, res) => {
  try {
    const body = req.body as DemoPaymentRequest;
    const now = Math.floor(Date.now() / 1000);
    if (Math.abs(now - body.timestamp) > 120) return res.status(400).json({ approved: false, reason: "expired" });
    if (consumed.has(body.nonce)) return res.status(409).json({ approved: false, reason: "replay" });

    const configured = credentials[body.credentialId];
    const publicKey = configured?.publicKey ?? (allowInBandKeys ? body.publicKey : undefined);
    if (!publicKey || !verifyDemoSignature(body, publicKey)) {
      return res.status(401).json({ approved: false, reason: "invalid_signature" });
    }
    consumed.set(body.nonce, now);
    for (const [nonce, usedAt] of consumed) if (now - usedAt > 300) consumed.delete(nonce);

    if (!process.env.STRIPE_SECRET_KEY) {
      return res.json({ approved: true, mode: "local-demo" });
    }
    if (!configured?.stripePaymentMethod) {
      return res.status(400).json({ approved: false, reason: "unmapped_credential" });
    }

    const form = new URLSearchParams({
      amount: String(body.amountCents),
      currency: body.currency.toLowerCase(),
      payment_method: configured.stripePaymentMethod,
      confirm: "true",
      off_session: "true",
      description: `TapChoice demo ${body.credentialId}`,
    });
    const stripe = await fetch("https://api.stripe.com/v1/payment_intents", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${process.env.STRIPE_SECRET_KEY}`,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: form,
    });
    const result = await stripe.json() as { id?: string; status?: string; error?: { message?: string } };
    if (!stripe.ok || result.status !== "succeeded") {
      return res.status(402).json({ approved: false, reason: result.error?.message ?? result.status ?? "declined" });
    }
    return res.json({ approved: true, mode: "stripe-test", paymentIntentId: result.id });
  } catch {
    return res.status(400).json({ approved: false, reason: "invalid_request" });
  }
});

if (process.env.NODE_ENV !== "test") {
  const port = Number(process.env.PORT ?? 8787);
  app.listen(port, () => console.log(`TapChoice demo backend listening on ${port}`));
}

