import fs from "node:fs";
import path from "node:path";

export class AuditLog {
  constructor(filePath = "logs/audit.log") {
    this.filePath = filePath;
    this.seq = 0;
  }

  append(event) {
    const enriched = {
      ...event,
      timestamp: event.timestamp ?? new Date().toISOString(),
      seq: this.seq++,
    };

    const dir = path.dirname(this.filePath);
    fs.mkdirSync(dir, { recursive: true });
    fs.appendFileSync(this.filePath, `${JSON.stringify(enriched)}\n`);

    return enriched;
  }

  readAll() {
    if (!fs.existsSync(this.filePath)) {
      return [];
    }
    const content = fs.readFileSync(this.filePath, "utf8");
    return content
      .split("\n")
      .filter((line) => line.trim().length > 0)
      .map((line) => JSON.parse(line));
  }
}

export default AuditLog;
