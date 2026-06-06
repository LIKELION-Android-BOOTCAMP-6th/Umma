#!/usr/bin/env node

/*
 * Export a prompt-review Firestore session into a Markdown timeline.
 *
 * Auth:
 * - Reuses the locally installed Firebase CLI login by default.
 * - Set FIREBASE_TOOLS_LIB=/path/to/firebase-tools/lib if auto-detection fails.
 */

const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const FIRESTORE_ORIGIN = "https://firestore.googleapis.com";
const DEFAULT_DATABASE = "default";
const DEFAULT_PROJECT = "umma-6804c";

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const project = args.project || DEFAULT_PROJECT;
  const database = args.database || DEFAULT_DATABASE;
  const outPath = args.out || null;

  const { auth, apiv2 } = loadFirebaseTools();
  const account = auth.getGlobalDefaultAccount();
  if (!account) {
    throw new Error("Firebase CLI account not found. Run `firebase login` first.");
  }
  auth.setActiveAccount({}, account);

  const client = new apiv2.Client({
    urlPrefix: FIRESTORE_ORIGIN,
    auth: true,
  });

  if (args.reports === "true") {
    const reports = await listReports({
      client,
      project,
      database,
      limit: Number(args.limit || 50),
    });
    const markdown = renderReportsMarkdown({
      project,
      reports,
    });
    const resolvedOutPath = outPath || defaultReportsOutputPath();
    fs.mkdirSync(path.dirname(resolvedOutPath), { recursive: true });
    fs.writeFileSync(resolvedOutPath, markdown, "utf8");
    console.log(`Wrote ${resolvedOutPath}`);
    return;
  }

  const uid = required(args.uid, "--uid is required");
  const sessionId = args.session || await findLatestSessionId({
    client,
    project,
    database,
    uid,
  });
  if (!sessionId) {
    throw new Error(`No chat_prompt_reviews session found for uid=${uid}`);
  }

  const session = await getDocument({
    client,
    project,
    database,
    documentPath: `users/${uid}/chat_prompt_reviews/${sessionId}`,
  });
  const decodedSession = decodeDocument(session);
  const embeddedEvents = Array.isArray(decodedSession.events) ? decodedSession.events : [];
  const events = embeddedEvents.length > 0
    ? embeddedEvents
    : (await listDocuments({
      client,
      project,
      database,
      collectionPath: `users/${uid}/chat_prompt_reviews/${sessionId}/events`,
      pageSize: 500,
    })).map(decodeDocument);

  const markdown = renderMarkdown({
    project,
    uid,
    sessionId,
    session: decodedSession,
    events: events.sort(compareEvents),
  });

  const resolvedOutPath = outPath || defaultOutputPath(sessionId);
  fs.mkdirSync(path.dirname(resolvedOutPath), { recursive: true });
  fs.writeFileSync(resolvedOutPath, markdown, "utf8");
  console.log(`Wrote ${resolvedOutPath}`);
}

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (!arg.startsWith("--")) continue;
    const key = arg.slice(2);
    const value = argv[i + 1] && !argv[i + 1].startsWith("--")
      ? argv[++i]
      : "true";
    args[key] = value;
  }
  return args;
}

function required(value, message) {
  if (!value) throw new Error(message);
  return value;
}

function loadFirebaseTools() {
  const explicitLib = process.env.FIREBASE_TOOLS_LIB;
  if (explicitLib) {
    return {
      auth: require(path.join(explicitLib, "auth")),
      apiv2: require(path.join(explicitLib, "apiv2")),
    };
  }

  const firebaseBin = execFileSync("which", ["firebase"], {
    encoding: "utf8",
  }).trim();
  const realFirebaseBin = fs.realpathSync(firebaseBin);
  const libDir = path.resolve(path.dirname(realFirebaseBin), "..");

  return {
    auth: require(path.join(libDir, "auth")),
    apiv2: require(path.join(libDir, "apiv2")),
  };
}

async function findLatestSessionId({ client, project, database, uid }) {
  const docs = await listDocuments({
    client,
    project,
    database,
    collectionPath: `users/${uid}/chat_prompt_reviews`,
    pageSize: 100,
  });
  const sessions = docs.map(decodeDocument).sort((a, b) => {
    return (b.updatedAt || b.createdAt || 0) - (a.updatedAt || a.createdAt || 0);
  });
  return sessions[0]?.sessionId || sessions[0]?.id || null;
}

async function listReports({ client, project, database, limit }) {
  const docs = await listDocuments({
    client,
    project,
    database,
    collectionPath: "chat_prompt_review_reports",
    pageSize: Math.min(Math.max(limit, 1), 100),
  });
  return docs
    .map(decodeDocument)
    .sort((a, b) => {
      return (b.updatedAt || b.reportedAt || 0) - (a.updatedAt || a.reportedAt || 0);
    })
    .slice(0, limit);
}

async function getDocument({ client, project, database, documentPath }) {
  const pathPart = `/v1/projects/${project}/databases/${database}/documents/${documentPath}`;
  const response = await client.get(pathPart);
  return response.body;
}

async function listDocuments({ client, project, database, collectionPath, pageSize }) {
  const documents = [];
  let pageToken = null;
  do {
    const query = new URLSearchParams({ pageSize: String(pageSize) });
    if (pageToken) query.set("pageToken", pageToken);
    const pathPart = `/v1/projects/${project}/databases/${database}/documents/${collectionPath}?${query.toString()}`;
    const response = await client.get(pathPart);
    documents.push(...(response.body.documents || []));
    pageToken = response.body.nextPageToken || null;
  } while (pageToken);
  return documents;
}

function decodeDocument(document) {
  const fields = document.fields || {};
  const decoded = {};
  Object.entries(fields).forEach(([key, value]) => {
    decoded[key] = decodeValue(value);
  });
  decoded.id = document.name ? document.name.split("/").pop() : decoded.eventId;
  return decoded;
}

function decodeValue(value) {
  if (Object.prototype.hasOwnProperty.call(value, "stringValue")) return value.stringValue;
  if (Object.prototype.hasOwnProperty.call(value, "integerValue")) return Number(value.integerValue);
  if (Object.prototype.hasOwnProperty.call(value, "doubleValue")) return Number(value.doubleValue);
  if (Object.prototype.hasOwnProperty.call(value, "booleanValue")) return Boolean(value.booleanValue);
  if (Object.prototype.hasOwnProperty.call(value, "nullValue")) return null;
  if (value.arrayValue) return (value.arrayValue.values || []).map(decodeValue);
  if (value.mapValue) {
    const map = {};
    Object.entries(value.mapValue.fields || {}).forEach(([key, nested]) => {
      map[key] = decodeValue(nested);
    });
    return map;
  }
  if (Object.prototype.hasOwnProperty.call(value, "timestampValue")) return value.timestampValue;
  return value;
}

function compareEvents(a, b) {
  const timeDiff = (a.createdAt || 0) - (b.createdAt || 0);
  if (timeDiff !== 0) return timeDiff;
  return eventPriority(a.type) - eventPriority(b.type);
}

function eventPriority(type) {
  switch (type) {
    case "FinalTurn":
      return 1;
    default:
      return 9;
  }
}

function renderMarkdown({ project, uid, sessionId, session, events }) {
  const lines = [];
  const summary = buildSessionSummary({
    language: session.language || "unknown",
    events,
  });
  lines.push("# AI Chat Prompt Review");
  lines.push("");
  lines.push("## Source");
  lines.push(`- project: ${project}`);
  lines.push(`- uid: ${uid}`);
  lines.push(`- sessionId: ${sessionId}`);
  lines.push(`- language: ${session.language || "unknown"}`);
  lines.push(`- promptVersion: ${session.promptVersion || "unknown"}`);
  lines.push(`- promptBand: ${session.promptBand || "unknown"}`);
  if (session.reportNote) {
    lines.push(`- reportNote: ${session.reportNote}`);
  }
  lines.push(`- exportedAt: ${formatTime(Date.now())}`);
  lines.push(`- events: ${events.length}`);
  lines.push("");

  renderSessionSummary(lines, summary);

  if (session.sessionPromptTrace) {
    lines.push("## Session Prompt Trace");
    lines.push("```text");
    lines.push(session.sessionPromptTrace);
    lines.push("```");
    lines.push("");
  }

  lines.push("## Timeline");
  lines.push("");
  events.forEach((event, index) => {
    renderEvent(lines, event, index + 1);
  });

  return `${lines.join("\n")}\n`;
}

function renderReportsMarkdown({ project, reports }) {
  const lines = [];
  lines.push("# AI Chat Prompt Review Reports");
  lines.push("");
  lines.push("## Source");
  lines.push(`- project: ${project}`);
  lines.push(`- exportedAt: ${formatTime(Date.now())}`);
  lines.push(`- reports: ${reports.length}`);
  lines.push("");
  lines.push("## Reports");
  lines.push("");
  lines.push("| reportId | reportedAt | status | promptVersion | promptBand | uid | sessionId | language | reportNote | eventCount | reviewPath |");
  lines.push("| --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: | --- |");
  reports.forEach((report) => {
    lines.push(
      `| ${code(report.reportId || report.id || "")} | ${formatTime(report.reportedAt || report.updatedAt)} | ${report.status || ""} | ` +
        `${report.promptVersion || "unknown"} | ${report.promptBand || "unknown"} | ${code(report.uid || "")} | ${code(report.sessionId || "")} | ` +
        `${report.language || ""} | ${escapeTableCell(report.reportNote || "")} | ` +
        `${report.eventCount || 0} | ${code(report.reviewPath || "")} |`
    );
  });
  lines.push("");
  lines.push("Use `uid` and `sessionId` to export a specific reported session.");
  lines.push("");
  return `${lines.join("\n")}\n`;
}

function buildSessionSummary({ language, events }) {
  const finalTurns = events.filter((event) => event.type === "FinalTurn");
  const userFinalTurns = finalTurns.filter((event) => event.role === "USER");
  const aiFinalTurns = finalTurns.filter((event) => event.role === "AI");
  const possiblePrimaryLanguageInAiFinals = aiFinalTurns
    .filter((event) => hasPossiblePrimaryLanguageText({
      targetLanguage: language,
      text: event.text || "",
    }))
    .map((event) => ({
      turnId: event.turnId,
      preview: singleLinePreview(event.text || ""),
    }));

  return {
    userFinalTurns: userFinalTurns.length,
    aiFinalTurns: aiFinalTurns.length,
    possiblePrimaryLanguageInAiFinals,
  };
}

function renderSessionSummary(lines, summary) {
  lines.push("## Session Summary");
  lines.push(`- userFinalTurns: ${summary.userFinalTurns}`);
  lines.push(`- aiFinalTurns: ${summary.aiFinalTurns}`);
  lines.push(`- possiblePrimaryLanguageInAiFinals: ${summary.possiblePrimaryLanguageInAiFinals.length}`);
  if (summary.possiblePrimaryLanguageInAiFinals.length > 0) {
    summary.possiblePrimaryLanguageInAiFinals.forEach((item) => {
      lines.push(`  - ${item.turnId || "unknown"}: ${item.preview}`);
    });
  }
  lines.push("");
}

function renderEvent(lines, event, index) {
  const title = event.type === "FinalTurn"
    ? `${event.role || "TURN"} Final`
    : event.type || "Event";
  lines.push(`### ${index}. ${title}`);
  lines.push(`- createdAt: ${formatTime(event.createdAt)}`);
  lines.push(`- eventId: ${event.eventId || event.id || "unknown"}`);
  if (event.turnId) lines.push(`- turnId: ${event.turnId}`);
  if (event.role) lines.push(`- role: ${event.role}`);
  if (event.metadata) lines.push(`- metadata: ${event.metadata}`);
  if (event.text) {
    lines.push("");
    lines.push("Text:");
    lines.push("```text");
    lines.push(event.text);
    lines.push("```");
  }
  lines.push("");
}

function formatTime(value) {
  if (!value) return "unknown";
  const date = new Date(Number(value));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toISOString();
}

function hasPossiblePrimaryLanguageText({ targetLanguage, text }) {
  if (!text) return false;
  if (targetLanguage === "en") return /[가-힣]/.test(text);
  if (targetLanguage === "ja") return /[가-힣]/.test(text);
  return false;
}

function singleLinePreview(text) {
  return text.replace(/\s+/g, " ").trim().slice(0, 160);
}

function code(value) {
  return `\`${String(value).replace(/`/g, "\\`")}\``;
}

function escapeTableCell(value) {
  return String(value)
    .replace(/\s+/g, " ")
    .replace(/\|/g, "\\|")
    .trim();
}

function defaultOutputPath(sessionId) {
  const safeSessionId = String(sessionId).replace(/[^A-Za-z0-9_-]/g, "_");
  return path.join(
    "tools",
    "chat_prompt_review",
    "output",
    `chat_prompt_review_${safeSessionId}.md`
  );
}

function defaultReportsOutputPath() {
  return path.join(
    "tools",
    "chat_prompt_review",
    "output",
    "chat_prompt_review_reports.md"
  );
}
