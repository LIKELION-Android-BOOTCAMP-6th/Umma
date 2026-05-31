# [Engine] CHAT-ENGINE-001-B Realtime Usage Tracking

## User Story

사용자는 AI Chat을 자연스럽게 사용하고, 서비스는 대화 중 발생한 OpenAI Realtime 사용량을 가능한 한 local-first로 보존해야 한다.

이 작업은 즉시 사용량을 제한하기 위한 것이 아니라, 이후 비용 분석과 사용자 플랜 정책을 설계하기 위한 usage 기록 기반을 만든다.

---

## 완료 기준(AC)

- [ ] AC1: AI Chat에서 한 턴 이상 대화한 뒤, 개발자는 local DB 또는 Logcat에서 해당 세션의 usage record 생성을 확인할 수 있다.
- [ ] AC2: 개발자는 AI 응답 usage와 사용자 발화 transcription usage가 서로 다른 kind로 분리 기록되는 것을 확인할 수 있다.
- [ ] AC3: AI Chat 화면 이탈 또는 세션 종료 후, 개발자는 Firestore에서 세션 단위 usage 문서와 월별 aggregate 문서가 생성/갱신된 것을 확인할 수 있다.
- [ ] AC4: Firestore sync가 실패해도 local usage record는 `PENDING`으로 남고, 다음 재시도에서 sync 성공 후 `SYNCED`로 전환된다.
- [ ] AC5: usage 기록 또는 sync가 실패해도 사용자는 AI Chat 대화, final 자막 표시, final turn 저장 흐름을 계속 사용할 수 있다.
- [ ] AC6: 세션 usage 문서는 `expiresAt`을 포함하고, local DB cleanup은 원격 반영이 끝난 `SYNCED` row만 대상으로 한다.

---

## 기준 문서

- 상위 문서: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001_OpenAI_Realtime_Transport_Migration.md`
- Auth hardening: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001/CHAT-ENGINE-001-A_Token_Endpoint_Auth_Hardening.md`

---

## 범위

### 포함

- `response.done`의 token usage parsing
- `conversation.item.input_audio_transcription.completed`의 transcription usage parsing
- sessionId / turnId 기준 local-first usage 기록
- 세션 종료 또는 화면 이탈 시 Cloud Function 기반 Firestore session aggregate sync
- pending usage 재시도 경계
- Firestore 월별 aggregate 생성
- 오래된 session usage 문서 cleanup 기준 필드
- local `SYNCED` usage row cleanup
- 화면 이탈 시 ViewModel scope 취소와 무관하게 짧은 usage sync가 마무리될 수 있는 application scope 경계

### 제외

- 사용자별 사용량 제한
- 유료/무료 플랜 정책
- 정확한 원화 비용 청구 정책

---

## 저장 원칙

- Realtime event가 올 때마다 Firestore에 직접 쓰지 않는다.
- 매 turn usage는 local DB에 먼저 저장한다.
- Firestore에는 Cloud Function을 통해 세션 단위 aggregate를 올린다.
- 세션 단위 aggregate는 디버깅/상세 분석용이며, 장기 플랜 판단은 월별 aggregate를 기준으로 한다.
- 세션 단위 aggregate에는 `expiresAt`을 포함해 cleanup 대상이 될 수 있게 한다.
- 화면 이탈 시점의 sync는 UI 상태 작업이 아니므로 ViewModel 생명주기에 종속시키지 않는다.
- Android의 usage sync endpoint는 `OPENAI_USAGE_SYNC_URL`로 명시한다.
  Realtime token endpoint와 함수명이 다르므로 URL 문자열 치환에 의존하지 않는다.
- 비용 계산을 위해 text/audio, input/output breakdown을 최대한 보존한다.
- response usage와 transcription usage는 중복 가능성을 피하기 위해 분리 저장한다.
- 원화 비용은 usage 저장 시점에 바로 확정하지 않는다. 원본 token breakdown을 먼저 보존하고, 비용 산정은 별도 pricing 정책에서 계산한다.

---

## 보관/집계 정책

- local DB는 `PENDING` row를 삭제하지 않는다.
- local DB는 `SYNCED` row만 cleanup 대상으로 삼는다.
- local `SYNCED` usage row 기본 보관 기간은 30일이다.
- local usage row가 10,000개를 넘으면 오래된 `SYNCED` row부터 추가 삭제한다.
- Firestore session usage 문서는 90일 보관을 기준으로 `expiresAt`을 기록한다.
- Cloud Function은 session usage 문서 저장과 KST 기준 월별 aggregate 갱신을 한 번에 처리한다.
- Cloud Function scheduled cleanup은 `expiresAt`이 지난 session usage 문서를 삭제한다.
- 플랜 제한 적용은 이번 범위에 포함하지 않는다. 다만 이후 `realtimeToken` 발급 전에 월별 aggregate를 조회할 수 있는 기반을 만든다.

---

## 기록 후보 필드

```text
uid
sessionId
turnId
language
model
transcriptionModel
createdAt

responseTotalTokens
responseInputTokens
responseOutputTokens
responseInputTextTokens
responseInputAudioTokens
responseCachedTokens
responseOutputTextTokens
responseOutputAudioTokens

transcriptionTotalTokens
transcriptionInputAudioTokens
transcriptionOutputTextTokens

pricingVersion
syncStatus
syncedAt
```

`pricingVersion`은 실제 비용을 바로 계산하기 위한 필드가 아니라, 이후 비용 산정 정책이 바뀌었을 때 어떤 가격표 기준으로 재계산했는지 구분하기 위한 후보 필드다.

---

## 테스트 방법

- AI Chat 한 턴 이상 진행 후 local usage record가 생성되는지 확인한다.
  - Logcat: `ChatViewModel` / `CHAT-ENGINE-001-B usage_recorded`
  - local table: `umma_chat_usage_db.chat_usage_records`
- `usage_recorded` 로그 또는 local table에서 `RESPONSE`와 `TRANSCRIPTION`이 분리 기록되는지 확인한다.
- AI Chat 화면 이탈 또는 세션 종료 시 Cloud Function을 통해 Firestore session aggregate가 sync되는지 확인한다.
  - Logcat: `ChatViewModel` / `CHAT-ENGINE-001-B usage_session_synced`
  - Firestore path: `users/{uid}/chat_usage_sessions/{sessionId}`
- Firestore session aggregate 생성 후 월별 aggregate가 함께 갱신되는지 확인한다.
  - monthly path: `users/{uid}/chat_usage_monthly/{yyyyMM}`
- sync 실패 이력이 있는 상태에서 Chat 화면 재진입 시 pending usage가 재시도되는지 확인한다.
  - Logcat: `ChatViewModel` / `CHAT-ENGINE-001-B usage_pending_synced`
- sync 실패 시에는 `chat usage session sync pending` 로그가 남고, local row가 `PENDING`으로 유지되는지 확인한다.
- 화면 이탈 직후 `Job was cancelled`가 아니라 `usage_session_synced`가 남는지 확인한다.
- local cleanup은 `CHAT-ENGINE-001-B usage_local_cleanup` 로그 또는 local DB row 수로 확인한다.
  - 검증 예시: 30일 이전 `SYNCED` row 1건을 만든 뒤 Chat 진입 시 `usage_local_cleanup deletedCount=1`, row 수 감소 확인.
- session usage cleanup은 `cleanupExpiredChatUsageSessions` 함수 로그와 `expiresAt` 지난 session 문서 삭제 여부로 확인한다.
  - 검증 예시: `expiresAt`이 지난 session 문서 1건을 만든 뒤 Cloud Scheduler 강제 실행 시 `deletedCount=1` 확인.
- usage 기록 실패가 대화 UI, final transcript 저장, correctionAvailable 신호를 막지 않는지 확인한다.
