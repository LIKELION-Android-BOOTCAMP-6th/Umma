# [Infra] LS-005 Local Cache & Sync Policy

## User Story

개발자는 Dashboard, AI Chat, Correction, Flashcard, Statistics가 빠르게 동작하고 Firebase 비용을 과도하게 증가시키지 않도록,
학습 상태 데이터의 Local Cache, Local Persist, Firebase Sync 정책을 일관되게 정의할 수 있다.

---

# 완료 기준(AC) (Acceptance Criteria)

- [ ] 학습 상태 데이터별 저장 위치가 정의된다.
- [ ] User Learning Preference 저장 정책이 정의된다.
- [ ] Language State 저장 정책이 정의된다.
- [ ] Dashboard Summary 저장 정책이 정의된다.
- [ ] Session Memory 저장 정책이 정의된다.
- [ ] Flashcard 저장 정책이 정의된다.
- [ ] Statistics 저장 정책이 정의된다.
- [ ] 앱 시작 시 preload 순서가 정의된다.
- [ ] Local-first 렌더링 정책이 정의된다.
- [ ] Firebase background sync 정책이 정의된다.
- [ ] 대화 중 Firebase 실시간 write 금지 원칙이 정의된다.
- [ ] turn 확정 후 로컬 반영 및 batch sync 정책이 정의된다.
- [ ] sync 실패 시 fallback / retry 정책이 정의된다.
- [ ] 로그아웃 시 인메모리 상태 초기화와 영구 데이터 유지 범위가 정의된다.
- [ ] 충돌 상황에서 어느 데이터를 우선할지 정책이 정의된다.

---

# Flow (링크)

- SYS-LEARNING-STATE-INFRA
- LS-001 → Language State Model Structure
- LS-002 → Dashboard Summary Model
- LS-003 → User Learning Preference Model
- LS-004 → Global Learning State Store
- LS-005 → Local Cache & Sync Policy
- AUTH-002 → 앱 진입 및 자동 로그인 처리
- AUTH-003 → 로그아웃
- AUTH-004 → 초기 사용자 설정
- DASH-001 → Dashboard preload 및 Summary fetch
- DASH-006 → Dashboard 학습 언어 selector 및 selectedLearningLanguage 변경

---

# 구현 범위

## 포함 범위

- 데이터별 저장 위치 정의
- Local Cache / Local Persist / Firebase 역할 정의
- 앱 시작 preload 순서 정의
- Dashboard local-first 렌더링 정책 정의
- Firebase background sync 정책 정의
- Session Memory batch sync 정책 정의
- 동기화 실패 및 재시도 정책 정의
- 로그아웃 시 상태 초기화 정책 정의
- 충돌 해결 정책 정의

---

## 제외 범위 (Out of Scope)

- 각 모델 상세 정의 (LS-001~004)
- Language State 업데이트 알고리즘 구현 (LS-006)
- 실제 Room Entity / DAO 구현
- 실제 DataStore key 구현
- 실제 Firestore DataSource 구현
- 실제 WorkManager / Retry Worker 구현
- AI Chat streaming 구현
- Dashboard / Flashcard / Statistics UI 구현

> LS-005는 “어떤 데이터를 어디에 저장하고 어떤 순서로 동기화할 것인가”를 정의한다.
> 실제 DAO, DataSource, Worker 구현은 개별 구현 이슈에서 다룬다.

---

# Details

## 핵심 원칙

### 1. Local-first

읽기 속도가 중요한 화면은 Local 데이터를 먼저 사용한다.

```text
Local Cache / Persist
→ 즉시 UI 렌더링
→ Firebase background sync
→ 변경사항 존재 시 Store 갱신
```

Dashboard는 반드시 이 원칙을 따른다.

---

### 2. Firebase는 원본 또는 동기화 대상

Firebase는 사용자 계정 간 동기화와 장기 저장을 담당한다.
하지만 모든 화면이 Firebase 응답을 기다리면 UX가 느려진다.

따라서:

- 앱 시작 시 Local preload 우선
- Firebase sync는 background 수행
- 실패 시 Local fallback 유지

---

### 3. 대화 중 실시간 Firebase write 금지

AI Chat 중 streaming chunk를 Firebase에 매번 write하지 않는다.

```text
Realtime streaming chunk
→ memory buffer
→ 사용자 발화/AI 응답 완료
→ turn 확정
→ local 반영
→ batch sync
```

---

### 4. Summary 기반 렌더링

Dashboard는 원본 데이터를 직접 계산하지 않는다.

- `recentFullContext` 전체 조회 금지
- Flashcard 전체 목록 조회 금지
- Language State Internal Metrics 전체 렌더링 금지

대신 `DashboardSummary[selectedLearningLanguage]`를 사용한다.

---

# 데이터별 저장 정책

| 데이터 | 언어별 저장 | 메모리 | Local 저장 | Firebase 저장 | 정책 |
| --- | --- | --- | --- | --- | --- |
| User Learning Preference | X | O | DataStore | Firestore | Local persist + Firebase sync |
| Language State | O | O | DataStore | Firestore | Local persist + Firebase sync |
| Dashboard Summary | O | O | DataStore | Firestore | Local cache + Firebase sync |
| Session Memory | O | O | Room | Firestore | turn 확정 후 local append + batch sync |
| Flashcard | O | O | Room | Firestore | local first + async sync |
| Statistics | O | O | Room 또는 DataStore | Firestore | Firebase fetch + local cache |

---

## 1. User Learning Preference

### 저장 위치

```text
Local: DataStore
Remote: users/{uid}/user_learning_preference/current
```

### 정책

- 앱 시작 시 가장 먼저 Local preload한다.
- `selectedLearningLanguage` 확인에 사용한다.
- Firebase sync는 background로 수행한다.
- 언어 변경 시 Local을 먼저 갱신하고 UI를 즉시 반영한다.
- Firebase 저장 실패 시 Local 값은 유지하고 재시도한다.

---

## 2. Language State

### 저장 위치

```text
Local: DataStore
Remote: users/{uid}/language_states/{language}
```

### 정책

- 언어별로 저장한다.
- AI Chat, Correction, Statistics에서 참조한다.
- 대화 중 매 turn마다 업데이트하지 않는다.
- 교정 또는 학습 분석 완료 후 batch update한다.
- Local 값을 먼저 사용하고 Firebase sync로 최신화한다.

---

## 3. Dashboard Summary

### 저장 위치

```text
Local: DataStore
Remote: users/{uid}/dashboard_summaries/{language}
```

### 정책

- Dashboard preload의 핵심 데이터이다.
- Local Summary가 오래되었더라도 우선 렌더링한다.
- Firebase background sync 후 변경사항이 있으면 UI를 갱신한다.
- Dashboard는 Summary 외 원본 데이터를 조회하지 않는다.

---

## 4. Session Memory

### 저장 위치

```text
Local: Room
Remote: users/{uid}/sessions/{language}
```

### 정책

Session Memory는 대화 1회마다 새 문서를 생성하지 않고,
사용자와 학습 언어 기준으로 재사용한다.

```text
users/{uid}/sessions/en
└── language: "en"

users/{uid}/sessions/ja
└── language: "ja"
```

대화 중:

```text
streaming chunk 수신
→ memory buffer 조립
→ 발화 또는 응답 완료
→ turn 확정
→ Room에 append
→ Firebase batch sync 예약
```

교정 및 Flashcard 저장 완료 후:

```text
recentFullContext 압축
→ topicSummaries / topicKeySentences 갱신
→ recentFullContext 초기화
→ correctionAvailable = false
→ Dashboard Summary 갱신
```

조회 정책:

- 현재 선택 언어의 Session Memory는 `users/{uid}/sessions/{selectedLearningLanguage}`로 조회한다.
- Dashboard는 Session Memory 원문을 직접 조회하지 않고 `DashboardSummary` / `SessionSummary`만 사용한다.
- `recentFullContext`는 Correction 진입 또는 교정 생성 시점에만 필요한 범위로 조회한다.

---

## 5. Flashcard

### 저장 위치

```text
Local: Room
Remote: users/{uid}/flashcards/{cardId}
```

### 정책

- Flashcard 학습은 반응 속도가 중요하므로 local first로 처리한다.
- 복습 결과는 즉시 Room에 반영한다.
- Firebase sync는 비동기로 수행한다.
- `dueFlashcards`, `recentSavedFlashcards`는 Dashboard Summary에 별도 반영한다.

---

## 6. Statistics

### 저장 위치

```text
Local: Room 또는 DataStore
Remote: Firestore
```

### 정책

- Statistics는 Dashboard보다 preload 우선순위가 낮다.
- 상세 통계 화면 진입 시 fetch 가능하다.
- MVP에서는 `updatedAt` 기준으로 최신 여부를 판단한다.
- TTL 기반 캐시 만료 정책은 Phase 2에서 검토한다.
- Dashboard는 Statistics 전체 히스토리를 직접 조회하지 않는다.

---

# 앱 시작 preload 순서

```text
앱 실행
→ Auth 상태 확인
→ 로그인된 사용자 확인
→ UserLearningPreference Local preload
→ selectedLearningLanguage 확인
→ DashboardSummary[selectedLearningLanguage] Local preload
→ LanguageState[selectedLearningLanguage] Local preload
→ GlobalLearningState 갱신
→ Dashboard 즉시 렌더링
→ Firebase background sync
→ 변경사항 존재 시 Store/UI 갱신
```

AUTH-002는 인증 세션 확인만 담당한다.
학습 상태 preload는 Dashboard / Learning State Flow의 책임이다.

---

# Dashboard 진입 sync 흐름

```text
Dashboard 진입
→ UserLearningPreference 확인
→ selectedLearningLanguage 확인
→ Local Dashboard Summary 조회
→ Content 또는 Empty Dashboard 렌더링
→ Firebase Summary fetch
→ 최신 데이터가 있으면 Local / Store 갱신
→ Dashboard 카드 재렌더링
```

---

# 언어 변경 sync 흐름

```text
언어 선택
→ selectedLearningLanguage Local update
→ GlobalLearningState 갱신
→ 선택 언어 Dashboard Summary Local fetch
→ Dashboard 재렌더링
→ UserLearningPreference Firebase sync
→ 선택 언어 Dashboard Summary Firebase sync
```

실패 시:

```text
Firebase sync 실패
→ Local selectedLearningLanguage 유지
→ Snackbar 또는 non-blocking error 표시
→ 다음 sync 시 재시도
```

---

# 쓰기 정책

## 즉시 Local 반영

다음 작업은 사용자 경험을 위해 Local에 먼저 반영한다.

- selectedLearningLanguage 변경
- Flashcard 복습 결과
- Flashcard 저장
- turn 확정 후 Session Memory append
- Dashboard Summary 임시 갱신

---

## Firebase batch sync

다음 작업은 batch sync로 묶어서 수행할 수 있다.

- Session Memory turn append
- Dashboard Summary 갱신
- Language State 업데이트
- Flashcard 저장 / 복습 결과 sync

---

## 실시간 write 금지 대상

다음 데이터는 매 chunk마다 Firebase write하지 않는다.

- AI audio streaming chunk
- AI text streaming delta
- 녹음 중 임시 buffer
- 아직 확정되지 않은 transcript

---

# 충돌 해결 정책

## 기본 기준

충돌 발생 시 `updatedAt`이 더 최신인 데이터를 우선한다.

```text
Local updatedAt > Remote updatedAt
→ Local 유지 후 Firebase sync 재시도

Remote updatedAt > Local updatedAt
→ Remote 값으로 Local 갱신
```

---

## 예외

### selectedLearningLanguage

여러 기기에서 서로 다른 언어를 선택했을 수 있다.
MVP에서는 최신 `updatedAt` 기준을 따른다.

### Flashcard review result

복습 결과는 중복 반영되면 안 된다.
추후 `reviewEventId` 또는 `lastReviewedAt` 기반 idempotent update를 고려한다.

### Session Memory

turn append는 `turnId` 또는 `createdAt` 기준으로 중복 제거한다.

동일 세션 안에서 같은 `turnId`가 이미 존재하면 재시도 결과로 보고 추가 저장하지 않는다.

---

# 실패 및 재시도 정책

## Firebase sync 실패

```text
Local update 성공
Firebase sync 실패
→ pending sync 상태 기록
→ UI는 Local 값 유지
→ Snackbar 또는 non-blocking error 표시
→ 다음 앱 실행 또는 네트워크 복구 시 재시도
```

---

## Local 저장 실패

Local 저장 실패는 사용자 경험에 직접 영향을 준다.

```text
Local write 실패
→ 해당 작업 실패 처리
→ Error UI 또는 Snackbar 표시
→ Firebase write만 단독 수행하지 않음
```

---

## 앱 종료 중 sync 미완료

```text
pending sync 기록
→ 앱 재시작 시 pending sync 재시도
```

pending sync 구현 방식은 실제 구현 이슈에서 확정한다.

---

# 로그아웃 정책

로그아웃 시:

```text
Firebase / Google session 제거
→ GlobalLearningState clear
→ UserLearningPreference in-memory clear
→ DashboardSummary in-memory clear
→ CurrentSessionMemory in-memory clear
→ Onboarding route 이동
```

유지 데이터:

- Firebase에 저장된 User Profile
- Firebase에 저장된 UserLearningPreference
- Firebase에 저장된 Language State
- Firebase에 저장된 Flashcard
- Firebase에 저장된 Statistics
- Firebase에 저장된 Session Memory

MVP에서는 로그아웃 시 Local persist 데이터를 즉시 삭제할지 여부를 팀 정책으로 결정한다.
단, 다른 사용자가 같은 기기에서 로그인했을 때 이전 사용자 데이터가 UI에 노출되면 안 된다.

---

# 상태 정책

## Loading

- Local preload 중 최소한의 Loading 또는 Splash 상태를 표시할 수 있다.
- Dashboard Summary가 Local에 있으면 Loading 없이 즉시 Content를 표시한다.

## Error

### Fatal

- UserLearningPreference Local / Remote 모두 없음
- selectedLearningLanguage 복구 실패
- Local 저장 실패
- 지원하지 않는 schemaVersion

→ Initial Setup 필요 또는 Error UI로 분기한다.

### Transient

- Firebase background sync 실패
- 일부 언어 Summary fetch 실패
- Statistics fetch 실패

→ Local fallback 유지 후 Snackbar로 안내한다.

## Empty

신규 사용자 또는 해당 언어 데이터가 아직 없는 경우 Empty Summary / Initial State를 생성할 수 있다.

---

# Edge Cases

- Local Cache 손상
- Firebase fetch 실패
- Firebase write 실패
- Local write 실패
- 앱 종료 중 pending sync 발생
- 네트워크 복구 후 중복 sync
- 여러 기기에서 같은 데이터 수정
- selectedLearningLanguage 변경 직후 앱 종료
- selectedLearningLanguage는 변경됐지만 해당 언어 Summary가 아직 없음
- Session Memory turn 중복 저장
- Flashcard review result 중복 반영
- schemaVersion 불일치
- 로그아웃 후 이전 사용자 Local Cache가 노출됨
- Firebase에는 데이터가 있으나 Local에는 없음
- Local에는 데이터가 있으나 Firebase에는 없음

---

# 테스트 시나리오

## 앱 시작 정상 흐름

1. 로그인된 사용자로 앱 실행
2. UserLearningPreference Local preload
3. selectedLearningLanguage 확인
4. Dashboard Summary Local preload
5. Dashboard 즉시 렌더링
6. Firebase background sync 수행
7. 변경사항이 있으면 UI 갱신

---

## 오프라인 흐름

1. 네트워크 끊김
2. 앱 실행
3. Local UserLearningPreference 로드
4. Local Dashboard Summary 로드
5. Dashboard 렌더링
6. Firebase sync 실패는 non-blocking error로 처리

---

## 언어 변경 흐름

1. Dashboard에서 학습 언어 변경
2. selectedLearningLanguage Local update
3. Dashboard 즉시 변경 언어 기준으로 재렌더링
4. Firebase sync 실패
5. Local 값 유지 및 재시도 pending 처리

---

## 대화 turn 저장 흐름

1. AI Chat streaming chunk 수신
2. memory buffer에만 저장
3. 사용자 발화 완료
4. turn 확정
5. Room Session Memory에 append
6. Firebase batch sync 예약

---

## 로그아웃 흐름

1. 로그인된 사용자 상태에서 로그아웃
2. 세션 제거
3. GlobalLearningState clear
4. Onboarding 이동
5. 이전 사용자 Dashboard Summary가 UI에 남지 않음 확인

---

# Related

## PR

- PR: #

---

## API / SDK

- Firebase Firestore
- DataStore
- Room
- Kotlin Flow / StateFlow
- Hilt

---

## Design(Figma)

해당 없음.

LS-005는 화면 UI가 아니라 저장 및 동기화 정책 정의 이슈이다.

---

# Labels

```text
type: infra
domain: learning-state
priority: high
sprint: week1
```
