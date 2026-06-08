# [Improvement] AI-POLICY-001 Chat 안전 프롬프트와 운영용 신고 기능

## 목적

Umma는 AI Chat에서 사용자의 음성/텍스트 입력에 따라 AI 응답을 실시간으로 생성한다.
Google Play의 AI 생성 콘텐츠 정책에 대응하려면, AI가 유해한 응답을 만들지 않도록 안전 원칙을 주입하고, 사용자가 앱 안에서 부적절한 AI 응답을 신고할 수 있어야 한다.

이 작업은 기존 개발용 prompt review 신고 도구를 운영용 신고 기능으로 바꾸는 작업이 아니다.
기존 도구는 QA/프롬프트 튜닝용으로 유지하고, release에서도 동작하는 별도 AI 콘텐츠 신고 기능을 만든다.

---

# User Story

사용자는 AI가 부적절하거나 위험한 응답을 했을 때 앱을 나가지 않고 신고할 수 있다.
Umma는 신고된 AI 응답과 주변 대화 맥락을 저장해, 이후 안전 정책과 프롬프트 개선에 활용할 수 있다.
AI는 언어 학습 대화 중에도 자해, 범죄, 아동 성착취, 혐오, 괴롭힘, 사기, 성적 콘텐츠, 위험한 전문 조언을 생성하지 않는다.

---

# 완료 기준(AC)

- [ ] Chat AI는 유해하거나 위험한 요청에 대해 짧게 거절하고 안전한 언어 학습 대화로 전환할 수 있다.
- [ ] 안전 정책은 하나의 짧은 prompt 섹션으로 추가되고, 기존 대화 band/persona 지시와 중복되지 않는다.
- [ ] 사용자는 Chat 화면에서 AI 생성 응답을 앱 안에서 신고할 수 있다.
- [ ] 신고 기능은 release 빌드에서도 동작하고, 개발용 prompt review flag에 의존하지 않는다.
- [ ] 신고 저장소는 기존 `chat_prompt_review_reports`와 분리된다.
- [ ] 신고 데이터에는 신고 사용자, 세션, 신고 대상 AI 응답, 직전 사용자 발화, 최근 6턴 컨텍스트, 언어 설정, 신고 사유, 신고 시각, 앱/모델 추적 정보가 포함된다.
- [ ] 최초 구현은 “가장 최근 AI final 응답”을 신고 대상으로 삼고, 신고 가능한 AI 응답이 없으면 신고 버튼을 비활성화한다.
- [ ] 신고 사유는 정해진 category 중 하나를 필수로 선택하고, 상세 메모는 선택 입력으로 둔다.
- [ ] 신고 실패는 현재 대화 세션, 마이크 상태, SessionMemory 저장 흐름을 막지 않는다.
- [ ] 기존 개발용 prompt review 신고 도구는 QA/프롬프트 튜닝 용도로 유지된다.

---

# 기준 문서

- [Google Play AI-generated content policy](https://support.google.com/googleplay/android-developer/answer/13985936)
- [Understanding Google Play's AI-Generated Content policy](https://support.google.com/googleplay/android-developer/answer/14094294)
- [CHAT-FIX-002 AI Chat Prompt Iteration Workflow](../FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-002_AI_Chat_Prompt_Iteration_Workflow.md)
- [CHAT-TUNE-002 Conversation Ability Prompt Strategy](../FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-002_Conversation_Ability_Prompt_Strategy.md)
- [CHAT-TUNE-008 Conversation Frame Snapshot](../FLOW_AI_CHAT_IMPROVEMENTS/CHAT-TUNE-008_Conversation_Frame_Snapshot.md)

---

# 기존 기능과의 관계

## 개발용 prompt review 신고

현재 프로젝트에는 prompt tuning을 위한 개발용 신고 도구가 있다.
이 도구는 `devDebug`와 `CHAT_PROMPT_REVIEW_ENABLED`에 묶여 있고, 어색한 대화/프롬프트 실패를 팀원이 분석하기 위한 도구다.

운영용 AI 콘텐츠 신고 기능은 목적이 다르다.
부적절하거나 유해한 AI 생성 응답을 실제 사용자가 신고할 수 있어야 하며, release 빌드에서도 동작해야 한다.

따라서 기존 prompt review 저장소를 그대로 쓰지 않는다.
다만 세션 ID, turn 수집 방식, prompt version 추적 같은 구현 아이디어는 참고할 수 있다.

## Chat prompt

현재 Chat prompt는 학습자 수준, 기준언어/학습언어 사용 방식, 자연스러운 대화 리듬을 중심으로 구성된다.
안전 정책은 band별 지시에 섞지 않고 `safety_policy` 같은 별도 섹션으로 둔다.
이렇게 해야 안전 원칙이 기존 대화 UX를 과도하게 덮어쓰지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| `ChatScreen` | 신고 버튼/다이얼로그/신고 완료 상태 표시 |
| `ChatViewModel` | 신고 UI 상태, 신고 중복 클릭 방지, 신고 UseCase 호출 |
| `BuildPromptUseCase` | Chat system prompt에 짧은 안전 정책 섹션 추가 |
| `ReportAiContentUseCase` | 신고 요청을 만들고 repository에 전달 |
| `AiContentReportRepository` | 운영용 신고 데이터를 저장 |
| Firestore data source | `ai_content_reports/{reportId}` 저장 |
| 기존 prompt review 도구 | 개발/QA용 유지, 운영 신고와 저장소 분리 |

---

# 주요 작업

1. **Chat 안전 prompt 섹션 추가**
   - `BuildPromptUseCase`에 `safety_policy` 섹션을 추가한다.
   - 금지 목록을 장황하게 늘리지 않고, 위험 요청 거절 후 안전한 언어 학습 대화로 전환한다는 행동 원칙으로 압축한다.
   - band별 prompt나 turn hint에 같은 내용을 반복하지 않는다.

2. **운영용 신고 모델 정의**
   - `AiContentReport` domain 모델을 만든다.
   - 신고 사유 enum을 정의한다.
   - 최초 사유 category는 `HarmfulDangerous`, `HateHarassment`, `SexualInappropriate`, `IllegalFraud`, `SelfHarm`, `Other`로 둔다.
   - 최초 구현의 신고 대상은 “가장 최근 AI final 응답”으로 고정한다.
   - `reportedTurnId`는 해당 AI final transcript의 turnId를 사용한다.
   - 직전 사용자 final transcript가 있으면 `previousUserText`로 함께 저장한다.
   - 신고 판단에 필요한 주변 맥락으로 신고 시점 기준 최근 6턴 final transcript snapshot을 저장한다.
   - 전체 세션 저장은 기본값으로 사용하지 않는다. 필요한 맥락만 저장해 개인정보 노출을 줄인다.
   - 신고 가능한 AI final 응답이 아직 없으면 신고 UI를 비활성화한다.
   - 추후 말풍선/자막 item별 액션 UI가 생기면 특정 AI turn 신고로 확장한다.

3. **운영용 신고 저장소 구현**
   - 기존 `chat_prompt_review_reports`가 아니라 `ai_content_reports/{reportId}`에 저장한다.
   - 신고 상태는 `New`, `Reviewed`, `Actioned`, `Dismissed` 같은 운영 상태를 둘 수 있게 한다.
   - 저장 실패는 대화 기능 실패로 전파하지 않고, 신고 UI에만 실패 안내를 노출한다.

4. **Chat UI 신고 기능 추가**
   - 신고 버튼은 기존 자막 토글/마이크 조작과 충돌하지 않는 위치에 둔다.
   - 신고 다이얼로그에서 사유 category 선택을 필수로 받고, 상세 메모는 선택 입력으로 받는다.
   - 중복 신고를 막기 위해 신고 중/접수 완료 상태를 관리한다.
   - 같은 AI turn은 한 번만 신고할 수 있게 처리한다.

5. **테스트 보강**
   - prompt에 safety 섹션이 한 번만 들어가는지 확인한다.
   - 신고 UseCase가 필수 필드를 저장 요청에 담는지 확인한다.
   - 신고 실패가 Chat 상태를 Error로 바꾸지 않는지 확인한다.

---

# 안전 프롬프트 예시

아래는 방향 예시이며, 실제 문구는 prompt 길이와 기존 대화 품질을 보며 조정한다.

```text
safety_policy:
- 자해, 범죄, 아동 성착취, 혐오·괴롭힘, 사기, 성적 콘텐츠, 위험한 의료·법률·금융 조언은 생성하지 않는다.
- 위험한 요청은 짧게 거절하고, 안전한 일상 언어학습 대화로 전환한다.
- 유해한 행동을 더 구체적이거나 실행 가능하게 만드는 표현 교정도 하지 않는다.
```

주의:

- 같은 내용을 `persona`, `conversation_principles`, band별 지시에 반복하지 않는다.
- “하지 말라” 목록을 계속 늘리는 방식으로 튜닝하지 않는다.
- 실패 사례는 테스트/리뷰 문서에 남기고, 실제 prompt에는 일반화된 행동 원칙만 둔다.

---

# 예외 처리

- 신고 저장 실패는 대화 세션을 종료하거나 마이크를 비활성화하지 않는다.
- 신고 대상 AI turn을 특정할 수 없으면 신고 버튼을 비활성화한다. 운영 신고는 분석 가능한 대상이 있을 때만 받는다.
- 사용자가 신고 메모를 비워도 신고 사유 category가 있으면 저장 가능하게 한다.
- 전체 세션 원문은 기본 저장하지 않는다. 운영 검토에 필요한 최소 맥락으로 최근 6턴 snapshot만 저장한다.
- 민감한 정보가 저장될 수 있으므로 신고 다이얼로그에는 대화 일부가 검토 목적으로 저장될 수 있음을 안내한다.

---

# 검증 기준

- `BuildPromptUseCaseTest`에서 `safety_policy`가 포함되고 중복되지 않는지 확인한다.
- Chat 신고 버튼이 release 설정에서도 노출 가능한 구조인지 확인한다.
- 신고 저장 요청에 `userId`, `sessionId`, `reportedAiText`, `previousUserText`, 최근 6턴 context snapshot, `selectedLang`, `primaryLang`, `reasonCategory`, `reportedAt`이 포함되는지 확인한다.
- 신고 실패가 `AIState`, `micControlState`, `SessionMemory` 저장 흐름을 변경하지 않는지 확인한다.
- 기존 prompt review 신고 도구가 dev/QA 용도로 그대로 남아 있는지 확인한다.
- `:app:compileDevDebugKotlin`, `:app:compileMockDebugKotlin` 빌드가 통과한다.
