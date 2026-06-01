# [Fix] CHAT-FIX-001 AI Chat Sprint2 후속 수정

## User Story

사용자는 Sprint2에서 구현된 AI Chat 기본 흐름을 사용할 때, 진입 설정, 화면 회전, 대화 상태 유지 같은 기본 UX가 끊기거나 우회되지 않는 안정적인 경험을 해야 한다.

이번 Flow 문서는 Sprint2 AI Chat 구현 이후 발견된 후속 수정 사항을 하나의 Fix 흐름으로 관리한다. 실제 백로그 이슈는 아래 작업 단위별로 작게 나누어 처리한다.

추가 Sprint2 회귀나 안정성 문제가 발견되면 `CHAT-FIX-001-G`, `CHAT-FIX-001-H`처럼 이 문서에 작업 단위를 확장한다.

---

# 작업 단위

| 작업 ID | 범위 | 목적 |
| --- | --- | --- |
| `CHAT-FIX-001-A` | 화면 회전 시 세션/화면 상태 유지 | READY 상태의 LiveSession/UI 상태가 회전으로 초기화되지 않게 한다. |
| `CHAT-FIX-001-B` | 관심주제 선택 다이얼로그 안정성 | 필수 선택 단계를 우회하지 못하게 하고, 회전/가로 화면에서도 선택을 완료할 수 있게 한다. |
| `CHAT-FIX-001-C` | 마이크 버튼 상태 UX | 입력 가능/녹음 중/AI 응답 중 버튼 상태를 명확히 표시한다. |
| `CHAT-FIX-001-D` | final 자막 대화형 표시 | 사용자/AI final 자막을 메신저 대화처럼 순서대로 표시한다. |
| `CHAT-FIX-001-E` | 음성 레벨 반응형 웨이브 | 사용자 입력/AI 출력 음성 레벨을 더 직관적인 원형 wave로 표시한다. |
| `CHAT-FIX-001-F` | AI 음성 재생 연속성 | AI 음성이 중간에 잘려 들리는 원인을 확인하고 끊김 없이 재생되게 한다. |
| `CHAT-FIX-001-G+` | 추후 Sprint2 후속 수정 | Sprint2 AI Chat 흐름에서 추가로 발견되는 회귀/안정성 문제를 작은 이슈 단위로 추가한다. |

---

# 완료 기준(AC) (Acceptance Criteria)

## CHAT-FIX-001-A 세션 유지

- [ ] READY 상태에서 화면 회전 시 새 LiveSession이 불필요하게 생성되지 않는다.
- [ ] 화면 회전 시 `ChatUiState`가 초기값으로 재설정되지 않는다.
- [ ] 화면 회전 후 자막 On/Off 상태가 유지된다.
- [ ] 화면 회전 후 마지막 사용자/AI 자막이 유지된다.
- [ ] 화면 회전 후 마이크 버튼 상태가 현재 세션 상태와 일치한다.
- [ ] AI 응답 중 화면 회전이 발생해도 응답 상태가 중복 초기화되지 않는다.
- [ ] 실제 AI Chat back stack이 제거될 때는 기존처럼 녹음과 재생이 정리된다.

## CHAT-FIX-001-B 관심주제 선택 다이얼로그

- [ ] 관심주제 미설정 사용자가 AI Chat에 진입하면 관심주제 선택 다이얼로그가 표시된다.
- [ ] 관심주제 선택 다이얼로그에는 닫기/취소 버튼이 노출되지 않는다.
- [ ] 다이얼로그 외부 터치 또는 뒤로가기로 필수 선택 단계를 우회할 수 없다.
- [ ] 화면 회전 후에도 선택한 관심주제가 선택 상태로 유지된다.
- [ ] 화면 회전 후에도 관심주제 목록을 스크롤해 모든 항목을 선택할 수 있다.
- [ ] 관심주제가 5개 미만이면 안내 문구가 표시되고 완료 버튼이 비활성화된다.
- [ ] 관심주제 5개 선택 시 완료 버튼이 활성화되고 선택 안내 문구가 노출되지 않는다.
- [ ] 저장 성공 후 다이얼로그가 닫히고 AI Chat 흐름을 계속 진행할 수 있다.

## CHAT-FIX-001-C 마이크 버튼 상태 UX

- [ ] 마이크 버튼은 입력 가능 상태에서 녹음 시작 동작으로 표시된다.
- [ ] 녹음 중에는 같은 버튼이 정지 동작으로 표시된다.
- [ ] AI 응답 중에는 마이크 버튼 형태를 유지하되 비활성화 상태로 표시된다.
- [ ] 정지 버튼을 누르면 user turn 종료 및 사용자 발화 확정 대기 상태로 전환된다.
- [ ] 세션 준비 전, AI 응답 중, 빠른 중복 클릭 상황에서 중복 녹음이 시작되지 않는다.

## CHAT-FIX-001-D final 자막 대화형 표시

- [ ] 사용자와 AI의 최신 final 자막은 메신저 대화처럼 역할별로 표시된다.
- [ ] 화면에는 사용자 최신 자막 1개와 AI 최신 자막 1개만 유지된다.
- [ ] 사용자 자막과 AI 자막은 역할이 구분되어 표시된다.
- [ ] 사용자/AI 자막은 실시간 타이핑이 아니라 final transcript 기준으로 표시된다.
- [ ] 같은 final 자막이 화면에 중복 표시되지 않는다.
- [ ] 긴 자막은 중앙 음성 visual을 덮지 않고 자막 영역 안에서 스크롤로 확인할 수 있다.

## CHAT-FIX-001-E 음성 레벨 반응형 웨이브

- [ ] 사용자가 말하는 동안 중앙 애니메이션이 입력 음성 크기에 맞춰 눈에 띄게 반응한다.
- [ ] AI가 말하는 동안 중앙 애니메이션이 출력 음성 크기에 맞춰 눈에 띄게 반응한다.
- [ ] 애니메이션은 기존 원형 계열을 유지하되 물결처럼 유동적인 wave 형태로 표시된다.
- [ ] 음성 레벨이 작거나 없을 때는 과한 움직임 없이 idle 상태로 돌아온다.
- [ ] wave 변경 후에도 마이크 버튼 상태, final 자막 표시, 대화 저장 흐름은 기존처럼 동작한다.

## CHAT-FIX-001-F AI 음성 재생 연속성

- [ ] AI 응답 음성이 중간에 단어가 빠진 것처럼 잘려 들리지 않는다.
- [ ] 짧은 AI 응답과 긴 AI 응답 모두 시작, 중간, 말끝이 자연스럽게 재생된다.
- [ ] AI 음성이 실제로 끝나기 전에는 마이크 버튼이 다시 활성화되지 않는다.
- [ ] AI 음성이 끝난 뒤에는 output level과 마이크 버튼 상태가 정상으로 돌아온다.
- [ ] 재생 안정성을 확인할 수 있도록 audio 수신, 재생 시작, 재생 종료 경계를 로그로 확인할 수 있다.
- [ ] 수정 후에도 마이크 버튼 비활성화, final 자막 표시, 사용량 기록 흐름은 기존처럼 동작한다.

---

# Flow (링크)

- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT.md`
- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-001_Entry_State.md`
- Sprint2 기준: `docs/Sprint2/User_FlowDB/FLOW_AI_CHAT/CHAT-008_Exit_Reentry.md`
- Sprint3 데모: `docs/Sprint3/Demo/DEMO_FLOW_AI_CHAT_IMPROVEMENTS.md`
- 마이크 버튼 상태 UX: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-C_Mic_Button_State_UX.md`
- final 자막 대화형 표시: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-D_Final_Subtitle_Conversation_UX.md`
- 음성 레벨 반응형 웨이브: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-E_Voice_Level_Wave_UX.md`
- AI 음성 재생 연속성: `docs/Sprint3/User_FlowDB/FLOW_AI_CHAT_IMPROVEMENTS/CHAT-FIX-001/CHAT-FIX-001-F_AI_Audio_Playback_Continuity.md`
- Reconnect 기준: `docs/System_FlowDB/SYS_REALTIME_INFRA/RT-004_Reconnect.md`

---

# 구현 범위

## 포함 범위

- Sprint2 AI Chat 기본 흐름에서 발견된 안정성/회귀성 수정
- 화면 회전 시 `stopChat()`에 의한 상태 초기화 방지
- 화면 회전 시 `enterChat()` 중복 실행 방어
- READY 상태의 `activeSessionId`와 `ChatUiState` 유지
- subtitle 상태와 마지막 자막 보존
- AI 응답 중 recomposition 안정화
- 실제 화면 이탈 또는 back stack 제거 시 cleanup 경계 확인
- 관심주제 미설정 사용자 진입 시 다이얼로그 표시 정책 유지
- 관심주제 선택 다이얼로그의 닫기/취소 우회 제거
- 관심주제 목록 스크롤 및 다이얼로그 높이 제약 적용
- 관심주제 저장 중 중복 클릭 방어
- 관심주제 5개 선택 전 안내 문구 표시 및 완료 버튼 비활성화
- 관심주제 5개 선택 후 완료 버튼 활성화 및 선택 안내 문구 제거
- 마이크 버튼의 녹음 시작/정지/응답 중 비활성 상태 정리
- 현재 화면 표시용 final 자막을 사용자/AI 순서대로 누적 표시
- 긴 final 자막을 자막 영역 내부 스크롤로 확인
- 사용자 입력과 AI 출력 상태를 음성 레벨 기반 웨이브 애니메이션으로 강화
- AI audio 수신부터 단말 재생 종료까지의 경계 확인
- AI audio가 순서대로 끝까지 재생되도록 `AudioPlayer` 재생 흐름 보강
- 로컬 재생 완료 전 마이크가 다시 활성화되지 않도록 AI speaking 상태 유지

## 제외 범위 (Out of Scope)

- `CHAT-FIX-001-C/D/E/F` 범위를 벗어나는 신규 UX 고도화
- prompt tuning 정책 구현
- 실시간 타이핑형 사용자/AI 자막 구현
- 장기 백그라운드 세션 유지
- 앱 프로세스 kill 이후 LiveSession 복원
- Firebase Live API 세션 자체의 영구 재사용
- Android multi-window 전체 대응
- 관심주제 추천 알고리즘 또는 추천 품질 개선
- 관심주제 개수 정책 변경
- 기존에 저장된 관심주제 편집 화면
- AI 응답 프롬프트에 관심주제를 반영하는 tune 작업
- 관심주제 미설정 상태에서 LiveSession 시작 자체를 차단하는 정책
- 음성 인식/합성 모델 변경
- OpenAI Realtime 설정 변경
- AI 응답 품질 또는 대화 내용 튜닝
- 사용자 발화 녹음 품질 개선
- 네트워크가 완전히 끊긴 상황의 자동 재생 복구

---

# Details

## 문서 확장 정책

- 이 문서는 Sprint2 AI Chat 구현을 대체하지 않는다.
- Sprint2 문서는 완료된 기준 흐름으로 보존하고, Sprint2 이후 발견된 수정 사항만 이 문서에 누적한다.
- 새 문제가 기존 작업 단위와 독립적이면 `CHAT-FIX-001-G`처럼 하위 작업 ID를 추가한다.
- 개별 구현/PR/백로그 이슈는 하위 작업 ID 단위로 작게 관리한다.

## 세션 상태 유지 정책

- 화면 회전은 configuration change로 보고, 사용자가 대화 화면을 떠난 것으로 간주하지 않는다.
- 이미 READY 상태이고 `activeSessionId`가 있으면 새 세션 시작을 시도하지 않는다.
- 자막 표시 여부와 마지막 자막은 ViewModel 상태로 유지한다.
- `DisposableEffect.onDispose`는 화면 회전, recomposition, navigation dispose에서 모두 호출될 수 있으므로 무조건 `stopChat()`을 호출하지 않는다.
- `Activity.isChangingConfigurations == true`인 dispose는 화면 회전으로 보고 세션과 UI 상태를 유지한다.
- `Activity.isChangingConfigurations != true`인 dispose는 실제 navigation 이탈로 보고 기존 Sprint2 정책대로 `stopChat()`을 호출해 녹음/재생/Live transport를 정리한다.
- `ChatViewModel.onCleared()`는 back stack 제거 또는 ViewModel 종료 시점의 마지막 cleanup 경계로 둔다.
- 사용자가 명시적으로 AI Chat을 종료하는 별도 UX가 생기면 그 이벤트에서만 `stopChat()`을 호출한다.

## 관심주제 다이얼로그 정책

- 다이얼로그 표시 여부는 사용자 프로필의 `interestTopics`가 비어 있는지 확인한 결과와 저장 성공 여부를 기준으로 한다.
- 관심주제 미설정은 세션 시작 자체를 막는 조건이 아니다. 다만 다이얼로그가 표시된 뒤에는 저장 성공 전까지 우회해서 닫을 수 없어야 한다.
- 관심주제는 정확히 5개를 선택해야 저장할 수 있다.
- 5개 미만이면 선택 안내 문구를 표시하고 완료 버튼은 비활성화한다.
- 5개를 채우면 완료 버튼이 활성화되고, 선택 안내 문구는 사라진다.
- 가로 화면처럼 세로 공간이 좁은 경우에도 목록을 스크롤해 모든 항목을 선택할 수 있어야 한다.

## 마이크 버튼 상태 UX 정책

- 이 작업은 OpenAI Realtime transport 전환 이후 남은 화면 UX 보강이다.
- 마이크 버튼은 사용자가 다음 행동을 예측할 수 있도록 같은 위치에서 상태만 바꾼다.
- 입력 가능 상태에서는 마이크 버튼을 표시한다.
- 녹음 중에는 같은 버튼을 정지 버튼으로 표시한다.
- AI 응답 중에는 마이크 버튼 형태를 유지하되 비활성화한다.
- 정지 버튼 입력 이후에는 user turn 종료와 사용자 발화 확정 대기 상태를 화면에서 이해할 수 있어야 한다.

## final 자막 대화형 표시 정책

- 사용자 발화와 AI 응답은 모두 final transcript 기준으로 화면에 표시한다.
- 사용자가 말하는 중 또는 AI가 말하는 중 글자가 실시간으로 타이핑되듯 출력되는 자막은 이번 범위에서 제외한다.
- 자막은 SessionMemory 전체 로그가 아니라 현재 Chat 화면의 최신 USER/AI final 확인용이다.
- 새 자막은 같은 역할의 이전 자막을 교체한다.
- 긴 자막은 말줄임 없이 표시하되, 중앙 음성 visual을 덮지 않도록 자막 영역 안에서 스크롤한다.
- 자막 영역이 스크롤 가능하면 상단 fade와 화살표 힌트로 위쪽 내용을 더 볼 수 있음을 알려준다.
- 화면 표시용 자막 리스트는 저장 정책이 아니며, SessionMemory 저장은 기존 final turn 기준을 유지한다.

## 음성 레벨 반응형 웨이브 정책

- 음성 반응 애니메이션은 실제 입력/출력 레벨을 더 잘 드러내는 시각 피드백이며, AI 응답 생성 정책이나 저장 정책을 바꾸지 않는다.
- 사용자 발화 중에는 microphone input level을 반영한다.
- AI 응답 중에는 output audio level을 반영한다.
- 기존 원형 계열은 유지하되, 가장자리가 물결처럼 유동적으로 변하는 wave 형태를 지향한다.

## AI 음성 재생 연속성 정책

- OpenAI Realtime의 AI 음성은 여러 개의 PCM audio chunk로 나누어 도착한다.
- 사용자가 듣는 음성 품질은 transport 수신 성공만으로 보장되지 않고, 로컬 `AudioPlayer`가 chunk를 순서대로 끝까지 재생해야 보장된다.
- `response.done`은 서버 응답 생성 완료 신호이지, 단말 스피커에서 마지막 chunk 재생이 끝났다는 신호가 아니다.
- 재생 완료 판단은 서버 done 이벤트보다 단말의 로컬 재생 상태를 우선해 확인한다.
- audio chunk가 일부라도 누락되면 사용자는 "말이 점프된다"거나 "단어가 빠진다"는 형태로 체감할 수 있다.
- 이번 작업은 OpenAI 모델이나 음성 포맷을 바꾸기보다, 현재 재생 경로에서 앱의 재생 안정성을 먼저 검증하고 보강한다.

## 기대 흐름

```text
AI Chat 진입
→ AI Chat 세션 준비 진행
→ interestTopics 비어 있음 확인
→ AI Chat READY 상태에서 관심주제 선택 다이얼로그 표시
→ 사용자가 주제 선택
→ 화면 회전
→ 선택 항목 유지
→ 목록 스크롤로 남은 항목 선택 가능
→ 5개 미만이면 안내 문구 표시 및 완료 버튼 비활성화
→ 5개 선택 후 저장
→ 관심주제 다이얼로그 닫힘
→ 화면 회전
→ 기존 ViewModel 상태 유지
→ stopChat 미호출
→ 새 LiveSession 생성 없음
→ 자막/버튼/AI 상태 유지
→ 대화 계속 가능
```

---

# 구현 메모

- `ChatScreen`의 `LaunchedEffect(Unit)`가 회전 후 호출되더라도 `ChatViewModel.enterChat()`에서 중복 시작을 방어한다.
- `ChatScreen`의 `DisposableEffect`에서는 `Activity.isChangingConfigurations`를 확인한 뒤, 화면 회전이 아닌 dispose에서만 `stopChat()`을 호출한다.
- `ChatViewModel.onCleared()`는 back stack 제거 또는 ViewModel 종료 시점에 남은 리소스를 정리하는 최종 방어선으로 사용한다.
- 관심주제 저장은 기존 `SaveInterestTopicsUseCase` 계약을 그대로 사용한다.
- 공통 `UmmaDialog`는 다른 화면에서도 사용 중이므로 기존 호출부를 깨지 않는 optional parameter로 확장한다.
- 관심주제 다이얼로그에는 취소 아이콘 숨김, back/outside dismiss 방지, 스크롤 가능한 목록, 완료 버튼 활성화 조건을 적용한다.
- `AudioPlayer`는 수신된 audio chunk를 버리지 않고 순서대로 재생하는 책임을 갖는다.
- `ChatViewModel`은 로컬 출력이 끝나기 전까지 사용자가 새 turn을 시작하지 못하도록 기존 `isAudioOutputPlaying` 기준을 유지한다.
- `ChatRepositoryImpl`은 audio delta를 앱 이벤트로 전달하는 transport 책임만 갖고, 로컬 재생 큐 정책을 판단하지 않는다.

---

# 검증 기준

## CHAT-FIX-001-A

- READY 상태에서 회전해도 Loading부터 다시 보이지 않는다.
- 회전 후 `activeSessionId`가 유지된다.
- subtitle visible 상태와 마지막 자막이 유지된다.
- AI 응답 중 회전해도 응답 상태가 중복 초기화되지 않는다.
- AI Chat back stack이 제거되면 녹음과 재생이 정리된다.

## CHAT-FIX-001-B

- 관심주제 미설정 계정으로 AI Chat 진입 시 다이얼로그가 표시된다.
- 다이얼로그에서 취소/닫기 UI가 보이지 않는다.
- 외부 터치나 뒤로가기로 다이얼로그가 닫히지 않는다.
- 화면 회전 후에도 선택한 관심주제가 유지된다.
- 가로 화면에서도 목록을 스크롤해 모든 주제를 확인하고 선택할 수 있다.
- 5개 미만이면 안내 문구가 표시되고 완료 버튼이 비활성화된다.
- 5개 선택 시 완료 버튼이 활성화되고 선택 안내 문구가 사라진다.
- 5개 저장 성공 후 다이얼로그가 닫힌다.

## CHAT-FIX-001-C

- 입력 가능 상태에서 마이크 버튼을 누르면 녹음이 시작된다.
- 녹음 중 버튼은 정지 버튼으로 보여서 다시 누르면 발화가 종료됨을 알 수 있다.
- AI 응답 중 마이크 버튼은 비활성화되어 새 입력이 시작되지 않는다.

## CHAT-FIX-001-D

- 사용자 final 자막과 AI final 자막이 역할별 최신 말풍선으로 표시된다.
- 사용자/AI 자막은 역할이 구분되어 보인다.
- 실시간 타이핑 자막이 아니라 final 자막 기준으로 표시된다.
- 긴 자막은 말줄임 없이 자막 영역 안에서 스크롤로 확인된다.

## CHAT-FIX-001-E

- 사용자 입력 애니메이션이 음성 레벨 변화에 따라 더 크게 또는 더 강하게 반응한다.
- AI 출력 애니메이션이 음성 레벨 변화에 따라 더 크게 또는 더 강하게 반응한다.
- 원형 가장자리가 완전한 동심원 반복이 아니라 wave 형태로 유동적으로 움직인다.
- 음성 레벨이 작거나 없으면 과한 움직임 없이 idle 상태로 돌아온다.
- AI 응답 중 마이크 버튼 비활성화 상태가 유지된다.
- final 자막 영역과 중앙 wave가 서로 읽기 어렵게 겹치지 않는다.

## CHAT-FIX-001-F

- 짧은 AI 응답과 긴 AI 응답을 각각 재생했을 때 단어가 중간에 잘려 들리지 않는다.
- Logcat에서 audio 수신, playback 시작, playback 완료 경계를 확인할 수 있다.
- 마지막 audio chunk 재생이 끝나기 전에는 마이크 버튼이 활성화되지 않는다.
- AI 음성 재생 완료 후에는 output level이 0으로 돌아가고 마이크 버튼이 다시 활성화된다.
- 재생 안정성 수정 후에도 final 자막과 usage 기록은 기존처럼 남는다.

---

# Edge Cases

- 세션 Loading 중 화면 회전
- Recording 중 화면 회전
- AI Speaking 중 화면 회전
- final turn 저장 중 화면 회전
- 화면 회전 직후 뒤로가기
- selected language 변경 후 재진입
- 관심주제 저장 중 화면 회전
- 관심주제 4개 선택 후 저장 시도
- 관심주제 5개 선택 후 일부 선택 해제
- 가로 화면에서 다이얼로그 진입
- 프로필 로딩 완료 전 화면 회전
- 녹음 시작 직후 즉시 정지
- 사용자 final 자막이 늦게 도착함
- AI 응답 중 마이크 버튼 연속 클릭
- 긴 사용자/AI 자막이 여러 줄로 표시됨
- 입력/출력 음성 레벨이 매우 작거나 거의 0에 가까움
- AI audio chunk가 짧은 간격으로 연속 도착함
- 서버 `response.done`이 로컬 재생 완료보다 먼저 도착함
- AI 음성 재생 중 화면 이탈 또는 화면 회전
