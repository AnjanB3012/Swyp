import fs from "node:fs";
import { AuditLog } from "./auditLog.js";

export const CONNECTION_STATES = {
  OPEN: "OPEN",
  TRIPPED: "TRIPPED",
  HALF_OPEN: "HALF_OPEN",
};

export const AUTO_TRIP_REASONS = ["invalid_signature", "replayed_nonce", "quote_hash_mismatch"];

export class ConnectionRegistry {
  constructor(auditLog, options = {}) {
    this.auditLog = auditLog;
    this.violationThreshold = options.violationThreshold ?? 3;
    this.cooldownMs = options.cooldownMs ?? 30000;
    this.connections = new Map();
  }

  getOrCreate(ansName) {
    let connection = this.connections.get(ansName);
    if (!connection) {
      connection = {
        ansName,
        state: CONNECTION_STATES.OPEN,
        violationCount: 0,
        trippedReason: null,
        trippedAt: null,
        lastCheckedAt: null,
      };
      this.connections.set(ansName, connection);
    }
    return connection;
  }

  recordViolation(ansName, reason) {
    const connection = this.getOrCreate(ansName);
    connection.violationCount += 1;
    connection.lastCheckedAt = Date.now();

    this.auditLog.append({ type: "violation", ans_name: ansName, reason });

    if (AUTO_TRIP_REASONS.includes(reason) || connection.violationCount >= this.violationThreshold) {
      return this.trip(ansName, reason, { auto: true });
    }

    return connection;
  }

  trip(ansName, reason, { auto = false } = {}) {
    const connection = this.getOrCreate(ansName);
    connection.state = CONNECTION_STATES.TRIPPED;
    connection.trippedReason = reason;
    connection.trippedAt = Date.now();

    this.auditLog.append({ type: "trip", ans_name: ansName, reason, auto });

    return connection;
  }

  manualToggle(ansName, newState) {
    if (!Object.values(CONNECTION_STATES).includes(newState)) {
      throw new Error(`Invalid connection state: ${newState}. Must be one of ${Object.values(CONNECTION_STATES).join(", ")}`);
    }

    const connection = this.getOrCreate(ansName);
    connection.state = newState;

    this.auditLog.append({ type: "manual_override", ans_name: ansName, new_state: newState });

    return connection;
  }

  attemptReconnect(ansName) {
    const connection = this.getOrCreate(ansName);

    if (connection.state !== CONNECTION_STATES.TRIPPED || Date.now() - connection.trippedAt < this.cooldownMs) {
      return connection;
    }

    connection.state = CONNECTION_STATES.HALF_OPEN;
    connection.violationCount = 0;

    this.auditLog.append({ type: "reconnect_attempt", ans_name: ansName });

    return connection;
  }

  isAllowed(ansName) {
    const connection = this.getOrCreate(ansName);
    return connection.state === CONNECTION_STATES.OPEN || connection.state === CONNECTION_STATES.HALF_OPEN;
  }

  getAll() {
    return Array.from(this.connections.values());
  }
}

export default ConnectionRegistry;

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

  const testLogPath = "logs/test-audit.log";
  if (fs.existsSync(testLogPath)) {
    fs.unlinkSync(testLogPath);
  }

  const auditLog = new AuditLog(testLogPath);
  const registry = new ConnectionRegistry(auditLog, { violationThreshold: 2 });

  const sellerA = "ans://seller-a.example.com";
  const sellerB = "ans://seller-b.example.com";

  registry.getOrCreate(sellerA);
  check("seller-a is allowed on creation", registry.isAllowed(sellerA) === true);

  registry.recordViolation(sellerA, "slow_response");
  check(
    "seller-a still OPEN and allowed after 1 sub-threshold violation",
    registry.getAll().find((c) => c.ansName === sellerA).state === CONNECTION_STATES.OPEN && registry.isAllowed(sellerA) === true
  );

  registry.recordViolation(sellerA, "slow_response");
  check(
    "seller-a auto-trips at threshold and is no longer allowed",
    registry.getAll().find((c) => c.ansName === sellerA).state === CONNECTION_STATES.TRIPPED && registry.isAllowed(sellerA) === false
  );

  registry.recordViolation(sellerB, "invalid_signature");
  const sellerBConn = registry.getAll().find((c) => c.ansName === sellerB);
  check(
    "seller-b trips immediately on AUTO_TRIP_REASONS with violationCount 1",
    sellerBConn.state === CONNECTION_STATES.TRIPPED && sellerBConn.violationCount === 1
  );

  registry.manualToggle(sellerA, CONNECTION_STATES.OPEN);
  check("seller-a allowed again after manualToggle to OPEN", registry.isAllowed(sellerA) === true);

  const events = auditLog.readAll();
  const hasType = (type) => events.some((e) => e.type === type);
  check(
    "audit log contains violation, trip, and manual_override events",
    hasType("violation") && hasType("trip") && hasType("manual_override")
  );

  process.exit(failed ? 1 : 0);
}
