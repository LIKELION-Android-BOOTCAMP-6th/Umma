# Correction Prompt Review Tool

Development tool for exporting Correction review reports from Firestore into Markdown.

Review document:

```text
users/{uid}/correction_prompt_reviews/{reportId}
```

Report index:

```text
correction_prompt_review_reports/{reportId}
```

List recent reports:

```bash
node tools/correction_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --reports \
  --limit 20
```

Export one report:

```bash
node tools/correction_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --uid USER_UID \
  --report REPORT_ID
```

Outputs are written under:

```text
tools/correction_prompt_review/output/
```
