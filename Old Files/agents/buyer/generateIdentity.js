import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { generateKeyPair } from "../../shared/crypto.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const { publicKey, privateKey } = generateKeyPair();
const identity = {
  ansName: "ans://buyer-local.test",
  publicKey,
  privateKey,
};

const outPath = path.join(__dirname, "identity.json");
fs.writeFileSync(outPath, JSON.stringify(identity, null, 2));
console.log(`Wrote buyer identity to ${outPath}`);
