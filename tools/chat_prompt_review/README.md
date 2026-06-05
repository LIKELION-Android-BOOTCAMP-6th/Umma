# AI Chat Prompt Review Tool

개발용 프롬프트 리뷰 자료를 Firestore에서 읽어 세션 단위 Markdown 문서로 내보내는 도구입니다.

## 앱 저장 스위치

현재 프롬프트 튜닝 단계에서는 debug build 기본값이 켜져 있습니다.
따라서 별도 설정 없이 `devDebug` / `mockDebug`에서 리뷰 데이터가 저장됩니다.

로컬에서 끄고 싶으면 `local.properties`에 아래 값을 추가합니다.

```properties
CHAT_PROMPT_REVIEW_ENABLED=false
```

release build에서는 코드에서 `BuildConfig.DEBUG`로 한 번 더 막기 때문에 저장되지 않습니다.

저장 위치:

```text
users/{uid}/chat_prompt_reviews/{sessionId}
```

이 컬렉션은 개발용입니다. `SessionMemory`, `usage`, `Correction` 저장 모델과 분리되어 있습니다.
앱은 turn마다 Firestore에 쓰지 않고 메모리에 모았다가 세션 종료 시 세션 문서 하나에 `events` 배열로 한 번만 저장합니다.

세션 저장이 성공하면 Logcat에 아래 태그로 export 식별 정보가 남습니다.

```text
tag:AiChatPromptReview
```

예시:

```text
sessionFlushed uid=USER_UID sessionId=SESSION_ID eventCount=12 firestorePath=users/USER_UID/chat_prompt_reviews/SESSION_ID
```

이 로그의 `uid`와 `sessionId`를 사용하면 방금 종료한 세션을 정확히 문서화할 수 있습니다.

## 실행

Firebase CLI 로그인이 필요합니다.

```bash
firebase login
```

특정 세션을 문서화합니다.

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --uid USER_UID \
  --session SESSION_ID
```

`--session`을 생략하면 `updatedAt` 기준 최신 리뷰 세션을 사용합니다.

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
  --session SESSION_ID \
  --out tools/chat_prompt_review/output/latest.md
```

## 산출물

```text
tools/chat_prompt_review/output/chat_prompt_review_{sessionId}.md
```

문서에는 세션 prompt trace, USER/AI final transcript, turn override trace가 시간순으로 정렬됩니다.
품질 판단은 자동화하지 않고, 이 문서를 기준으로 수동 리뷰합니다.
문서 상단의 `Session Summary`에는 여러 세션 비교에 필요한 카운트와 분포만 자동 요약합니다.

자동 요약 항목:

- USER/AI final turn 수
- turn override 수
- `hasInstructions` true/false 횟수
- `contextSignal` 분포
- `primaryBridge` 정책/사유 분포
- `outputAudioSpeed` 값
- target language 대화 중 primary language가 섞인 의심 AI final 수

수동 분석 결과와 열린 개선 항목은 아래 문서에 남깁니다.

```text
tools/chat_prompt_review/output/REVIEW_NOTES.md
```

`chat_prompt_review_*.md` 산출물은 Git에 올리지 않고, `REVIEW_NOTES.md`만 추적합니다.
