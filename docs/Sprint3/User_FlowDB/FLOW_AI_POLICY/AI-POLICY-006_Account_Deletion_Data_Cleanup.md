# [Improvement] AI-POLICY-006 계정 삭제 데이터 정리

## 목적

Google Play 계정 삭제 요구사항은 "사용자가 계정을 삭제할 수 있다"에서 끝나지 않는다.
계정에 연결된 사용자 데이터가 실제로 어디까지 삭제되거나 비식별화되는지, 그리고 운영상 보관해야 하는 예외가 무엇인지 코드와 정책 문서가 일치해야 한다.

현재 Umma의 `deleteAccount` Cloud Function은 `users/{uid}` 문서와 그 하위 데이터를 삭제하고 Firebase Auth 계정을 삭제하는 흐름을 갖고 있다.
하지만 운영용 AI 신고처럼 루트 컬렉션에 저장되는 데이터는 별도 정책이 필요하고, 신고 저장 시점의 보존 만료 시각도 서버 기준으로 고정할 필요가 있다.

이 작업은 Play Console 제출 직전에 [AI-POLICY-003 약관·개인정보·신고 운영 정책 정리](./AI-POLICY-003_Legal_Disclosure_and_Operations.md)에 적을 계정 삭제 설명이 실제 코드 동작과 어긋나지 않도록 삭제/비식별화 범위를 확정한다.

---

# User Story

사용자는 계정 삭제를 요청했을 때 본인의 학습 데이터가 어떤 기준으로 삭제되는지 이해할 수 있다.
운영자는 AI 신고와 보안 로그처럼 운영상 검토가 필요한 데이터를 삭제할지, 비식별화할지, 보관할지 일관된 기준으로 처리할 수 있다.
Google Play 심사자는 앱 내 삭제 경로와 공개 삭제 URL의 설명이 실제 데이터 처리와 맞는지 확인할 수 있다.

---

# 완료 기준(AC)

- [ ] 계정 삭제 시 `users/{uid}` 하위 학습 데이터는 삭제된다.
- [ ] 루트 컬렉션에 저장된 사용자 연결 데이터는 삭제, 비식별화, 보관 중 하나로 분류된다.
- [ ] 운영용 AI 신고 데이터는 AI 안전 정책 개선을 위해 90일 동안 제한 보관된다.
- [ ] 운영용 AI 신고 데이터는 저장 시점에 서버 기준 만료 시각이 함께 기록된다.
- [ ] 운영용 AI 신고 데이터는 만료 시각을 기준으로 자동 정리될 수 있다.
- [ ] 개발용 prompt review 데이터는 회원 탈퇴 시 삭제된다.
- [ ] 계정 삭제가 진행 중일 때는 추가 write가 다시 데이터를 만들지 못한다.
- [ ] 계정 삭제 실패 시 사용자는 실패를 알 수 있고, 일부 데이터만 삭제된 상태가 조용히 성공 처리되지 않는다.
- [ ] 계정 삭제 정책은 개인정보처리방침과 Play Console Data Safety 설명에 반영할 수 있다.
- [ ] 기존 Chat, Correction, Flashcard, LearningState, Statistics 흐름은 변경되지 않는다.

---

# 기준 문서

- [AI-POLICY-003 약관·개인정보·신고 운영 정책 정리](./AI-POLICY-003_Legal_Disclosure_and_Operations.md)
- [AI-POLICY-001 Chat 안전 프롬프트와 운영용 신고 기능](./AI-POLICY-001_Chat_Safety_Prompt_and_Report.md)
- [AI-POLICY-004 Firebase App Check와 접근 규칙 보안 정리](./AI-POLICY-004_Firebase_AppCheck_and_Rules_Hardening.md)
- [AI-POLICY-005 App Check Enforcement와 Backend 보안 강화](./AI-POLICY-005_AppCheck_Enforcement_and_Backend_Hardening.md)

---

# 핵심 결정

- **계정 삭제 구현은 Cloud Function이 소유한다.**
  - Android 앱은 삭제 요청을 보낼 뿐, Firestore 여러 경로를 직접 삭제하지 않는다.
  - 클라이언트가 직접 삭제하면 중간 실패, 권한 누락, partial delete를 통제하기 어렵다.

- **`users/{uid}` 하위 데이터는 삭제 대상이다.**
  - Chat session, SessionMemory, Flashcard, LearningState, Statistics, notification settings처럼 사용자 학습 경험에 직접 연결된 데이터는 계정 삭제와 함께 삭제한다.

- **루트 컬렉션 데이터는 별도 분류가 필요하다.**
  - `ai_content_reports`는 AI 안전 정책 개선과 운영 신고 검토를 위해 90일 동안 제한 보관한다.
  - `chat_prompt_review_reports`는 개발용 prompt review 자료이므로 회원 탈퇴 시 삭제한다.

- **계정 삭제는 fail-closed로 처리한다.**
  - 계정 삭제 중 `users/{uid}` 또는 삭제 대상 루트 문서 정리에 실패하면 성공으로 응답하지 않는다.
  - 사용자가 삭제 완료로 인식하기 전에 삭제 대상 데이터가 실제로 정리되어야 한다.
  - 실패 로그에는 uid, 문서 경로, 오류 코드 정도만 남기고 transcript 원문은 남기지 않는다.

- **계정 삭제 중에는 추가 write를 막는다.**
  - 삭제 시작 시점에 `deleting` 상태를 먼저 기록하고, 이 상태에서는 클라이언트 write가 다시 문서를 만들지 못하게 막는다.
  - 단순히 Auth 삭제 순서만 바꾸는 것으로는 경쟁 조건을 막기 어렵기 때문에, write 차단 상태를 별도로 둔다.

- **운영용 AI 신고는 90일 보관 후 정리한다.**
- `ai_content_reports`는 신고 원문과 최소 context가 있어야 AI 안전 검토 가치가 있다.
  - 문서 경로(`reportId`)에는 `uid`를 넣지 않고, 시간+난수 기반의 opaque id를 쓴다.
  - 문서 본문에도 uid, 이메일, 닉네임 같은 사용자 식별자는 저장하지 않는다.
  - `sessionId`, `reportedTurnId`, `contextTurns.turnId`는 분석 재현을 위한 가명 식별자로 남길 수 있지만, 사용자 uid와 직접 연결되는 경로값은 아니다.
  - 보관 목적은 AI 안전 정책 개선, 부적절 응답 조사, abuse 대응으로 제한한다.
  - 보관 기간은 90일로 두고, `expiresAt` 또는 동등한 만료 기준을 서버 기준으로 계산한다.

- **정책 문구를 먼저 확정하지 않는다.**
  - 실제 삭제/비식별화 방식이 정해진 뒤 `AI-POLICY-003`의 개인정보처리방침, Data Safety, 계정 삭제 안내 문구를 최종화한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Android app | 사용자에게 계정 삭제 요청 UI와 실패 안내를 제공한다. |
| AuthRepository | `deleteAccount` callable Function 호출과 결과 전달을 담당한다. |
| Cloud Functions | Auth 검증 후 Firestore/Auth 삭제와 루트 데이터 처리 정책을 실행하고, 삭제 중 상태와 만료 정리를 관리한다. |
| Firestore Rules | 클라이언트 직접 delete를 막고 서버 삭제 경로를 우회하지 않게 한다. |
| 운영 정책 | 신고 데이터 보관, 비식별화, 삭제 예외 사유를 정의한다. |
| AI-POLICY-003 | 확정된 삭제 정책을 사용자/심사자에게 설명한다. |

---

# 데이터 분류

| 데이터 | 위치 | 기본 판단 | 이유 |
| --- | --- | --- | --- |
| 사용자 프로필/언어 설정 | `users/{uid}` | 삭제 | 계정과 직접 연결된 기본 사용자 데이터다. |
| Chat/SessionMemory | `users/{uid}` 하위 | 삭제 | 사용자의 대화 원문과 학습 컨텍스트다. |
| Correction/Flashcard | `users/{uid}` 하위 | 삭제 | 학습 결과와 반복 학습 데이터다. |
| LearningState/Statistics | `users/{uid}` 하위 | 삭제 | 사용자 능력 분석과 통계 데이터다. |
| Notification settings/devices | `users/{uid}` 하위 | 삭제 | 계정별 알림 설정과 기기 정보다. |
| AI content reports | `ai_content_reports` | 90일 제한 보관 후 자동 정리 | AI 안전 정책 개선과 부적절 응답 조사를 위해 원문 context가 필요하다. |
| Prompt review reports | `chat_prompt_review_reports` | 회원 탈퇴 시 삭제 | 개발용 프롬프트 개선 자료이며 운영 신고 보관 예외에 포함하지 않는다. |
| Cloud Functions logs | Google Cloud Logging | 보관 예외 가능 | 보안, 장애 분석, abuse 대응 목적의 운영 로그다. |

---

# 현재 저장 필드 기준 주의점

## `chat_prompt_review_reports`

회원 탈퇴 시 문서 자체를 삭제한다.
이 컬렉션은 개발용 프롬프트 리뷰 인덱스지만, 현재 저장 구조에는 단순 uid 외에도 사용자 연결성이 남을 수 있는 필드가 있다.

삭제해야 하는 이유:

- `userId`, `uid`는 Firebase 사용자 식별자다.
- `reviewPath`는 `users/{uid}/chat_prompt_reviews/{reportId}` 형태라 경로 안에 uid가 남는다.
- `sessionId`는 단독 개인정보는 아니지만 대화 세션과 연결 가능한 식별자다.
- `reportNote`는 테스터가 직접 입력한 자유 텍스트라 민감 정보가 들어갈 수 있다.
- `sessionPromptTrace`, `metadata`, `events.text`, `events.metadata`는 대화 흐름과 프롬프트 적용 상태를 재구성할 수 있다.

따라서 uid, 이메일, 닉네임, 기기 식별값만 제거하는 방식보다 문서 삭제가 더 단순하고 안전하다.

## `users/{uid}/chat_prompt_reviews`

`users/{uid}` 하위 문서이므로 기존 recursive delete 대상이다.
다만 루트 인덱스인 `chat_prompt_review_reports`가 이 경로를 `reviewPath`로 들고 있으므로, 루트 인덱스도 함께 삭제해야 삭제 정책이 완결된다.

## `ai_content_reports`

운영용 AI 신고 문서는 90일 제한 보관한다.
현재 저장되는 주요 원문 필드는 다음과 같다.

- `reportedAiText`: 신고된 AI 응답 원문
- `previousUserText`: 직전 사용자 발화 원문
- `contextTurns.text`: 최근 final turn context 원문
- `detailNote`: 사용자가 입력한 신고 메모
  - `detailNote`는 사용자 자유 입력이므로 이메일, 전화번호, 닉네임, 주소 같은 민감 정보가 들어갈 수 있으면 저장 전에 줄이거나 마스킹하는 쪽이 안전하다.
- `userId`, `email`, `nickname`, device id 같은 직접 식별자는 본문에 저장하지 않는다.

이 값들은 AI 안전 검토에 필요한 핵심 자료이므로 회원 탈퇴 시 즉시 삭제하지 않는다.
대신 보관 목적과 기간을 개인정보처리방침과 Data Safety 설명에 명시하고, 90일 만료 후 자동 정리한다.

---

# 주요 작업

## 1. 현재 삭제 경로 확인

- `deleteAccount` Cloud Function이 `users/{uid}`를 recursive delete하는지 확인한다.
- Firebase Auth 계정 삭제가 Firestore 삭제 이후에 실행되는지 확인한다.
- 중간 실패 시 성공으로 표시되지 않는지 확인한다.

## 2. 루트 컬렉션 처리 정책 결정

대상:

- `ai_content_reports`
- `chat_prompt_review_reports`

결정된 방향:

- `chat_prompt_review_reports`는 계정 삭제 시 삭제한다.
- `ai_content_reports`는 AI 안전 정책 개선을 위해 원문 context를 포함해 90일 동안 제한 보관한다.
- `ai_content_reports`는 Cloud Function이 저장 시점의 서버 기준 90일 만료 시각을 함께 기록한다.
- `ai_content_reports`의 문서 ID는 uid를 포함하지 않는 opaque id로 생성한다.
- 운영 보관 목적은 AI 안전, 부적절 응답 조사, abuse 대응으로 제한한다.

## 3. Cloud Function 보강

- `deleteAccount` 안에서 `chat_prompt_review_reports` 삭제 단계를 추가한다.
- `chat_prompt_review_reports` 삭제 실패 시 계정 삭제를 성공으로 응답하지 않는다.
- `ai_content_reports`는 계정 삭제 시 삭제하지 않고, 90일 만료 기준으로 정리한다.
- `ai_content_reports`는 저장 시점에 서버 기준 만료 시각을 기록하고, 만료 대상은 반복 정리로 제거한다.
- `ai_content_reports`의 문서 경로는 사용자 uid를 직접 드러내지 않도록 한다.
- 계정 삭제 시작 시점에 별도 락 문서를 기록해, 진행 중인 클라이언트 write가 다시 데이터를 만들지 못하게 막는다.
- 실패 로그에는 uid와 문서 경로만 남기고, 사용자 원문 transcript를 남기지 않는다.

실패 처리 원칙:

- 삭제 대상 문서 정리 실패는 `deleteAccount` 실패로 반환한다.
- 서버 내부에서 재시도 job을 두더라도, 사용자에게 삭제 성공으로 표시하기 전에 삭제 대상 정리가 완료되어야 한다.
- pending retry는 장애 복구 보조 수단이며, partial delete를 성공으로 숨기는 용도로 쓰지 않는다.

## 4. 앱 실패 안내 확인

- 계정 삭제 실패 시 사용자가 다시 시도하거나 문의할 수 있는 안내가 있는지 확인한다.
- 재인증 실패, 네트워크 실패, 서버 삭제 실패가 같은 메시지로 뭉개지지 않는지 점검한다.

## 5. 정책 문서 반영

- 삭제/비식별화 범위가 확정되면 `AI-POLICY-003`의 개인정보처리방침, Data Safety, 계정 삭제 안내 문구에 반영한다.
- 공개 계정 삭제 URL에는 앱을 삭제한 사용자도 요청할 수 있는 방법을 명시한다.

---

# 예외 처리

- 운영 신고 데이터는 법적 요구, abuse 방지, 정책 위반 조사 목적이 있으면 일부 비식별 정보만 제한 보관할 수 있다.
- 보관 예외가 있으면 개인정보처리방침에 보관 목적과 범위를 명확히 설명한다.
- `ai_content_reports`는 AI 안전 정책 개선 목적의 90일 제한 보관 예외로 둔다.
- `ai_content_reports`의 문서 ID와 경로에는 uid를 넣지 않는다.
- `chat_prompt_review_reports`는 개발용 자료이므로 보관 예외로 두지 않는다.
- Cloud Logging처럼 외부 관리형 로그는 즉시 개별 삭제가 어려울 수 있으므로 보관 기간과 접근 제한 정책으로 관리한다.
- 계정 삭제 중 일부 단계가 실패하면 성공으로 처리하지 않는다.

---

# 테스트 방법

- 테스트 계정으로 Chat, AI 신고, Correction, Flashcard, LearningState, Statistics 데이터를 만든다.
- 계정 삭제를 실행한 뒤 `users/{uid}` 문서와 하위 데이터가 사라지는지 확인한다.
- `chat_prompt_review_reports`에 해당 uid 또는 해당 uid가 포함된 `reviewPath` 문서가 남지 않는지 확인한다.
- `ai_content_reports` 문서는 계정 삭제 후에도 90일 보관 정책에 맞게 남고, 만료 기준 필드가 기록되는지 확인한다.
- `ai_content_reports` 문서 ID에 uid가 들어가지 않는지 확인한다.
- `ai_content_reports` 만료 기준이 지난 문서가 자동 정리 대상이 되는지 확인한다.
- 삭제 진행 중 write가 들어와도 다시 문서가 생성되지 않는지 확인한다.
- 삭제 실패를 의도적으로 재현해 앱이 실패를 표시하고 서버 로그가 원문을 노출하지 않는지 확인한다.
- 계정 삭제 후 같은 사용자가 다시 로그인할 때 이전 학습 데이터가 복원되지 않는지 확인한다.

---

# 검증 기준

- `deleteAccount`는 인증된 사용자만 실행할 수 있다.
- `users/{uid}` 하위 데이터는 계정 삭제 후 남지 않는다.
- `chat_prompt_review_reports`의 사용자 연결 문서는 계정 삭제 후 남지 않는다.
- `ai_content_reports`는 90일 보관 목적과 만료 기준을 가진다.
- 계정 삭제 진행 중에는 사용자 write와 prompt review write가 다시 생성되지 않는다.
- 운영 보관 예외는 개인정보처리방침에 설명할 수 있는 수준으로 제한된다.
- 실패 경로에서 partial delete가 조용히 성공으로 표시되지 않는다.
- 기존 Chat, Correction, Flashcard, LearningState, Statistics 정상 흐름이 깨지지 않는다.

---

# 구현 착수 전 점검

현재 결정만으로 1차 구현에 들어갈 수 있다.
다만 아래 항목은 구현 중 코드 기준으로 확정해야 한다.

- `chat_prompt_review_reports` 삭제 쿼리는 `uid`와 `userId`를 모두 기준으로 잡는다.
- 기존 문서에 `reviewPath`만 있고 `uid/userId`가 누락된 과거 데이터가 있을 수 있는지 확인한다.
- `ai_content_reports`의 90일 만료 필드는 신규 신고 저장 시점에 함께 기록한다.
- 기존 `ai_content_reports` 문서에 만료 필드가 없는 경우 마이그레이션할지, 신규 문서부터 적용할지 결정한다.
- `deleteAccount`에서 `chat_prompt_review_reports` 삭제와 `users/{uid}` recursive delete의 순서를 정한다. 권장 순서는 루트 인덱스 삭제 후 `users/{uid}` 삭제다.
- 삭제 실패 테스트는 Firestore 권한 문제가 아니라 Admin SDK 오류 경로를 재현할 수 있는 단위 테스트로 검증한다.
- `ai_content_reports` cleanup은 limit 초과 시 반복 실행 또는 TTL 정책으로 backlog를 남기지 않게 해야 한다.
