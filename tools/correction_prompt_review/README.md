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

신고 시 `userId`(소유자 검증용)와 `promptBand`(교정 성장 band — 어느 정책이 적용됐는지 추적)가 함께 저장된다.

## Firestore Rules 배포

`correction_prompt_review_reports` 최상위 컬렉션 규칙을 배포해야 신고 인덱스 저장이 허용된다:

```bash
firebase deploy --only firestore:rules
```

규칙을 배포하지 않으면 최상위 인덱스 저장 시 `PERMISSION_DENIED` 로 거부된다.

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
