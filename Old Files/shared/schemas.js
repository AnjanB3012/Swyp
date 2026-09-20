/**
 * @typedef {Object} NegotiationMessage
 * @property {string} type - Message type/intent (e.g. "offer", "counter", "accept", "reject").
 * @property {string} from_ans_name - ANS-registered name of the sending agent.
 * @property {string} to_ans_name - ANS-registered name of the intended recipient agent.
 * @property {string} nonce - Unique per-message value used to detect and reject replayed requests.
 * @property {string} timestamp - ISO 8601 timestamp of when the message was created.
 * @property {Object} payload - Negotiation-specific data (e.g. quote terms, price, quantity).
 * @property {string} payload_hash - Hash of `payload`, used to detect swapped/tampered quotes.
 * @property {string} signature - Signature over the message (proves authenticity, binds to from_ans_name).
 */

// No validation library wired up yet. This file exists to document the wire
// format that supervisor/worker/agents will exchange once negotiation logic
// is implemented.
