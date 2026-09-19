import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { EventEmitter } from "node:events";
import { fileURLToPath } from "node:url";
import { verifyIncomingMessage, NonceStore } from "../../worker/negotiate.js";
import { ConnectionRegistry, CONNECTION_STATES } from "../../supervisor/connectionRegistry.js";
import { AuditLog } from "../../supervisor/auditLog.js";
import { hashPayload, signPayload, generateNonce } from "../../shared/crypto.js";
import { decideNegotiation } from "../../shared/llmClient.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const PORT = 4001;

// Hardcoded seller preferences for now — a config step later can make these adjustable.
const SELLER_TARGET_PRICE = 60;
const SELLER_FLOOR_PRICE = 35;

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
<script src="https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.28.1/cytoscape.min.js"></script>
<style>
  html, body { height: 100%; margin: 0; }
  body { font-family: -apple-system, Helvetica, Arial, sans-serif; background: #0f1115; color: #e6e6e6; display: flex; flex-direction: column; overflow: hidden; }
  header { flex: 0 0 auto; padding: 14px 20px; border-bottom: 1px solid #2a2d35; }
  h1 { font-size: 20px; margin: 0; }
  h1 .ans { color: #6cb6ff; font-weight: normal; }
  .layout { flex: 1 1 auto; display: flex; min-height: 0; }
  #graphPane { flex: 0 0 75%; position: relative; border-right: 1px solid #2a2d35; }
  #cy { width: 100%; height: 100%; }
  #nodeTooltip { position: absolute; display: none; background: #1a1c22; border: 1px solid #3a3d46; border-radius: 4px; padding: 4px 8px; font-size: 11px; pointer-events: none; z-index: 10; white-space: nowrap; }
  #sidebar { flex: 0 0 25%; display: flex; flex-direction: column; padding: 16px; box-sizing: border-box; min-width: 0; overflow: hidden; }
  .sidebar-h { font-size: 15px; margin: 0 0 8px 0; color: #aaa; text-transform: uppercase; letter-spacing: 0.05em; display: flex; align-items: center; justify-content: space-between; }
  .sidebar-h .toggle { cursor: pointer; color: #6cb6ff; font-size: 11px; text-transform: none; letter-spacing: 0; }
  .sidebar-panel { background: #1a1c22; border: 1px solid #2a2d35; border-radius: 6px; padding: 10px 12px; box-sizing: border-box; }
  #detailPanel { flex: 0 0 auto; margin-bottom: 16px; }
  .detail-empty { color: #888; font-size: 13px; }
  .detail-name { font-weight: bold; margin-bottom: 8px; font-size: 13px; word-break: break-all; }
  .detail-row { font-size: 12px; color: #ccc; margin: 4px 0; }
  .detail-actions { margin-top: 10px; }
  #activitySection { flex: 1 1 auto; display: flex; flex-direction: column; min-height: 0; }
  #activityList { flex: 1 1 auto; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; margin-top: 8px; }
  #activitySection.collapsed #activityList { display: none; }
  .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: bold; }
  .badge-OPEN { background: #1e4620; color: #6fe07a; }
  .badge-TRIPPED { background: #4a1e1e; color: #ff7a7a; }
  .badge-HALF_OPEN { background: #4a3a1e; color: #ffcf6c; }
  button { background: #23262e; color: #e6e6e6; border: 1px solid #3a3d46; border-radius: 4px; padding: 4px 10px; margin-right: 6px; cursor: pointer; font-size: 12px; }
  button:hover { background: #31353f; }
  .entry { background: #1a1c22; border: 1px solid #2a2d35; border-radius: 6px; padding: 8px 10px; font-size: 12px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
  .entry .ts { color: #888; font-size: 10px; }
  .gate { display: inline-block; padding: 1px 6px; border-radius: 4px; font-size: 10px; margin-right: 2px; }
  .gate-pass { color: #6fe07a; }
  .gate-fail { color: #ff7a7a; }
  .gate-absent { color: #555; }
  .verdict-ACCEPTED { color: #6fe07a; font-weight: bold; }
  .verdict-REJECTED { color: #ff7a7a; font-weight: bold; }
  .state-change { color: #6cb6ff; }
  .reasoning { font-style: italic; color: #999; font-size: 11px; flex-basis: 100%; }
</style>
</head>
<body>
  <header>
    <h1>Warden Mesh Seller Dashboard - <span class="ans" id="ansNameLabel"></span></h1>
  </header>

  <div class="layout">
    <div id="graphPane">
      <div id="cy"></div>
      <div id="nodeTooltip"></div>
    </div>

    <div id="sidebar">
      <div class="sidebar-h">Selected connection</div>
      <div id="detailPanel" class="sidebar-panel">
        <div class="detail-empty">click a node to see details</div>
      </div>

      <div id="activitySection">
        <div class="sidebar-h">
          Activity
          <span class="toggle" id="activityToggle">hide</span>
        </div>
        <div id="activityList"></div>
      </div>
    </div>
  </div>

<script>
  const ansName = ${JSON.stringify(identity.ansName)};
  document.getElementById("ansNameLabel").textContent = ansName;

  let selectedAnsName = null;

  function badgeClass(state) {
    return "badge badge-" + state;
  }

  function truncateLabel(name) {
    const short = name.replace("ans://", "");
    return short.length > 16 ? short.slice(0, 14) + "\\u2026" : short;
  }

  function nodeDataFor(conn) {
    return {
      id: conn.ansName,
      label: truncateLabel(conn.ansName),
      fullName: conn.ansName,
      state: conn.state,
      violationCount: conn.violationCount,
      trippedReason: conn.trippedReason,
      type: "connection",
    };
  }

  const cy = cytoscape({
    container: document.getElementById("cy"),
    elements: [{ data: { id: "self", label: ansName, fullName: ansName, type: "self" } }],
    style: [
      {
        selector: "node",
        style: {
          label: "data(label)",
          color: "#e6e6e6",
          "font-size": 10,
          "text-valign": "center",
          "text-halign": "center",
          "text-wrap": "ellipsis",
          "text-max-width": "70px",
          width: 46,
          height: 46,
          "border-width": 3,
          "background-color": "#23262e",
          "border-color": "#3a3d46",
        },
      },
      {
        selector: 'node[type = "self"]',
        style: {
          shape: "hexagon",
          "background-color": "#20304f",
          "border-color": "#6cb6ff",
          width: 66,
          height: 66,
          "font-weight": "bold",
        },
      },
      {
        selector: 'node[state = "OPEN"]',
        style: { "background-color": "#1e4620", "border-color": "#6fe07a" },
      },
      {
        selector: 'node[state = "TRIPPED"]',
        style: { "background-color": "#4a1e1e", "border-color": "#ff7a7a" },
      },
      {
        selector: 'node[state = "HALF_OPEN"]',
        style: { "background-color": "#1e4620", "border-color": "#ffcf6c" },
      },
      {
        selector: "edge",
        style: { width: 2, "line-color": "#3a3d46", "curve-style": "bezier", "target-arrow-shape": "none" },
      },
    ],
    layout: { name: "breadthfirst", roots: "#self", animate: false },
    minZoom: 0.3,
    maxZoom: 2.5,
    wheelSensitivity: 0.2,
  });

  function runLayout() {
    cy.layout({ name: "breadthfirst", roots: "#self", animate: true, animationDuration: 300 }).run();
  }

  const tooltip = document.getElementById("nodeTooltip");
  cy.on("mouseover", "node", (evt) => {
    tooltip.textContent = evt.target.data("fullName") || evt.target.data("label");
    tooltip.style.display = "block";
  });
  cy.on("mousemove", "node", (evt) => {
    const pos = evt.renderedPosition || evt.position;
    tooltip.style.left = pos.x + 14 + "px";
    tooltip.style.top = pos.y + 14 + "px";
  });
  cy.on("mouseout", "node", () => {
    tooltip.style.display = "none";
  });

  cy.on("tap", "node", (evt) => {
    const node = evt.target;
    if (node.data("type") === "self") {
      selectedAnsName = null;
      document.getElementById("detailPanel").innerHTML =
        '<div class="detail-empty">This is your agent (' + ansName + ').</div>';
      return;
    }
    selectedAnsName = node.id();
    renderDetailPanel(node.data());
  });

  function renderDetailPanel(data) {
    const panel = document.getElementById("detailPanel");
    panel.innerHTML =
      '<div class="detail-name">' + data.fullName + "</div>" +
      '<div><span class="' + badgeClass(data.state) + '">' + data.state + "</span></div>" +
      '<div class="detail-row">Violation count: ' + data.violationCount + "</div>" +
      '<div class="detail-row">Tripped reason: ' + (data.trippedReason || "-") + "</div>" +
      '<div class="detail-actions">' +
      '<button id="detailTripBtn">Trip</button>' +
      '<button id="detailReopenBtn">Reopen</button>' +
      "</div>";
    document.getElementById("detailTripBtn").onclick = () => tripConnection(data.fullName);
    document.getElementById("detailReopenBtn").onclick = () => reopenConnection(data.fullName);
  }

  async function tripConnection(name) {
    await fetch("/connections/" + encodeURIComponent(name) + "/trip", { method: "POST" });
    loadConnections();
  }

  async function reopenConnection(name) {
    await fetch("/connections/" + encodeURIComponent(name) + "/reopen", { method: "POST" });
    loadConnections();
  }

  function syncGraph(connections) {
    for (const conn of connections) {
      let node = cy.getElementById(conn.ansName);
      if (node.empty()) {
        cy.add([
          { data: nodeDataFor(conn) },
          { data: { id: "edge-" + conn.ansName, source: "self", target: conn.ansName } },
        ]);
        runLayout();
        node = cy.getElementById(conn.ansName);
      } else {
        node.data("state", conn.state);
        node.data("violationCount", conn.violationCount);
        node.data("trippedReason", conn.trippedReason);
      }
      if (selectedAnsName === conn.ansName) {
        renderDetailPanel(node.data());
      }
    }
  }

  async function loadConnections() {
    const res = await fetch("/connections");
    const connections = await res.json();
    syncGraph(connections);
    return connections;
  }

  function flashEdge(fromAnsName) {
    const edge = cy.getElementById("edge-" + fromAnsName);
    if (edge.empty()) return;
    edge.animate(
      { style: { "line-color": "#6cb6ff", width: 6 } },
      {
        duration: 300,
        complete: () => {
          edge.animate({ style: { "line-color": "#3a3d46", width: 2 } }, { duration: 300 });
        },
      }
    );
  }

  async function handleNegotiationEvent(event) {
    const fromAnsName = event.from_ans_name;
    const node = cy.getElementById(fromAnsName);
    if (node.empty()) {
      cy.add([
        {
          data: {
            id: fromAnsName,
            label: truncateLabel(fromAnsName),
            fullName: fromAnsName,
            state: "OPEN",
            violationCount: 0,
            trippedReason: null,
            type: "connection",
          },
        },
        { data: { id: "edge-" + fromAnsName, source: "self", target: fromAnsName } },
      ]);
      runLayout();
    }
    flashEdge(fromAnsName);
    await loadConnections();
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
      '<span class="verdict-' + verdictWord + '">' + verdictText + '</span>' +
      (event.decision_reasoning ? '<div class="reasoning">' + event.decision_reasoning + '</div>' : '');

    return div;
  }

  function prependActivity(event) {
    const container = document.getElementById("activityList");
    container.insertBefore(renderEntry(event), container.firstChild);
  }

  document.getElementById("activityToggle").onclick = () => {
    const section = document.getElementById("activitySection");
    section.classList.toggle("collapsed");
    document.getElementById("activityToggle").textContent = section.classList.contains("collapsed") ? "show" : "hide";
  };

  loadConnections();

  const source = new EventSource("/events");
  source.onmessage = (e) => {
    const event = JSON.parse(e.data);
    prependActivity(event);
    if (event.type === "connection_state_change") {
      loadConnections();
    } else {
      handleNegotiationEvent(event);
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
  if (req.method === "GET" && req.url === "/") {
    res.writeHead(302, { Location: "/dashboard" });
    res.end();
    return;
  }

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

    // The LLM is never involved in gate verification above — it only picks a
    // negotiation move once the message has already passed all four gates.
    let decision = null;
    if (verdict.ok && (message.type === "offer" || message.type === "counter")) {
      decision = await decideNegotiation({
        role: "seller",
        item: message.payload.item,
        incomingOffer: message.payload.price,
        targetPrice: SELLER_TARGET_PRICE,
        floorOrCeiling: SELLER_FLOOR_PRICE,
      });
    }

    const enrichedEvent = auditLog.append({
      type: "negotiation",
      from_ans_name: message.from_ans_name,
      message_type: message.type,
      gates: verdict.gates,
      overall: verdict.ok ? "accepted" : "rejected",
      reason: verdict.reason,
      decision_reasoning: decision ? decision.reasoning : undefined,
    });
    events.emit("update", enrichedEvent);

    if (!verdict.ok) {
      console.log(`[SELLER] ${message.from_ans_name} -> ${message.type}: REJECTED (${verdict.reason})`);
      res.writeHead(403, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ ok: false, reason: verdict.reason }));
      return;
    }

    console.log(
      `[SELLER] ${message.from_ans_name} -> ${message.type}: ACCEPTED (gates: ` +
        `${verdict.gates.map((g) => `${g.name}=${g.result}`).join(", ")})`
    );

    if (decision) {
      const responsePayload = {
        item: message.payload.item,
        price:
          decision.action === "counter"
            ? decision.price
            : decision.action === "accept"
              ? message.payload.price
              : null,
      };
      const responseMessage = {
        type: decision.action,
        from_ans_name: identity.ansName,
        to_ans_name: message.from_ans_name,
        nonce: generateNonce(),
        timestamp: new Date().toISOString(),
        payload: responsePayload,
        payload_hash: hashPayload(responsePayload),
        signature: signPayload(responsePayload, identity.privateKey),
      };

      console.log(
        `[SELLER] Decision: ${decision.action}` +
          `${responsePayload.price != null ? " @ " + responsePayload.price : ""} — ${decision.reasoning}`
      );

      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ ok: true, response: responseMessage }));
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
