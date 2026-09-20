import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { EventEmitter } from "node:events";
import { fileURLToPath } from "node:url";
import { verifyIncomingMessage, NonceStore } from "../../worker/negotiate.js";
import { ConnectionRegistry, CONNECTION_STATES } from "../../supervisor/connectionRegistry.js";
import { AuditLog } from "../../supervisor/auditLog.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const PORT = 4001;

const identity = JSON.parse(fs.readFileSync(path.join(__dirname, "identity.json"), "utf8"));

const auditLog = new AuditLog("logs/seller-audit.log");
const connectionRegistry = new ConnectionRegistry(auditLog);
const nonceStore = new NonceStore();
const events = new EventEmitter();

// In a real ANS setup, known buyers' public keys would come from resolving their
// identity cert via ANS lookup rather than reading a local identity file directly.
const buyerIdentity = JSON.parse(
  fs.readFileSync(path.join(__dirname, "..", "buyer", "identity.json"), "utf8")
);
const knownBuyers = new Map([[buyerIdentity.ansName, buyerIdentity.publicKey]]);

const DASHBOARD_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Warden Mesh - Seller Dashboard</title>
<style>
  body { font-family: -apple-system, Helvetica, Arial, sans-serif; background: #0f1115; color: #e6e6e6; margin: 0; padding: 24px; }
  h1 { font-size: 20px; margin: 0 0 4px 0; }
  h1 .ans { color: #6cb6ff; font-weight: normal; }
  h2 { font-size: 15px; margin: 24px 0 8px 0; color: #aaa; text-transform: uppercase; letter-spacing: 0.05em; }
  table { width: 100%; border-collapse: collapse; }
  th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #2a2d35; font-size: 13px; }
  .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: bold; }
  .badge-OPEN { background: #1e4620; color: #6fe07a; }
  .badge-TRIPPED { background: #4a1e1e; color: #ff7a7a; }
  .badge-HALF_OPEN { background: #4a3a1e; color: #ffcf6c; }
  button { background: #23262e; color: #e6e6e6; border: 1px solid #3a3d46; border-radius: 4px; padding: 4px 10px; margin-right: 6px; cursor: pointer; font-size: 12px; }
  button:hover { background: #31353f; }
  #activity { display: flex; flex-direction: column; gap: 6px; }
  .entry { background: #1a1c22; border: 1px solid #2a2d35; border-radius: 6px; padding: 8px 12px; font-size: 13px; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
  .entry .ts { color: #888; font-size: 11px; }
  .gate { display: inline-block; padding: 1px 6px; border-radius: 4px; font-size: 11px; margin-right: 2px; }
  .gate-pass { color: #6fe07a; }
  .gate-fail { color: #ff7a7a; }
  .gate-absent { color: #555; }
  .verdict-ACCEPTED { color: #6fe07a; font-weight: bold; }
  .verdict-REJECTED { color: #ff7a7a; font-weight: bold; }
  .state-change { color: #6cb6ff; }
</style>
</head>
<body>
  <h1>Warden Mesh Seller Dashboard - <span class="ans" id="ansName"></span></h1>

  <h2>Connections</h2>
  <table id="connectionsTable">
    <thead><tr><th>ANS Name</th><th>State</th><th>Actions</th></tr></thead>
    <tbody id="connectionsBody"></tbody>
  </table>

  <h2>Activity</h2>
  <div id="activity"></div>

<script>
  const ansName = ${JSON.stringify(identity.ansName)};
  document.getElementById("ansName").textContent = ansName;

  function badgeClass(state) {
    return "badge badge-" + state;
  }

  async function loadConnections() {
    const res = await fetch("/connections");
    const connections = await res.json();
    const body = document.getElementById("connectionsBody");
    body.innerHTML = "";
    for (const conn of connections) {
      const tr = document.createElement("tr");
      const nameTd = document.createElement("td");
      nameTd.textContent = conn.ansName;
      const stateTd = document.createElement("td");
      const badge = document.createElement("span");
      badge.className = badgeClass(conn.state);
      badge.textContent = conn.state;
      stateTd.appendChild(badge);
      const actionsTd = document.createElement("td");

      const tripBtn = document.createElement("button");
      tripBtn.textContent = "Trip";
      tripBtn.onclick = () => tripConnection(conn.ansName);

      const reopenBtn = document.createElement("button");
      reopenBtn.textContent = "Reopen";
      reopenBtn.onclick = () => reopenConnection(conn.ansName);

      actionsTd.appendChild(tripBtn);
      actionsTd.appendChild(reopenBtn);

      tr.appendChild(nameTd);
      tr.appendChild(stateTd);
      tr.appendChild(actionsTd);
      body.appendChild(tr);
    }
  }

  async function tripConnection(name) {
    await fetch("/connections/" + encodeURIComponent(name) + "/trip", { method: "POST" });
    loadConnections();
  }

  async function reopenConnection(name) {
    await fetch("/connections/" + encodeURIComponent(name) + "/reopen", { method: "POST" });
    loadConnections();
  }

  function gateSpan(gates, name, label) {
    const gate = gates && gates.find((g) => g.name === name);
    if (!gate) {
      return '<span class="gate gate-absent">' + label + ': -</span>';
    }
    if (gate.result === "pass") {
      return '<span class="gate gate-pass">' + label + ': \\u2713</span>';
    }
    return '<span class="gate gate-fail">' + label + ': \\u2717</span>';
  }

  function renderEntry(event) {
    const div = document.createElement("div");
    div.className = "entry";

    if (event.type === "connection_state_change") {
      div.innerHTML =
        '<span class="ts">' + event.timestamp + '</span>' +
        '<span class="state-change">CONNECTION STATE CHANGE: ' + event.ansName + ' -> ' + event.state + '</span>';
      return div;
    }

    const verdictWord = event.overall === "accepted" ? "ACCEPTED" : "REJECTED";
    const verdictText = verdictWord === "ACCEPTED" ? verdictWord : verdictWord + " (" + event.reason + ")";

    div.innerHTML =
      '<span class="ts">' + event.timestamp + '</span>' +
      '<span>' + event.from_ans_name + '</span>' +
      '<span>' + event.message_type + '</span>' +
      gateSpan(event.gates, "signature", "Signature") +
      gateSpan(event.gates, "replay", "Replay") +
      gateSpan(event.gates, "quote_hash", "Quote-Hash") +
      gateSpan(event.gates, "connection", "Connection") +
      '<span class="verdict-' + verdictWord + '">' + verdictText + '</span>';

    return div;
  }

  function prependActivity(event) {
    const container = document.getElementById("activity");
    container.insertBefore(renderEntry(event), container.firstChild);
  }

  loadConnections();

  const source = new EventSource("/events");
  source.onmessage = (e) => {
    const event = JSON.parse(e.data);
    prependActivity(event);
    if (event.type === "connection_state_change") {
      loadConnections();
    }
  };
</script>
</body>
</html>
`;

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (chunk) => {
      data += chunk;
    });
    req.on("end", () => resolve(data));
    req.on("error", reject);
  });
}

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok", ansName: identity.ansName }));
    return;
  }

  if (req.method === "POST" && req.url === "/negotiate") {
    const body = await readBody(req);
    let message;
    try {
      message = JSON.parse(body);
    } catch {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ ok: false, reason: "invalid_json" }));
      return;
    }

    const senderPublicKeyPem = knownBuyers.get(message.from_ans_name);
    if (!senderPublicKeyPem) {
      console.log(`[SELLER] ${message.from_ans_name} -> ${message.type}: REJECTED (unknown_sender)`);
      res.writeHead(403, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ ok: false, reason: "unknown_sender" }));
      return;
    }

    const verdict = verifyIncomingMessage(message, senderPublicKeyPem, nonceStore, connectionRegistry);

    const enrichedEvent = auditLog.append({
      type: "negotiation",
      from_ans_name: message.from_ans_name,
      message_type: message.type,
      gates: verdict.gates,
      overall: verdict.ok ? "accepted" : "rejected",
      reason: verdict.reason,
    });
    events.emit("update", enrichedEvent);

    if (!verdict.ok) {
      console.log(`[SELLER] ${message.from_ans_name} -> ${message.type}: REJECTED (${verdict.reason})`);
      res.writeHead(403, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ ok: false, reason: verdict.reason }));
      return;
    }

    console.log(`[SELLER] ${message.from_ans_name} -> ${message.type}: ACCEPTED`);

    if (message.type === "offer") {
      const response = {
        ok: true,
        response: {
          type: "accept",
          item: message.payload.item,
          price: message.payload.price,
        },
      };
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(response));
      return;
    }

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ ok: true, response: null }));
    return;
  }

  if (req.method === "GET" && req.url === "/dashboard") {
    res.writeHead(200, { "Content-Type": "text/html" });
    res.end(DASHBOARD_HTML);
    return;
  }

  if (req.method === "GET" && req.url === "/connections") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify(connectionRegistry.getAll()));
    return;
  }

  if (req.method === "GET" && req.url === "/events") {
    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    });

    const onUpdate = (event) => {
      res.write(`data: ${JSON.stringify(event)}\n\n`);
    };
    events.on("update", onUpdate);

    req.on("close", () => {
      events.off("update", onUpdate);
    });
    return;
  }

  const tripMatch = req.method === "POST" && req.url.match(/^\/connections\/([^/]+)\/trip$/);
  if (tripMatch) {
    const ansName = decodeURIComponent(tripMatch[1]);
    const connection = connectionRegistry.trip(ansName, "manual_dashboard_trip", { auto: false });
    events.emit("update", {
      type: "connection_state_change",
      ansName,
      state: connection.state,
      timestamp: new Date().toISOString(),
    });
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify(connection));
    return;
  }

  const reopenMatch = req.method === "POST" && req.url.match(/^\/connections\/([^/]+)\/reopen$/);
  if (reopenMatch) {
    const ansName = decodeURIComponent(reopenMatch[1]);
    const connection = connectionRegistry.manualToggle(ansName, CONNECTION_STATES.OPEN);
    events.emit("update", {
      type: "connection_state_change",
      ansName,
      state: connection.state,
      timestamp: new Date().toISOString(),
    });
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify(connection));
    return;
  }

  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ error: "not_found" }));
});

server.listen(PORT, () => {
  console.log(`[SELLER] ${identity.ansName} listening on http://localhost:${PORT}`);
});
