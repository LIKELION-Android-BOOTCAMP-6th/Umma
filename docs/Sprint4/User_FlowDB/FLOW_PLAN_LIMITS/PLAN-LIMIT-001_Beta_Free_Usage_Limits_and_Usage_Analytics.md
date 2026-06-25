# [Feature] PLAN-LIMIT-001 베타 무료 플랜 사용량 제한과 사용량 분석 준비

## 목적

MVP 베타 기간에는 모든 사용자를 무료 플랜으로 운영하되, API 비용이 예측 불가능하게 커지지 않도록 사용량 제한을 둔다.
무료 플랜 기준은 최근 24시간 AI 음성 대화 3분, 최근 24시간 교정 3회로 시작한다.

이번 작업은 단순히 버튼을 비활성화하는 UI 작업이 아니다.
사용자가 남은 사용량을 이해할 수 있게 보여주고, 실제 비용이 발생하는 Chat/Correction 시작 경계에서 사용량을 검증하며, 베타 기간 동안 유료 플랜 설계에 필요한 대화 시간과 token 사용량 데이터를 수집하는 기반을 만든다.

---

# User Story

베타 사용자는 AI 대화 화면에서 최근 24시간 기준 남은 대화 시간을 확인할 수 있다.
베타 사용자는 교정 화면에 진입할 때 최근 24시간 기준 남은 교정 횟수를 확인하고, 교정을 진행할지 결정할 수 있다.
베타 사용자는 마이페이지에서 현재 플랜 버튼을 볼 수 있고, 현재는 베타 무료 플랜만 제공된다는 안내를 받는다.

운영자는 베타 기간 동안 사용자별 대화 시간, token 사용량, 교정 사용량을 확인해 실제 유료 플랜 가격과 사용량 cap을 설계할 수 있다.

---

# 완료 기준(AC)

- [ ] 무료 플랜 사용자는 최근 24시간 기준 AI 음성 대화를 최대 3분까지 사용할 수 있다.
- [ ] 무료 플랜 사용자는 최근 24시간 기준 교정을 최대 3회까지 사용할 수 있다.
- [ ] AI 대화 화면에는 남은 대화 시간을 확인하는 진입점이 제공된다.
- [ ] 남은 대화 시간이 없으면 새 AI 대화 세션을 시작할 수 없고, 제한 안내가 표시된다.
- [ ] 교정 시작 전에는 남은 교정 횟수와 진행 여부를 묻는 다이얼로그가 표시된다.
- [ ] 남은 교정 횟수가 없으면 교정을 시작하지 않고 제한 안내가 표시된다.
- [ ] 마이페이지에는 현재 플랜 버튼이 표시되고, 클릭 시 베타 기간에는 무료 플랜만 제공된다는 안내가 표시된다.
- [ ] Chat usage에는 token breakdown과 함께 플랜 설계에 필요한 대화 시간이 저장된다.
- [ ] 사용량 저장 또는 sync가 실패해도 기존 대화 저장, final transcript, 교정 가능 신호는 깨지지 않는다.
- [ ] 사용량 제한은 클라이언트 표시만으로 판단하지 않고 서버 기준 검증 경계를 갖는다.
- [ ] 워치에서 시작한 AI 대화도 폰의 동일한 Chat 사용량 제한을 따른다.

---

# 기준 문서

- [RULES](../../../../RULES.md)
- [SYS-REALTIME-INFRA](../../../System_FlowDB/SYS_REALTIME_INFRA.md)
- [SYS-CORRECTION-INFRA](../../../System_FlowDB/SYS_CORRECTION_INFRA.md)
- [CHAT-ENGINE-001-B Realtime Usage Tracking](../../../Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-ENGINE-001/CHAT-ENGINE-001-B_Realtime_Usage_Tracking.md)
- [AI-POLICY-004 Firebase AppCheck and Rules Hardening](../../../Sprint3/User_FlowDB/FLOW_AI_POLICY/AI-POLICY-004_Firebase_AppCheck_and_Rules_Hardening.md)
- [CHAT-REF-001 ChatViewModel 책임 분리](../FLOW_AI_CHAT_IMPROVEMENTS/CHAT-REF-001_ChatViewModel_Responsibility_Decomposition.md)

---

# 핵심 결정

- **베타 기본 플랜은 무료 플랜 하나만 둔다.**
  - 무료 플랜 제한은 최근 24시간 AI 대화 3분, 최근 24시간 교정 3회다.
  - 유료 결제, 구독, Play Billing 연동은 이번 범위에 포함하지 않는다.

- **대화 제한은 사용자가 이해하기 쉬운 시간 기준으로 보여준다.**
  - 실제 OpenAI 비용은 token 기준이지만, 사용자에게는 남은 시간을 보여주는 편이 직관적이다.
  - 운영 분석에는 시간과 token을 함께 저장해 `token per minute`, `cost per minute`를 계산할 수 있게 한다.
  - 무료 플랜의 대화 시간은 `사용자 발화 시간 + AI 음성 재생 시간`을 기준으로 계산한다.
  - 세션을 켜두었지만 실제 대화가 없던 idle 시간은 무료 플랜 차감 기준에 포함하지 않는다.

- **비용이 발생하는 시작 경계에서 서버 기준으로 검증한다.**
  - AI Chat은 Realtime token 발급 경계에서 최근 24시간 기준 남은 대화 시간이 있는지 확인한다.
  - Correction은 교정 실행 경계에서 최근 24시간 기준 남은 교정 횟수가 있는지 확인한다.
  - UI는 사용자 안내와 선제 차단을 담당하지만, 최종 비용 방어는 서버/저장 기준에서 수행한다.
  - 남은 대화 시간이 10초 이하이면 새 AI Chat 세션을 시작하지 않는다.
  - 사용량 한도에 도달한 진행 중 세션은 현재 AI 응답이 끝난 뒤 종료한다.

- **베타 데이터는 유료 플랜 설계를 위한 관측 데이터로 남긴다.**
  - 사용자별 실제 대화 시간, AI 응답 token, 사용자 input token, 교정 횟수, 제한 도달 여부를 본다.
  - 베타 기간 중 플랜 가격을 확정하지 않고, 실제 사용량 분포를 보고 조정한다.

- **기존 local-first usage 저장 흐름은 유지한다.**
  - token usage는 현재 `ChatUsageRecord`와 세션 aggregate 구조를 확장해 보존한다.
  - usage sync 실패는 기존처럼 사용자 대화 실패로 전파하지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| ChatScreen | 남은 사용량 버튼, 제한 안내, ViewModel 상태 표시를 담당한다. |
| ChatViewModel | UI 상태를 조립하고 사용자의 Chat 시작 요청을 UseCase에 위임한다. |
| Chat UseCase | 최근 24시간 기준 남은 대화 시간 조회, Chat 시작 가능 여부 판단, 사용량 표시 모델 생성을 담당한다. |
| Chat Repository/DataSource | local usage 저장, Cloud Function 호출, remote aggregate sync를 담당한다. |
| Cloud Functions | Realtime token 발급 전 사용량 한도를 검증하고, usage aggregate를 서버 기준으로 갱신한다. |
| Correction Screen/ViewModel | 교정 시작 전 남은 횟수 다이얼로그를 표시하고 사용자 확인을 받는다. |
| Correction UseCase | 교정 시작 가능 여부를 확인하고, 교정 성공 또는 시작 시점에 사용량을 반영한다. |
| MyPageScreen | 현재 플랜 버튼과 베타 무료 플랜 안내를 표시한다. |
| Firestore Rules | 클라이언트가 서버 전용 사용량 aggregate를 임의 수정하지 못하게 막는다. |
| Watch 연동 경로 | 별도 사용량 정책을 만들지 않고 폰 Chat 경로의 사용량 제한 결과를 따른다. |

---

# 사용량 기준

## 무료 플랜 제한

| 항목 | 제한 | 기준 |
| --- | ---: | --- |
| AI 음성 대화 | 최근 24시간 3분 | 서버 timestamp 기준 rolling window |
| 교정 | 최근 24시간 3회 | 서버 timestamp 기준 rolling window |

## 대화 시간 계산 기준

대화 시간은 사용자에게 제공된 AI 음성 대화 사용량을 나타내는 값이다.
OpenAI 비용 자체는 token 기준이므로, 시간은 플랜 제한과 운영 분석을 위한 별도 지표로 저장한다.

권장 저장값:

| 필드 | 의미 |
| --- | --- |
| `conversationBillableDurationMs` | 무료 플랜 차감 기준이 되는 실제 음성 대화 시간 |
| `userSpeakingDurationMs` | 사용자가 마이크로 발화한 시간 합계 |
| `aiSpeakingDurationMs` | AI 음성이 실제 재생된 시간 합계 |
| `idleSessionDurationMs` | 세션은 열려 있었지만 실제 발화/재생이 없던 시간 |
| `turnCount` | 사용자 발화 turn 수 |
| `responseCount` | AI 응답 수 |
| `totalTokens` | response/transcription token 합계 |
| `inputAudioTokens` | 사용자 음성 입력 token 합계 |
| `outputAudioTokens` | AI 음성 출력 token 합계 |

### 시간 계산 원칙

- 사용자 제한에는 `conversationBillableDurationMs = userSpeakingDurationMs + aiSpeakingDurationMs`를 사용한다.
- 이 기준은 사용자가 실제로 말하고 들은 시간에 가까워 idle 시간을 포함한 세션 길이보다 공정하다.
- OpenAI 비용은 token 기준이지만, 실제 음성이 오간 시간이 token 비용과 가장 가까운 사용자 표시 기준이다.
- 세션을 열어두고 아무 말도 하지 않은 idle 시간은 제한 사용량에 과도하게 반영하지 않는다.
- 사용량 분석에는 idle 포함 세션 길이도 별도 필드로 남길 수 있지만, 무료 플랜 차감 기준과 섞지 않는다.

### 사용량 기준 시간 범위

- 무료 플랜 한도는 자정 리셋이 아니라 최근 24시간 rolling window 기준으로 계산한다.
- 서버는 현재 서버 시각에서 24시간 전까지의 사용량 이벤트를 합산해 남은 사용량을 판단한다.
- 이 방식은 KST/UTC/사용자 로컬 자정 기준보다 글로벌 사용자에게 공정하고, 기기 시간대 변경으로 한도를 반복 초기화하는 문제를 줄인다.
- 사용자가 한도를 모두 사용하면 가장 오래된 사용 이벤트가 24시간 범위 밖으로 밀려나는 시각을 `다시 사용 가능 시각`으로 계산한다.
- 클라이언트 기기 시각과 timezone은 표시 보조값으로만 사용하고, 사용량 한도 판단에는 서버 timestamp를 사용한다.

---

# 사용량 한도 적용 정책

## 기존 usage 저장 구조 유지 원칙

- 기존 `chat_usage_sessions`와 `chat_usage_monthly`는 사용자별 하위 컬렉션 위치를 유지한다.
- 이 구조는 사용자 삭제, 사용자별 보안 규칙, 사용자별 비용 분석, 개인정보 경계에 맞다.
- 루트 전역 컬렉션으로 옮기면 collection group 집계는 쉬워질 수 있지만, 사용자 소유권 검증과 계정 삭제 경계가 복잡해진다.
- PLAN-LIMIT-001은 새 사용량 제한을 기존 usage 흐름에 무작정 덧붙이는 작업이 아니라, 기존 `chat_usage_sessions`를 세션 디버깅/원본 aggregate로 유지하고 `usage_events`를 rolling window 판단용 원본으로 분리하는 작업이다.
- 월별 비용 분석은 기존 `chat_usage_monthly`를 확장하는 방향을 우선 검토한다.
- 별도 `usage_monthly`는 Chat 외 Correction까지 통합한 월별 플랜 분석이 필요해질 때만 추가한다.

## 남은 사용량 조회

- 앱은 `getPlanUsageStatus` Cloud Function을 통해 서버 기준 남은 사용량을 조회한다.
- 이 함수는 비용이 발생하는 작업을 시작하지 않고, UI 표시와 선제 안내에 필요한 값만 반환한다.
- 반환값에는 `planId`, `remainingChatMs`, `remainingCorrectionCount`, `nextChatAvailableAt`, `nextCorrectionAvailableAt`, `windowStartedAt`, `serverNow`를 포함한다.
- 클라이언트 표시값은 stale일 수 있으므로, 실제 Chat/Correction 시작 가능 여부는 각 시작 함수에서 다시 검증한다.

## Chat 한도 적용

- 새 AI Chat 세션 시작 전 `realtimeToken` Cloud Function에서 최근 24시간 기준 남은 대화 시간을 확인한다.
- 남은 시간이 10초 이하이면 새 세션을 시작하지 않고 제한 안내를 표시한다.
- token 발급 성공 응답에는 `remainingChatMs`, `sessionUsageLimitMs`, `serverNow`를 함께 내려준다.
- 앱은 서버가 내려준 `sessionUsageLimitMs`를 기준으로 진행 중 세션의 종료 타이밍을 계산한다.
- 세션 중 사용량 한도에 도달하면 현재 AI 응답이 끝난 뒤 세션을 종료한다.
- 즉시 음성을 끊지 않는 이유는 사용자가 듣고 있는 AI 응답이 중간에 잘리는 UX를 피하기 위해서다.
- 세션 종료 후 실제 `conversationBillableDurationMs`를 기준으로 rolling window 이벤트와 monthly aggregate를 정산한다.
- 짧은 초과분은 허용하되, 반복적인 큰 초과가 발생하지 않도록 클라이언트 timer와 서버 aggregate를 함께 사용한다.
- Chat 사용량 event idempotency key는 `sessionId`를 사용한다.
- 같은 `sessionId` usage sync가 재전송되면 기존 event/session aggregate와 차이만 반영하거나 같은 값으로 덮어써 중복 차감을 막는다.
- 한도 도달로 세션을 종료할 때는 WebSocket을 즉시 강제 종료하지 않고, `response.done`과 AudioPlayer drain 이후 기존 정상 종료 흐름을 사용한다.
- 기존 마이크 상태, `AIState.SPEAKING`, `isAudioOutputPlaying`, final transcript 저장 흐름을 변경하지 않는다.
- AI 음성 재생 시간은 마지막 playback duration만 쓰지 않고 세션 동안 여러 응답의 playback duration을 누적한다.
- 워치에서 AI 대화를 시작하더라도 실제 Realtime token 발급과 세션 처리는 폰 Chat 경로를 통과해야 한다.
- 워치 전용 우회 경로가 있으면 같은 `getPlanUsageStatus`와 `realtimeToken` 검증을 사용하도록 막는다.

## Correction 한도 적용

- 교정은 `reserveCorrectionUsage` Cloud Function으로 시작 시점에 사용량을 예약한다.
- 예약 성공 응답에는 `reservationId`, `remainingCorrectionCount`, `expiresAt`을 포함한다.
- 교정이 성공하면 `completeCorrectionUsage` Cloud Function으로 예약을 완료 사용량으로 확정한다.
- 교정이 실패하거나 서버가 처리하지 못한 경우 `restoreCorrectionUsage` Cloud Function으로 예약을 복구한다.
- 중복 탭이나 재시도로 같은 교정 요청이 여러 번 들어와도 `reservationId` 또는 `correctionJobId` 기준 idempotency key와 transaction으로 과도하게 차감하지 않는다.
- 예약 만료 시간은 10분으로 둔다.
- 만료된 예약은 다음 조회/예약 시 서버가 복구하거나 scheduled cleanup에서 복구한다.
- 이 방식은 API 비용 방어와 사용자 공정성을 함께 지키기 위한 절충안이다.
- 예약은 `generateSuggestions`가 실제로 시작되기 직전 한 번만 수행한다.
- 기존 `generationLaunched` 가드와 같은 생명주기에서 reservationId를 보관해 화면 재진입, LangState refresh, retry로 예약이 중복 생성되지 않게 한다.
- `CompleteCorrectionUseCase`의 Flashcard 저장, LangState update, Statistics 기록, SessionMemory compression 방어 흐름은 변경하지 않는다.
- 교정 저장 완료가 성공한 뒤에만 예약을 완료 사용량으로 확정하고, 완료 파이프라인 실패 또는 교정 생성 실패 시 예약을 복구한다.
- 사용자가 교정 진행 중 화면을 닫거나 앱이 백그라운드로 가는 경우에도 예약이 무기한 남지 않도록 best-effort 복구를 시도한다.
- best-effort 복구가 실패해도 서버의 10분 만료 복구가 최종 안전망이 된다.

---

# 서버 함수 계획

| 함수 | 역할 | 비용 발생 여부 |
| --- | --- | --- |
| `getPlanUsageStatus` | 최근 24시간 남은 사용량과 다시 사용 가능 시각을 조회한다. | 없음 |
| `realtimeToken` | Chat 사용량 한도를 검증한 뒤 OpenAI Realtime client secret을 발급한다. | token 발급 이후 발생 |
| `submitChatUsageSession` | 종료된 Chat 세션의 token/duration usage를 저장하고 rolling window event/monthly aggregate를 갱신한다. | 없음 |
| `reserveCorrectionUsage` | 교정 시작 전 최근 24시간 교정 한도를 확인하고 예약을 만든다. | 없음 |
| `completeCorrectionUsage` | 성공한 교정 예약을 완료 사용량으로 확정한다. | 없음 |
| `restoreCorrectionUsage` | 실패한 교정 예약을 복구한다. | 없음 |
| `cleanupExpiredUsageEvents` | 보관 기간이 지난 usage event와 만료된 reservation을 정리한다. | 없음 |

서버 함수 공통 원칙:

- 모든 함수는 Firebase Auth uid를 기준으로 사용자 경계를 검증한다.
- 비용 발생 작업을 시작하는 함수는 App Check header를 받을 수 있어야 한다.
- App Check enforcement가 아직 켜지지 않은 단계에서는 Auth와 서버 검증을 먼저 적용하고, enforcement 이후에는 App Check 누락 요청이 차단되는지 별도 확인한다.
- 사용량 한도 계산은 클라이언트 시각이 아니라 서버 시각을 기준으로 한다.
- rolling window 계산과 예약/확정/복구는 transaction으로 처리해 동시 요청 중복 차감을 막는다.

# 데이터 구조 계획

## 서버 aggregate

권장 경로:

```text
users/{uid}/chat_usage_sessions/{sessionId}
users/{uid}/chat_usage_monthly/{yyyyMM}
users/{uid}/usage_events/{eventId}
users/{uid}/usage_reservations/{reservationId}
```

각 경로의 역할:

| 경로 | 역할 | 클라이언트 write |
| --- | --- | --- |
| `chat_usage_sessions` | 세션 단위 token/duration 디버깅과 재전송 idempotency 기준 | 금지 |
| `chat_usage_monthly` | 월별 Chat 비용 분석과 장기 운영 집계 | 금지 |
| `usage_events` | 최근 24시간 rolling window 제한 판단의 원본 이벤트 | 금지 |
| `usage_reservations` | Correction 예약/확정/복구 상태 관리 | 금지 |

rolling window 판단을 위해 `usage_events` 같은 원본 이벤트 또는 충분히 세밀한 time bucket이 필요하다.
월별 aggregate는 운영 분석에는 유용하지만, 최근 24시간 한도 판단에는 오래된 이벤트가 window 밖으로 빠지는 시각을 계산하기 어렵다.
초기 구현은 사용자별 `usage_events` 쿼리로 충분하다.
베타 사용량이 늘어 조회 비용이나 latency가 실제 문제가 되면 hour bucket 또는 rolling summary를 추가하되, 초기부터 별도 aggregate를 과하게 만들지 않는다.

사용량 이벤트 예시:

```text
eventId: "..."
planId: "beta_free"
eventType: "chat_session"
idempotencyKey: "sessionId-or-reservationId"
createdAt: ...
expiresFromWindowAt: createdAt + 24h
conversationBillableDurationMs: 120000
userSpeakingDurationMs: 45000
aiSpeakingDurationMs: 75000
idleSessionDurationMs: 30000
totalTokens: 6055720
inputAudioTokens: ...
inputCachedTokens: ...
outputAudioTokens: ...
```

Correction 예약 예시:

```text
reservationId: "..."
planId: "beta_free"
reservationType: "correction"
status: "Reserved" | "Completed" | "Restored" | "Expired"
correctionJobId: "..."
createdAt: ...
expiresAt: createdAt + 10m
completedAt: ...
restoredAt: ...
```

분석 aggregate 예시:

```text
planId: "beta_free"
periodId: "20260619"
chatLimitMs: 180000
correctionReservedCount: 0
correctionCompletedCount: 2
correctionFailedCount: 0
correctionRestoredCount: 0
correctionLimit: 3
chatSessionCount: 4
chatTurnCount: 12
chatLimitBlockedCount: 0
correctionLimitBlockedCount: 0
usageSyncFailedCount: 0
limitReached: false
updatedAt: ...
```

월별 aggregate는 유료 플랜 설계와 비용 분석을 위해 긴 기간의 사용량을 합산한다.
현재 `chat_usage_monthly`가 이미 token aggregate를 담당하므로, Chat duration 필드는 이 문서의 구현 범위에서는 `chat_usage_monthly`에 확장하는 방향을 우선한다.
Chat과 Correction을 합친 월별 플랜 분석이 필요해지는 시점에만 별도 `usage_monthly`를 검토한다.
사용량 제한 판단은 aggregate만 믿지 않고 최근 24시간 이벤트 또는 time bucket을 기준으로 계산한다.
`usage_events`는 30일 보관을 기본값으로 둔다.
최근 24시간 판단에는 24시간 이벤트만 필요하지만, 30일 보관하면 베타 중 문제 재현과 비용 분석에 필요한 최소 디버깅 여유가 생긴다.
Firestore Rules에서는 `usage_events`, `usage_reservations`도 `chat_usage_sessions`, `chat_usage_monthly`와 같은 서버 전용 컬렉션으로 막는다.
계정 삭제 시 `users/{uid}` 하위 사용량 문서가 함께 삭제되는지 `AI-POLICY-006` 기준으로 확인한다.
만약 계정 삭제 함수가 하위 컬렉션을 재귀 삭제하지 않는 구조라면 `usage_events`, `usage_reservations`, `chat_usage_sessions`, `chat_usage_monthly`를 삭제 대상에 명시한다.

## local usage

local usage는 기존 `chat_usage_records`를 유지한다.
다만 세션 aggregate 생성 시 duration 값을 함께 계산하려면 local 또는 session runtime에 아래 값이 필요하다.

- user turn별 `durationMs`
- AI playback 실제 재생 시간
- 세션 시작/종료 시각
- idle 제외 대화 시간

local schema 변경이 필요하면 migration 또는 별도 usage duration table을 검토한다.
기존 Room DB는 `exportSchema=false`이며 usage 전용 DB이지만, 설치 사용자의 기존 row 유실 여부는 구현 전에 결정한다.
duration 보강은 기존 token usage 원본 저장을 깨지 않는 방향으로 진행한다.
AI playback duration은 세션 단위 누적값으로 저장해 마지막 응답만 반영되는 회귀를 막는다.

---

# 주요 작업

## 1. 무료 플랜 정책 모델 정의

정의할 값:

- `planId = beta_free`
- `rollingWindowMillis = 24 * 60 * 60 * 1000`
- `chatLimitMs = 3 * 60 * 1000`
- `correctionLimit = 3`
- 최근 24시간 rolling window 기준
- 제한 초과 상태
- 다시 사용 가능 시각
- 남은 대화 시간/교정 횟수 표시 모델
- Chat session idempotency key = `sessionId`
- Correction idempotency key = `reservationId` 또는 `correctionJobId`
- Correction reservation 만료 = 10분
- rolling window event 보관 = 30일
- 서버 전용 사용량 컬렉션 = `chat_usage_sessions`, `chat_usage_monthly`, `usage_events`, `usage_reservations`

정책 위치:

- Domain UseCase 또는 policy class에 둔다.
- UI나 Repository에 숫자 상수를 직접 흩뿌리지 않는다.

## 2. Chat 사용량 제한

작업:

- Chat 화면에 남은 사용량 확인 버튼을 추가한다.
- 버튼을 누르면 최근 24시간 기준 남은 대화 시간, 교정 횟수, 베타 무료 플랜 안내를 보여준다.
- `getPlanUsageStatus`를 호출해 서버 기준 남은 사용량을 조회한다.
- Chat 세션 시작 전 남은 대화 시간이 있는지 확인한다.
- Realtime token 발급 Cloud Function에서 서버 기준 사용량 한도를 확인한다.
- token 발급 응답의 `remainingChatMs`와 `sessionUsageLimitMs`를 UI 상태에 반영한다.
- 남은 시간이 10초 이하이면 새 세션을 시작하지 않는다.
- 진행 중 한도에 도달하면 현재 AI 응답이 끝난 뒤 종료한다.
- 제한이 끝났으면 token을 발급하지 않고 앱에 제한 안내를 반환한다.
- 한도 종료는 기존 정상 세션 종료 경로를 사용해 마이크 활성화 복구와 AI 음성 재생 drain 방어를 유지한다.
- 워치에서 시작한 대화도 폰 Chat 경로의 token 발급과 사용량 검증을 사용한다.

주의:

- 이미 진행 중인 세션은 짧은 초과가 발생할 수 있다.
- MVP 베타에서는 정확한 초 단위 차단보다 과도한 비용 방어와 사용자 안내를 우선한다.
- 장시간 초과를 막기 위해 클라이언트에서도 남은 시간 기준으로 세션 종료 또는 마이크 비활성화를 검토한다.
- `realtimeToken` 응답은 기존 `client_secret.value` 구조를 유지하고, 사용량 필드는 추가 필드로만 붙인다.

## 3. Chat usage duration 저장

작업:

- 사용자 발화 duration 합계를 session aggregate에 포함한다.
- AI playback duration 합계를 session aggregate에 포함한다.
- `conversationBillableDurationMs = userSpeakingDurationMs + aiSpeakingDurationMs`를 session aggregate에 포함한다.
- idle 시간은 `idleSessionDurationMs`로 분리해 분석용으로만 보존한다.
- Cloud Function monthly aggregate와 rolling window 이벤트에 duration 합산을 추가한다.
- 기존 token breakdown 저장은 유지한다.

주의:

- OpenAI 비용 계산은 token 기준이므로 duration은 비용 대체값이 아니라 분석 보조값이다.
- duration 저장 실패가 final transcript 저장, correctionAvailable 신호, usage token 저장을 막지 않아야 한다.
- AI playback duration은 응답마다 누적하고, 세션 종료 후 aggregate에 반영한다.

## 4. Correction 사용량 제한

작업:

- 교정 화면 진입 또는 교정 시작 전 남은 교정 횟수를 조회한다.
- 남은 횟수가 있으면 `최근 24시간 기준 교정 가능 횟수가 N회 남았습니다. 교정하시겠습니까?` 다이얼로그를 표시한다.
- 사용자가 확인하면 교정을 시작한다.
- 남은 횟수가 없으면 교정을 시작하지 않고 제한 안내를 표시한다.
- 교정 시작 시점에 사용량을 예약한다.
- 교정 성공 시 예약을 완료 사용량으로 확정한다.
- 교정 실패 시 예약을 복구한다.
- 중복 요청은 idempotency key와 transaction으로 과도하게 차감되지 않게 한다.
- 예약이 10분 안에 완료/복구되지 않으면 만료 예약으로 보고 서버에서 복구할 수 있게 한다.
- 기존 Correction 자동 생성 가드, 캐시, retry, 완료 rollback/dedup 방어 로직을 제거하지 않는다.

## 5. MyPage 현재 플랜 안내

작업:

- 마이페이지에 `현재 플랜` 버튼을 추가한다.
- 클릭 시 현재는 베타 버전이며 무료 플랜만 이용 가능하다는 안내를 표시한다.
- 유료 플랜/결제 UI/Play Billing은 연결하지 않는다.

권장 문구:

```text
현재 Umma는 베타 기간 동안 무료 플랜으로 제공됩니다.
AI 대화와 교정 사용량은 안정적인 운영을 위해 최근 24시간 기준으로 제한됩니다.
정식 플랜은 베타 사용 데이터를 바탕으로 준비할 예정입니다.
```

## 6. 베타 운영 분석 데이터 준비

분석할 지표:

- 사용자별 최근 24시간/월 대화 시간
- 사용자별 최근 24시간/월 사용자 발화 시간
- 사용자별 최근 24시간/월 AI 음성 재생 시간
- 사용자별 최근 24시간/월 AI output audio token
- 사용자별 최근 24시간/월 input audio token
- 사용자별 최근 24시간/월 cached token
- 사용자별 교정 횟수
- 제한 도달 사용자 비율
- 제한 차단 횟수
- usage sync 실패 횟수
- 평균 token per minute
- 평균 cost per minute
- heavy user 상위 비율
- 무료 플랜 한도 소진율

목표:

- 실제 무료 사용자 1명당 월 비용 추정
- `월 60분`, `월 100분`, `월 150분` 플랜별 손익분기점 추정
- 교정 횟수 cap이 비용에 미치는 영향 파악

운영 조회 방식:

- MVP 단계에서는 Firebase Console, collection group query, 또는 운영 스크립트로 사용량을 확인한다.
- 관리자용 대시보드는 이번 작업 범위에 포함하지 않는다.
- 운영 조회가 필요해질 때도 클라이언트 write 금지 원칙은 유지하고, 서버 또는 관리자 권한 경로에서만 aggregate를 읽는다.

---

# 예외 처리

- 서버 사용량 한도 조회 실패 시 새 비용 발생 작업은 기본적으로 시작하지 않는다.
- local 표시값이 stale일 수 있으므로, 최종 시작 가능 여부는 서버 응답을 기준으로 한다.
- 사용량 sync가 실패하면 local pending 상태로 남기고 다음 진입 시 재시도한다.
- 동일 Chat/Correction 사용량 요청이 재전송되어도 idempotency key 기준으로 중복 차감하지 않는다.
- 사용자가 기기 시간대나 기기 시각을 변경해도 서버 timestamp 기준 최근 24시간 사용량 한도는 변하지 않아야 한다.
- 사용자가 한도를 모두 사용한 경우, 가장 오래된 사용량 이벤트가 24시간 window에서 빠지는 시각을 기준으로 다시 사용 가능 안내를 표시한다.
- 네트워크 실패로 교정 예약 후 실제 교정이 실패한 경우 count 복구 또는 실패 상태 표시 기준을 둔다.
- 앱 크래시나 네트워크 단절로 교정 예약이 남으면 10분 만료 후 서버가 복구한다.
- 계정 삭제 시 usage aggregate 삭제/보관 기준은 `AI-POLICY-006`과 맞춘다.
- `usage_events`, `usage_reservations`는 클라이언트가 직접 write할 수 없어야 한다.
- App Check enforcement 전후의 동작 차이를 구분해, enforcement 전에는 기능이 동작하고 enforcement 후에는 미검증 요청이 차단되는지 확인한다.

---

# 제외 범위

- 유료 플랜 가격 확정
- Play Billing 연동
- 구독 상태 검증
- 프로모션 코드 또는 무료 체험권
- 관리자용 비용 대시보드 UI
- 플랜별 기능 차등 정책
- OpenAI/Gemini 모델 교체
- Correction 품질 개선

---

# 테스트 방법

- Chat 화면에서 남은 사용량을 확인하고, 남은 시간이 충분한 경우에만 세션을 시작할 수 있는지 확인한다.
- 남은 시간이 0 또는 10초 이하인 경우 새 Chat 세션이 차단되고 제한 안내가 표시되는지 확인한다.
- 진행 중 한도 도달 시 현재 AI 응답이 끝난 뒤 정상 종료되고, 마이크/하단 상태가 stuck 상태로 남지 않는지 확인한다.
- 한 Chat 세션 후 token, 사용자 발화 시간, AI 재생 시간, 차감 대상 시간이 Firestore에 저장되는지 확인한다.
- 여러 AI 응답이 있는 세션에서 AI 재생 시간이 마지막 응답이 아니라 전체 응답 합계로 저장되는지 확인한다.
- 같은 Chat usage가 재전송되어도 rolling window event와 monthly aggregate가 중복 증가하지 않는지 확인한다.
- 워치에서 시작한 대화도 폰 Chat과 같은 사용량 제한을 통과하는지 확인한다.
- 교정 시작 전 남은 횟수 다이얼로그가 표시되고, 남은 횟수가 0이면 교정 API가 시작되지 않는지 확인한다.
- 교정 예약/성공/실패/만료/화면 이탈 상황에서 reservation이 중복 차감 없이 완료 또는 복구되는지 확인한다.
- 최근 24시간 밖의 event는 한도 계산에서 제외되고, 30일이 지난 event는 cleanup 대상이 되는지 확인한다.
- 기기 시각이나 timezone을 변경해도 서버 기준 사용량이 임의로 초기화되지 않는지 확인한다.
- Firestore Rules에서 `usage_events`, `usage_reservations` 클라이언트 write가 거절되는지 확인한다.
- 계정 삭제 후 사용자 하위 usage 문서가 삭제 또는 정책대로 보관되는지 확인한다.
- App Check enforcement 전후로 Functions 호출 실패/성공 기준이 문서와 맞는지 확인한다.
- 마이페이지 현재 플랜 버튼이 베타 무료 플랜 안내만 표시하는지 확인한다.
- usage sync 실패를 재현했을 때 local pending retry가 유지되고 대화 UX가 깨지지 않는지 확인한다.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin`을 실행한다.

---

# 검증 기준

- 무료 플랜 제한 숫자는 Domain 정책 한 곳에서 확인할 수 있다.
- Chat/Correction 시작은 서버 timestamp 기준 최근 24시간 한도 검증을 통과해야 한다.
- Chat duration은 token 비용 대체값이 아니라 플랜 제한/분석 보조값으로 분리된다.
- `conversationBillableDurationMs`는 `userSpeakingDurationMs + aiSpeakingDurationMs` 기준으로 계산된다.
- Chat usage는 `sessionId`, Correction usage는 reservation idempotency key 기준으로 중복 차감되지 않는다.
- 한도 도달 종료는 기존 세션 정상 종료 흐름을 사용해 마이크/AI speaking 상태 회귀를 만들지 않는다.
- Correction 예약은 기존 generate 가드와 완료 rollback/dedup 방어를 제거하지 않고 결합된다.
- Firestore Rules는 클라이언트가 서버 전용 usage 컬렉션을 임의 수정하지 못하게 유지한다.
- 계정 삭제와 워치 경유 Chat은 사용량 제한/보안 경계를 우회하지 않는다.
- MyPage `현재 플랜` 버튼은 결제 기능이 있는 것처럼 오해시키지 않는다.
- 기존 Chat, Correction, Flashcard, LearningState, Statistics 동작은 회귀하지 않는다.

---

# 완료 후 기대 상태

- 베타 무료 플랜의 사용량 제한이 사용자에게 명확히 안내된다.
- API 비용이 무제한으로 증가하는 경로가 줄어든다.
- 운영자는 실제 token 사용량과 대화 시간을 함께 보고 유료 플랜을 설계할 수 있다.
- 유료 플랜/결제 연동 없이도 베타 배포에 필요한 비용 방어와 사용량 관측 기반이 준비된다.
