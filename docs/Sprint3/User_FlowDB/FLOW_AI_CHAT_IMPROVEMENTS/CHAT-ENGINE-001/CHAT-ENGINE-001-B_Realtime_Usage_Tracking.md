# [Engine] CHAT-ENGINE-001-B Realtime Usage Tracking

## User Story

사용자는 AI Chat을 자연스럽게 사용하고, 서비스는 대화 중 발생한 OpenAI Realtime 사용량을 가능한 한 local-first로 보존해야 한다.

이 작업은 즉시 사용량을 제한하기 위한 것이 아니라, 이후 비용 분석과 사용자 플랜 정책을 설계하기 위한 usage 기록 기반을 만든다.

---

## 완료 기준(AC)

- [ ] AI Chat의 `response.done` usage를 수집한다.
- [ ] 사용자 발화 transcription usage를 수집한다.
- [ ] response usage와 transcription usage를 구분해 저장한다.
- [ ] usage는 먼저 local에 저장하고, Firestore에는 세션 단위로 묶어 sync한다.
- [ ] Firestore sync 실패 시 pending 상태로 남기고 이후 재시도할 수 있다.
- [ ] 사용량 기록 실패가 AI Chat 대화 진행을 막지 않는다.

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
- 세션 종료 또는 화면 이탈 시 Firestore session aggregate sync
- pending usage 재시도 경계

### 제외

- 사용자별 사용량 제한
- 유료/무료 플랜 정책
- 정확한 원화 비용 청구 정책
- App Check 검증

---

## 저장 원칙

- Realtime event가 올 때마다 Firestore에 직접 쓰지 않는다.
- 매 turn usage는 local DB에 먼저 저장한다.
- Firestore에는 세션 단위 aggregate를 올린다.
- 비용 계산을 위해 text/audio, input/output breakdown을 최대한 보존한다.
- response usage와 transcription usage는 중복 가능성을 피하기 위해 분리 저장한다.
- 원화 비용은 usage 저장 시점에 바로 확정하지 않는다. 원본 token breakdown을 먼저 보존하고, 비용 산정은 별도 pricing 정책에서 계산한다.

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

- AI Chat 한 턴 완료 후 local usage record가 생성되는지 확인한다.
- `response_done_usage`와 user transcription usage가 서로 다른 필드에 저장되는지 확인한다.
- AI Chat 화면 이탈 또는 세션 종료 시 Firestore session aggregate가 sync되는지 확인한다.
- 네트워크 실패 시 usage가 pending 상태로 남고 다음 sync에서 재시도되는지 확인한다.
- usage 기록 실패가 대화 UI, final transcript 저장, correctionAvailable 신호를 막지 않는지 확인한다.
