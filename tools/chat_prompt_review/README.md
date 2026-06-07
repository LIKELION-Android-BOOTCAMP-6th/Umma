# AI Chat Prompt Review Tool

개발용 프롬프트 리뷰 자료를 Firestore에서 읽어 세션 단위 Markdown 문서로 내보내는 도구입니다.

## 앱 저장 스위치

현재 프롬프트 튜닝 단계에서는 debug build 기본값이 켜져 있습니다.
따라서 별도 설정 없이 `devDebug`에서 채팅 화면 상단에 개발용 `신고` 버튼이 표시됩니다.
`mockDebug`는 fake transport를 사용하는 화면 검증용이므로 신고 버튼을 표시하지 않습니다.

로컬에서 끄고 싶으면 `local.properties`에 아래 값을 추가합니다.

```properties
CHAT_PROMPT_REVIEW_ENABLED=false
```

release build에서는 코드에서 `BuildConfig.DEBUG`로 한 번 더 막기 때문에 저장되지 않습니다.

세션 리뷰 저장 위치:

```text
users/{uid}/chat_prompt_reviews/{reportId}
```

신고 인덱스 저장 위치:

```text
chat_prompt_review_reports/{reportId}
```

`reportId`는 Firestore 콘솔에서 바로 찾기 쉽도록 KST 24시간 기준으로 생성합니다.

```text
yyyyMMdd_HHmmss_{language}_{sessionPrefix}
```

예:

```text
20260606_185432_en_b0db18c9
```

이 컬렉션은 개발용입니다. `SessionMemory`, `usage`, `Correction` 저장 모델과 분리되어 있습니다.
앱은 turn마다 Firestore에 쓰지 않고 메모리에만 모읍니다.
팀원이 `신고` 버튼을 누른 세션만 Firestore에 저장하며, 신고하지 않은 세션은 원격에 남기지 않습니다.
신고 시 입력한 불편 상황 메모, `promptVersion`, `promptRevision`, `promptBand`가 함께 저장되어 테스트 브랜치/프롬프트 버전과 적용 band별로 신고를 구분할 수 있습니다.
`promptVersion`은 큰 구조 버전이고, `promptRevision`은 같은 구조 안에서 반복되는 미세 튜닝 식별자입니다.

신고 저장이 성공하면 Logcat에 아래 태그로 export 식별 정보가 남습니다.

```text
tag:AiChatPromptReview
```

예시:

```text
sessionFlushed uid=USER_UID sessionId=SESSION_ID eventCount=12 status=reported promptBand=SimpleSentence firestorePath=users/USER_UID/chat_prompt_reviews/REPORT_ID reportPath=chat_prompt_review_reports/REPORT_ID
```

이제 팀원이 로그를 복사하지 않아도 Firestore의 `chat_prompt_review_reports`에서 신고된 세션 목록을 확인할 수 있습니다.

## 실행

Firebase CLI 로그인이 필요합니다.

```bash
firebase login
```

특정 세션을 문서화합니다.

신고 목록의 `reportId`를 알고 있으면 `--report`를 사용하는 것이 가장 정확합니다.
도구가 신고 인덱스의 `reviewPath`를 읽어 실제 리뷰 문서 위치를 찾습니다.

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --uid USER_UID \
  --report REPORT_ID
```

기존 로그나 오래된 문서처럼 세션 ID만 알고 있어도 호환 조회할 수 있습니다.

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --uid USER_UID \
  --session SESSION_ID
```

신고된 세션 목록을 문서화합니다.

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --reports
```

최근 N개 신고만 확인할 수도 있습니다.

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --reports \
  --limit 20
```

`--report`와 `--session`을 모두 생략하면 `updatedAt` 기준 최신 리뷰 문서를 사용합니다.

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --uid USER_UID
```

출력 경로를 지정할 수 있습니다.

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --uid USER_UID \
  --report REPORT_ID \
  --out tools/chat_prompt_review/output/latest.md
```

## 산출물

```text
tools/chat_prompt_review/output/chat_prompt_review_{reportId}.md
```

문서에는 세션 prompt trace와 USER/AI final transcript가 시간순으로 정렬됩니다.
품질 판단은 자동화하지 않고, 이 문서를 기준으로 수동 리뷰합니다.
문서 상단의 `Session Summary`에는 여러 세션 비교에 필요한 카운트와 분포만 자동 요약합니다.

자동 요약 항목:

- USER/AI final turn 수
- target language 대화 중 primary language가 섞인 의심 AI final 수
- promptVersion
- promptRevision
- 신고 메모

수동 분석 결과와 열린 개선 항목은 아래 문서에 남깁니다.

```text
tools/chat_prompt_review/output/REVIEW_NOTES.md
```

`chat_prompt_review_*.md`, `chat_prompt_review_reports.md`, `REVIEW_NOTES.md` 산출물은 Git에 올리지 않습니다.
