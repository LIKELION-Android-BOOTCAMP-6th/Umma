# SYS-WATCH-INFRA

## 1. 목표 및 범위

- 목표: Umma의 Wear OS 연동 V1을 `폰 중심 Companion 아키텍처`로 정의하고, 워치 AI 대화와 알림 확장의 책임 경계 및 운영 정책을 SSOT로 고정한다.
- 성공 조건: 구현자가 이 문서만 보고도 워치 앱, 폰 브릿지, 백그라운드 유지 정책, 알림 경로, UX 범위, 제외 범위를 임의 판단 없이 구현할 수 있다.

V1의 제품 역할은 다음과 같이 고정한다.

- 워치는 `독립 AI 클라이언트`가 아니라 `폰의 확장 UI + 원격 입력 장치`다.
- 폰은 인증, OpenAI Realtime token 발급, WebSocket transport, usage sync, Session Memory, notification device 등록의 유일한 소유자다.
- 워치는 `원격 마이크 + 경량 채팅 UI + 알림 액션 표면`만 담당한다.

---

## 2. 주요 단계

| 단계 | 주요 구현 내용 | 시스템 반응 | 성공/실패 분기 | 관리 이슈 ID |
| --- | --- | --- | --- | --- |
| 1. 워치 연동 기준 문서화 | Companion only 정책, 브릿지 계약, V1 포함/제외 범위 정의 | 후속 구현 이슈가 동일한 제품 가정 위에서 진행됨 | 성공: 정책 고정 / 실패: 구현자별 해석 분기 발생 | WATCH-001 |
| 2. Wear 모듈 및 브릿지 계약 스캐폴딩 | `:wear` 모듈, shared contract, command/event 타입 추가 | 워치-폰 통신 경계가 코드 구조로 분리됨 | 성공: 계약 고정 / 실패: 워치 구현이 폰 domain과 과결합 | WATCH-002 |
| 3. 폰 소유 워치 AI 브릿지 | 워치 command를 기존 chat use case에 연결하고 AIEvent를 워치 상태로 변환 | 워치 입력이 기존 AI Chat 파이프라인으로 흘러감 | 성공: 기존 chat stack 재사용 / 실패: 세션 소유권 분산 | WATCH-003 |
| 4. 활성 대화용 Foreground Service | 워치 active chat 동안만 폰에서 마이크/네트워크 리소스 유지 | 화면 off 상태에서도 워치 대화 지속 | 성공: active chat 지속 / 실패: 세션 중단 또는 과도한 백그라운드 점유 | WATCH-004 |
| 5. 워치 채팅 UX 및 알림 액션 | PTT, replay, history 펼치기, watch review/snooze/open-on-phone 구현 | 워치에서 짧고 반복 가능한 학습 루프 제공 | 성공: 빠른 재진입 / 실패: UI 과밀, 개인정보 과노출 | WATCH-005 ~ WATCH-008 |

---

## 3. 핵심 정책

### 3-1. 제품 역할 정책

- V1의 워치 운영 모드는 `Companion only`다.
- 워치는 폰 근처 사용을 전제로 하며, direct cloud fallback을 제공하지 않는다.
- 워치가 폰과 분리되면 AI 대화는 종료하거나 복구 대기 상태로 전환한다.
- V1에서 standalone watch product 가정은 두지 않는다.

### 3-2. 세션 소유권 정책

- AI 세션의 소유자는 폰만 허용한다.
- 워치는 세션 생성, 세션 재연결, turn commit, usage sync, Session Memory append를 직접 수행하지 않는다.
- 워치는 command를 전송하고, 폰은 기존 `StartSessionUseCase`, `SendAudioDataUseCase`, `EndUserTurnUseCase`, `RetryConnectionUseCase`, `StopSessionUseCase`를 호출한다.
- `ChatRepository`의 책임은 변경하지 않고 폰 소유로 유지한다.

### 3-3. 백그라운드 유지 정책

- 워치 대화 중 폰 화면이 꺼져도 active chat은 유지한다.
- 이를 위해 `활성 대화 중에만` 폰 Foreground Service를 올린다.
- Foreground Service는 아래 조건 중 하나라도 참이면 유지한다.
  - 워치가 현재 녹음 중
  - AI가 thinking 상태
  - AI가 speaking 상태
  - 워치 채팅 화면이 열려 있고 idle timeout이 지나지 않음
- 마지막 AI 응답 이후 30초 동안 추가 입력/재생/재시도 이벤트가 없으면 서비스를 종료한다.
- 유휴 상태 장시간 유지용 상시 서비스는 허용하지 않는다.

### 3-4. 알림 경로 정책

- V1 알림의 소유권은 전부 폰에 둔다.
- 알림은 기존 폰 FCM 및 로컬 알림을 기준으로 생성한다.
- 워치는 시스템 notification mirroring으로 알림을 받는다.
- V1에서 watch-specific FCM token 저장 및 direct watch push는 구현하지 않는다.
- 워치 액션은 `SRS 알림`에만 우선 제공한다.

### 3-5. 워치 대화 UX 정책

- 주 입력 방식은 `PTT`다.
- V1에서 지원하는 입력은 `워치 물리 버튼 + 화면 PTT`다.
- 이어폰 media button PTT는 V1에서 제외한다.
- 워치 메인 화면에는 partial subtitle을 노출하지 않는다.
- 대화는 `오디오 중심`으로 제공하되, 텍스트는 기본 숨김 후 최근 이력 펼치기로만 노출한다.
- AI 음성은 자동 재생하지 않고 사용자가 직접 탭해 재생한다.
- 최근 이력은 finalized turn만 사용한다.
- 최근 이력은 기본 접힘 상태이며, 펼치면 최근 4개 turn까지만 보여준다.
- turn당 노출 텍스트는 160자 이내로 절삭한다.

### 3-6. 개인정보 정책

- partial transcript는 워치에 보내지 않는다.
- 워치 lock 상태 또는 민감도 판단 불가 상태에서는 full transcript를 노출하지 않는다.
- 워치 이력 및 알림 문구는 요약/절삭을 기본값으로 한다.
- direct watch push를 하지 않더라도 미러링 알림에서 과도한 본문 노출이 없도록 body 구성 자체를 짧게 유지한다.

---

## 4. 책임 경계

### 폰이 담당하는 범위

- 사용자 인증 상태 확인
- Firebase ID token 기반 Realtime token 발급
- OpenAI Realtime WebSocket 연결 및 재연결
- 워치에서 전달한 오디오 frame 처리
- USER turn commit 및 AI response lifecycle 처리
- Session Memory append 및 후속 correction/flashcard/statistics 연계
- usage sync
- notification device 등록 및 notification scheduling
- watch command 수신 및 watch state event 송신
- active watch chat foreground service 운영

### 워치가 담당하는 범위

- 워치 채팅 진입점 제공
- 물리 버튼 및 화면 PTT 입력
- 녹음 시작/종료 command 전달
- 폰 상태를 watch UI 상태로 렌더링
- replay, history expand/collapse, open-on-phone CTA 제공
- SRS 알림 액션 진입 처리

### V1 범위 밖

- 워치 독립 AI 세션
- 워치 자체 Realtime token 발급
- 워치 FCM token 저장/직접 push
- direct cloud fallback
- Tile/Complication
- 이어폰 media button PTT
- 긴 transcript UI
- AI 음성 자동 재생

---

## 5. 브릿지 계약 기준

### 전송 수단

- `MessageClient`: 제어 명령, RPC성 요청
- `ChannelClient`: 워치 오디오 업로드 및 AI 오디오 다운로드
- `DataClient`: V1 실시간 채팅 transport에는 사용하지 않음

### 워치 → 폰 command 기준

- `OpenWatchChat`
- `StartWatchChatSession`
- `PressPtt`
- `ReleasePtt`
- `CancelCurrentTurn`
- `ReplayLastAiAudio`
- `ExpandHistory`
- `CollapseHistory`
- `OpenOnPhone`
- `NotificationActionReview`
- `NotificationActionSnooze10m`

### 폰 → 워치 상태/event 기준

- `BridgeUnavailable`
- `PhoneConnecting`
- `ChatReady`
- `Recording`
- `AwaitingTranscript`
- `Thinking`
- `Speaking`
- `Idle`
- `RecoverableError`
- `TerminalError`
- `RecentHistorySnapshot`
- `LastAiAudioAvailable`
- `SessionClosed`

### 워치 상태 payload 최소 기준

- active session id
- current learning language
- current AI state
- replay 가능 여부
- 최근 finalized turn 목록
- recoverable/terminal error code
- phone 연결 가능 여부

---

## 6. V1 사용자 흐름

```text
워치에서 AI Chat 진입
-> 폰 연결 가능 여부 확인
-> watch command로 phone session 준비
-> 워치에서 PTT press
-> 워치 오디오 frame을 phone으로 전달
-> phone이 기존 realtime transport로 AI 전송
-> PTT release 시 phone이 USER turn commit
-> phone이 AIEvent를 watch state로 변환
-> 워치에서 상태 표시 및 필요 시 replay
-> 사용자가 길게 이어가고 싶으면 Open on Phone
```

```text
폰에서 SRS 알림 생성
-> 워치로 notification mirroring
-> 사용자가 워치 액션 선택
-> watch action command가 phone에 전달
-> phone이 review 진입 또는 snooze reschedule 수행
```

---

## 7. 후속 GitHub Issue 맵

### 설계 및 구조

- `WATCH-001` 워치 연동 설계서 SSOT 작성
- `WATCH-002` Wear 모듈 및 워치-폰 브릿지 계약 스캐폴딩

### AI 브릿지 및 백그라운드

- `WATCH-003` 폰 측 워치 AI 채팅 브릿지 오케스트레이션 구현
- `WATCH-004` 워치 AI 채팅용 Foreground Service 및 화면 꺼짐 지속 정책 구현
- `WATCH-005` 워치 오디오 업로드/다운로드 브릿지 구현

### 워치 UX 및 개인정보

- `WATCH-006` 워치 채팅 UI 및 PTT 인터랙션 구현
- `WATCH-007` 워치 대화 이력/프라이버시 정책 반영

### 알림 및 안정성

- `WATCH-008` SRS 알림 워치 액션 및 폰 핸드오프 구현
- `WATCH-009` 워치 연결 실패/복구 및 안정성 검증

---

## 8. 예외 상황 및 처리 기준

### 1. 폰 연결 끊김

- 워치가 폰과 분리되면 direct cloud fallback을 시도하지 않는다.
- 세션 중이라면 `RecoverableError` 또는 `TerminalError`로 전환한다.
- 워치에는 재시도 또는 open-on-phone CTA를 노출한다.

### 2. 중복 세션 시작

- 워치가 중복으로 `StartWatchChatSession`을 보내더라도 폰은 기존 active session을 우선 재사용한다.
- 새 session 생성은 기존 active session이 유효하지 않을 때만 허용한다.

### 3. idle timeout 직전 재입력

- Foreground Service 종료 직전 새 PTT 입력이 들어오면 idle 종료를 취소하고 active 상태로 복귀한다.

### 4. replay 가능 오디오 없음

- AI response는 끝났지만 replay 가능한 마지막 음성이 없을 수 있다.
- 이 경우 replay 버튼은 숨기거나 비활성화하고 상태 문구만 유지한다.

### 5. due card 소진

- 워치 알림에서 `워치에서 복습`을 눌렀더라도 이미 due card가 소진됐을 수 있다.
- 이 경우 empty review 상태를 보여주고 폰에서 열기 CTA를 제공한다.

---

## 9. 연결 문서

- [SYS_REALTIME_INFRA.md](./SYS_REALTIME_INFRA.md)
- [RT-002_Stream.md](./SYS_REALTIME_INFRA/RT-002_Stream.md)
- [RT-004_Reconnect.md](./SYS_REALTIME_INFRA/RT-004_Reconnect.md)
- [FLOW_AI_CHAT.md](../Sprint2/User_FlowDB/FLOW_AI_CHAT.md)
- [FLOW_SRS.md](../Sprint2/User_FlowDB/FLOW_SRS.md)
- [06_srs_review_pipeline.drawio](../drawio/06_srs_review_pipeline.drawio)

