#!/usr/bin/env node

/*
 * Export a Correction prompt-review Firestore document into Markdown.
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
    const markdown = renderReportsMarkdown({ project, reports });
    const resolvedOutPath = outPath || defaultReportsOutputPath();
    fs.mkdirSync(path.dirname(resolvedOutPath), { recursive: true });
    fs.writeFileSync(resolvedOutPath, markdown, "utf8");
    console.log(`Wrote ${resolvedOutPath}`);
    return;
  }

  const uid = required(args.uid, "--uid is required");
  const reviewId = args.review || await resolveReviewDocumentId({
    client,
    project,
    database,
    uid,
    reportId: args.report || null,
  });
  if (!reviewId) {
    throw new Error(`No correction_prompt_reviews document found for uid=${uid}`);
  }

  const review = decodeDocument(await getDocument({
    client,
    project,
    database,
    documentPath: `users/${uid}/correction_prompt_reviews/${reviewId}`,
  }));
  const markdown = renderMarkdown({
    project,
    uid,
    reviewId,
    review,
  });

  const resolvedOutPath = outPath || defaultOutputPath(reviewId);
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

  const command = process.platform === "win32" ? "where" : "which";
  const firebaseBin = execFileSync(command, ["firebase"], { encoding: "utf8" })
    .split(/\r?\n/)
    .filter(Boolean)[0];
  const realFirebaseBin = fs.realpathSync(firebaseBin);
  const libDir = path.resolve(path.dirname(realFirebaseBin), "..");

  return {
    auth: require(path.join(libDir, "auth")),
    apiv2: require(path.join(libDir, "apiv2")),
  };
}

async function resolveReviewDocumentId({ client, project, database, uid, reportId }) {
  if (reportId) {
    return await findReviewDocumentIdByReportId({ client, project, database, reportId }) || reportId;
  }
  return findLatestReviewDocumentId({ client, project, database, uid });
}

async function findReviewDocumentIdByReportId({ client, project, database, reportId }) {
  const report = await getDocument({
    client,
    project,
    database,
    documentPath: `correction_prompt_review_reports/${reportId}`,
  });
  return reviewIdFromReport(decodeDocument(report));
}

async function findLatestReviewDocumentId({ client, project, database, uid }) {
  const docs = await listDocuments({
    client,
    project,
    database,
    collectionPath: `users/${uid}/correction_prompt_reviews`,
    pageSize: 100,
  });
  const reviews = docs.map(decodeDocument).sort((a, b) => {
    return (b.updatedAt || b.reportedAt || 0) - (a.updatedAt || a.reportedAt || 0);
  });
  return reviews[0]?.id || reviews[0]?.reviewId || reviews[0]?.reportId || null;
}

async function listReports({ client, project, database, limit }) {
  const docs = await listDocuments({
    client,
    project,
    database,
    collectionPath: "correction_prompt_review_reports",
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
  decoded.id = document.name ? document.name.split("/").pop() : decoded.reportId;
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

function renderMarkdown({ project, uid, reviewId, review }) {
  const lines = [];
  lines.push("# Correction Prompt Review");
  lines.push("");
  lines.push("## Source");
  lines.push(`- project: ${project}`);
  lines.push(`- uid: ${uid}`);
  lines.push(`- reviewId: ${reviewId}`);
  lines.push(`- reportId: ${review.reportId || reviewId}`);
  lines.push(`- language: ${review.language || "unknown"}`);
  lines.push(`- primaryLanguage: ${review.primaryLanguage || "unknown"}`);
  lines.push(`- phase: ${review.phase || "unknown"}`);
  lines.push(`- reportedAt: ${formatTime(review.reportedAt)}`);
  lines.push(`- suggestions: ${review.suggestionCount || (review.suggestions || []).length || 0}`);
  lines.push(`- selectedSuggestionIds: ${(review.selectedSuggestionIds || []).join(", ") || "none"}`);
  if (review.reportNote) lines.push(`- reportNote: ${review.reportNote}`);
  if (review.errorReason) lines.push(`- errorReason: ${review.errorReason}`);
  if (review.saveErrorReason) lines.push(`- saveErrorReason: ${review.saveErrorReason}`);
  if (review.completionErrorReason) lines.push(`- completionErrorReason: ${review.completionErrorReason}`);
  lines.push(`- exportedAt: ${formatTime(Date.now())}`);
  lines.push("");

  lines.push("## Context Turns");
  lines.push("");
  (review.contextTurns || []).forEach((turn, index) => {
    lines.push(`### ${index + 1}. ${turn.speaker || "unknown"}`);
    lines.push("```text");
    lines.push(turn.text || "");
    lines.push("```");
    lines.push("");
  });
  if (!review.contextTurns || review.contextTurns.length === 0) {
    lines.push("_No context turns captured._");
    lines.push("");
  }

  lines.push("## Suggestions");
  lines.push("");
  (review.suggestions || []).forEach((suggestion, index) => {
    lines.push(`### ${index + 1}. ${suggestion.id || "unknown"}`);
    lines.push(`- sourceTurnIndex: ${suggestion.sourceTurnIndex}`);
    lines.push(`- sourceCandidateIds: ${(suggestion.sourceCandidateIds || []).join(", ")}`);
    if (suggestion.sourceLang) lines.push(`- sourceLang: ${suggestion.sourceLang}`);
    renderTextBlock(lines, "Before", suggestion.beforeText);
    renderTextBlock(lines, "Native", suggestion.nativeText);
    renderTextBlock(lines, "After", suggestion.afterText);
    renderTextBlock(lines, "Explanation", suggestion.explanation);
  });
  if (!review.suggestions || review.suggestions.length === 0) {
    lines.push("_No suggestions captured._");
    lines.push("");
  }

  return `${lines.join("\n")}\n`;
}

function renderTextBlock(lines, title, text) {
  lines.push("");
  lines.push(`${title}:`);
  lines.push("```text");
  lines.push(text || "");
  lines.push("```");
  lines.push("");
}

function renderReportsMarkdown({ project, reports }) {
  const lines = [];
  lines.push("# Correction Prompt Review Reports");
  lines.push("");
  lines.push("## Source");
  lines.push(`- project: ${project}`);
  lines.push(`- exportedAt: ${formatTime(Date.now())}`);
  lines.push(`- reports: ${reports.length}`);
  lines.push("");
  lines.push("## Reports");
  lines.push("");
  lines.push("| reportId | reportedAt | phase | language | primaryLanguage | uid | reviewId | suggestions | selected | reportNote | reviewPath |");
  lines.push("| --- | --- | --- | --- | --- | --- | --- | ---: | ---: | --- | --- |");
  reports.forEach((report) => {
    const reviewId = reviewIdFromReport(report);
    lines.push(
      `| ${code(report.reportId || report.id || "")} | ${formatTime(report.reportedAt || report.updatedAt)} | ${report.phase || ""} | ` +
        `${report.language || ""} | ${report.primaryLanguage || ""} | ${code(report.uid || "")} | ${code(reviewId || "")} | ` +
        `${report.suggestionCount || 0} | ${report.selectedCount || 0} | ${escapeTableCell(report.reportNote || "")} | ${code(report.reviewPath || "")} |`
    );
  });
  lines.push("");
  lines.push("Use `uid` with `--report REPORT_ID` for the exact review document.");
  lines.push("");
  return `${lines.join("\n")}\n`;
}

function reviewIdFromReport(report) {
  if (!report) return null;
  if (report.reviewId) return report.reviewId;
  if (report.reviewPath) return String(report.reviewPath).split("/").filter(Boolean).pop();
  return report.reportId || report.id || null;
}

function formatTime(value) {
  if (!value) return "unknown";
  const date = new Date(Number(value));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toISOString();
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

function defaultOutputPath(reviewId) {
  const safeReviewId = String(reviewId).replace(/[^A-Za-z0-9_-]/g, "_");
  return path.join(
    "tools",
    "correction_prompt_review",
    "output",
    `correction_prompt_review_${safeReviewId}.md`
  );
}

function defaultReportsOutputPath() {
  return path.join(
    "tools",
    "correction_prompt_review",
    "output",
    "correction_prompt_review_reports.md"
  );
}
