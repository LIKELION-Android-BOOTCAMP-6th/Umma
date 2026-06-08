# [Improvement] AI-POLICY-006 계정 삭제 데이터 정리

## 목적

Google Play 계정 삭제 요구사항은 "사용자가 계정을 삭제할 수 있다"에서 끝나지 않는다.
계정에 연결된 사용자 데이터가 실제로 어디까지 삭제되거나 비식별화되는지, 그리고 운영상 보관해야 하는 예외가 무엇인지 코드와 정책 문서가 일치해야 한다.

현재 Umma의 `deleteAccount` Cloud Function은 `users/{uid}` 문서와 그 하위 데이터를 삭제하고 Firebase Auth 계정을 삭제하는 흐름을 갖고 있다.
하지만 운영용 AI 신고처럼 루트 컬렉션에 `userId`를 들고 저장되는 데이터는 별도 정책이 필요하다.

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
- [ ] 운영용 AI 신고 데이터는 사용자 식별값과 원문 컨텍스트 처리 기준이 정해진다.
- [ ] 개발용 prompt review 데이터는 운영 신고와 다른 보관 기준을 갖는다.
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
  - `ai_content_reports`는 운영 신고 검토와 정책 개선 목적이 있으므로 무조건 즉시 삭제할지, 비식별화할지, 제한 보관할지 결정해야 한다.
  - `chat_prompt_review_reports`는 개발용 prompt review 자료이므로 운영용 신고 데이터와 같은 정책으로 묶지 않는다.

- **정책 문구를 먼저 확정하지 않는다.**
  - 실제 삭제/비식별화 방식이 정해진 뒤 `AI-POLICY-003`의 개인정보처리방침, Data Safety, 계정 삭제 안내 문구를 최종화한다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Android app | 사용자에게 계정 삭제 요청 UI와 실패 안내를 제공한다. |
| AuthRepository | `deleteAccount` callable Function 호출과 결과 전달을 담당한다. |
| Cloud Functions | Auth 검증 후 Firestore/Auth 삭제와 루트 데이터 처리 정책을 실행한다. |
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
| AI content reports | `ai_content_reports` | 삭제 또는 비식별화 결정 필요 | 운영 신고 검토 목적과 개인정보 최소화가 충돌할 수 있다. |
| Prompt review reports | `chat_prompt_review_reports` | 별도 결정 필요 | 개발용 품질 개선 자료라 운영 신고와 목적이 다르다. |
| Cloud Functions logs | Google Cloud Logging | 보관 예외 가능 | 보안, 장애 분석, abuse 대응 목적의 운영 로그다. |

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

결정할 항목:

- 계정 삭제 시 문서를 완전히 삭제할지
- 운영 검토에 필요한 최소 필드만 남기고 `userId`, 원문, context snapshot을 제거할지
- 법적 요구, abuse 방지, 정책 위반 조사 목적으로 일정 기간 보관할지

권장 방향:

- 운영용 AI 신고는 기본적으로 `userId`, 사용자 발화, AI 응답 원문, 최근 context snapshot을 제거하거나 마스킹한다.
- 신고 상태, 신고 사유, 앱 버전, 모델 버전처럼 사용자 재식별성이 낮은 운영 통계 필드는 필요하면 남길 수 있다.
- 개발용 prompt review 데이터는 출시 운영 데이터가 아니므로 별도 삭제 또는 주기적 정리 정책을 둔다.

## 3. Cloud Function 보강

- `deleteAccount` 안에서 `users/{uid}` 삭제 후 루트 컬렉션 처리 단계를 추가한다.
- 루트 컬렉션 처리 실패 시 전체 계정 삭제를 실패로 볼지, 재시도 가능한 pending 상태로 남길지 결정한다.
- 실패 로그에는 uid와 문서 경로만 남기고, 사용자 원문 transcript를 남기지 않는다.

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
- Cloud Logging처럼 외부 관리형 로그는 즉시 개별 삭제가 어려울 수 있으므로 보관 기간과 접근 제한 정책으로 관리한다.
- 계정 삭제 중 일부 단계가 실패하면 성공으로 처리하지 않는다.

---

# 테스트 방법

- 테스트 계정으로 Chat, AI 신고, Correction, Flashcard, LearningState, Statistics 데이터를 만든다.
- 계정 삭제를 실행한 뒤 `users/{uid}` 문서와 하위 데이터가 사라지는지 확인한다.
- `ai_content_reports`에 남은 문서가 정책대로 삭제 또는 비식별화됐는지 확인한다.
- `chat_prompt_review_reports`가 결정된 정책대로 처리되는지 확인한다.
- 삭제 실패를 의도적으로 재현해 앱이 실패를 표시하고 서버 로그가 원문을 노출하지 않는지 확인한다.
- 계정 삭제 후 같은 사용자가 다시 로그인할 때 이전 학습 데이터가 복원되지 않는지 확인한다.

---

# 검증 기준

- `deleteAccount`는 인증된 사용자만 실행할 수 있다.
- `users/{uid}` 하위 데이터는 계정 삭제 후 남지 않는다.
- 루트 컬렉션의 사용자 연결 데이터는 문서에 정한 기준대로 삭제 또는 비식별화된다.
- 운영 보관 예외는 개인정보처리방침에 설명할 수 있는 수준으로 제한된다.
- 실패 경로에서 partial delete가 조용히 성공으로 표시되지 않는다.
- 기존 Chat, Correction, Flashcard, LearningState, Statistics 정상 흐름이 깨지지 않는다.
