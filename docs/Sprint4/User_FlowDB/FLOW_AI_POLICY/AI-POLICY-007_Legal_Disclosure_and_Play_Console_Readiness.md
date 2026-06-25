# [Improvement] AI-POLICY-007 정책 공개 URL과 Play Console 심사 입력 준비

## 목적

Sprint3의 [AI-POLICY-003 약관·개인정보·신고 운영 정책 정리](../../../Sprint3/User_FlowDB/FLOW_AI_POLICY/AI-POLICY-003_Legal_Disclosure_and_Operations.md)는 Google Play 제출에 필요한 정책·운영 항목을 넓게 정리했다.
하지만 Sprint3에서는 App Check, Firestore Rules, AI 신고, 계정 삭제 데이터 정책 등 선행 작업을 먼저 진행하면서 정책 공개 URL과 Play Console 입력 준비를 마무리하지 못했다.

이번 Sprint4 작업은 `AI-POLICY-003`의 남은 항목 중 실제 제출 전에 필요한 정책 공개 페이지, 앱 내 접근 경로, Play Console 입력 초안을 준비하는 데 집중한다.
계정 삭제 데이터 처리 구현은 [AI-POLICY-006 계정 삭제 데이터 정리](../../../Sprint3/User_FlowDB/FLOW_AI_POLICY/AI-POLICY-006_Account_Deletion_Data_Cleanup.md)를 기준으로 하고, App Check enforcement와 backend hardening은 [AI-POLICY-005 App Check Enforcement와 Backend 보안 강화](../../../Sprint3/User_FlowDB/FLOW_AI_POLICY/AI-POLICY-005_AppCheck_Enforcement_and_Backend_Hardening.md)를 기준으로 별도 진행한다.

---

# User Story

사용자는 앱 안에서 개인정보처리방침, 이용약관, AI 생성 콘텐츠 안내를 확인할 수 있다.
앱을 삭제한 사용자도 공개 URL을 통해 계정 삭제 요청 방법을 확인할 수 있다.
Google Play 심사자는 Play Console에 입력된 URL과 설명을 통해 Umma의 데이터 처리, AI 신고, 계정 삭제 경로를 확인할 수 있다.

---

# 완료 기준(AC)

- [ ] 개인정보처리방침, 이용약관, AI 생성 콘텐츠 안내, 계정 삭제 안내는 로그인 없이 열리는 공개 URL로 준비된다.
- [ ] 앱 안에서 개인정보처리방침, 이용약관, AI 생성 콘텐츠 안내에 접근할 수 있다.
- [ ] 개인정보처리방침에는 음성/transcript, AI 응답, 교정 결과, Flashcard, LangState/Statistics, AI 신고 데이터 처리 목적이 포함된다.
- [ ] 계정 삭제 안내는 앱 안의 삭제 경로와 앱 밖 공개 요청 경로를 모두 설명한다.
- [ ] Play Console Data Safety 입력 초안은 실제 저장 흐름과 맞게 정리된다.
- [ ] Play Console App access 입력 초안은 리뷰어가 로그인 후 Chat, AI 신고, 교정, Flashcard 흐름을 확인할 수 있게 작성된다.
- [ ] AI 생성 콘텐츠 신고 기능 설명은 앱의 실제 AI 신고 기능과 맞게 정리된다.
- [ ] 콘텐츠 등급, 타깃 연령, 광고 포함 여부, 스토어 등록정보, 이미지 자산 준비 항목이 제출 전 체크리스트로 정리된다.
- [ ] 정책 문구는 `AI-POLICY-005`, `AI-POLICY-006`의 보안·삭제 결정과 충돌하지 않는다.

---

# 기준 문서

- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google Play Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455)
- [AI-POLICY-001 Chat 안전 프롬프트와 운영용 신고 기능](../../../Sprint3/User_FlowDB/FLOW_AI_POLICY/AI-POLICY-001_Chat_Safety_Prompt_and_Report.md)
- [AI-POLICY-002 Correction 후보와 Flashcard 저장 안전 방어](../../../Sprint3/User_FlowDB/FLOW_AI_POLICY/AI-POLICY-002_Correction_and_Flashcard_Safety_Guard.md)
- [AI-POLICY-003 약관·개인정보·신고 운영 정책 정리](../../../Sprint3/User_FlowDB/FLOW_AI_POLICY/AI-POLICY-003_Legal_Disclosure_and_Operations.md)
- [AI-POLICY-005 App Check Enforcement와 Backend 보안 강화](../../../Sprint3/User_FlowDB/FLOW_AI_POLICY/AI-POLICY-005_AppCheck_Enforcement_and_Backend_Hardening.md)
- [AI-POLICY-006 계정 삭제 데이터 정리](../../../Sprint3/User_FlowDB/FLOW_AI_POLICY/AI-POLICY-006_Account_Deletion_Data_Cleanup.md)

---

# 핵심 결정

- **정책 공개 URL은 Firebase Hosting을 기본 경로로 한다.**
  - Google Play 제출용 URL은 로그인 없이 열려야 한다.
  - PDF, Notion 임시 페이지, Drive 공유 링크는 제출용으로 사용하지 않는다.

- **앱 내 접근은 마이페이지 정책 메뉴에서 시작한다.**
  - 온보딩 약관 동의 흐름은 변경 범위가 커서 이번 작업에 포함하지 않는다.
  - 앱 내 정책 메뉴는 공개 URL을 여는 방식으로 최신성을 유지한다.

- **정책 문구는 코드와 실제 운영 결정을 기준으로 작성한다.**
  - 수집하지 않는 데이터를 적지 않는다.
  - 저장하는 데이터는 Data Safety와 개인정보처리방침에서 서로 다르게 설명하지 않는다.
  - AI 신고 데이터 보관과 계정 삭제 예외는 `AI-POLICY-006` 결정과 맞춘다.

- **Play Console 입력값은 문서 초안으로 먼저 준비한다.**
  - 실제 Console 제출은 App Bundle, 내부 테스트, 공개 URL 배포 상태가 갖춰진 뒤 진행한다.
  - 이번 문서는 제출 전 누락을 줄이기 위한 준비 문서다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| Android app | 마이페이지에서 정책 링크를 열고, 링크 실패 시 사용자에게 단순 오류를 안내한다. |
| Firebase Hosting | 개인정보처리방침, 이용약관, AI 안전 안내, 계정 삭제 안내 정적 페이지를 공개한다. |
| Play Console | Privacy Policy URL, Data Safety, App access, 콘텐츠 등급, 타깃 연령, 광고 여부, 스토어 정보를 입력한다. |
| 운영 정책 | AI 신고 데이터 보관, 검토, 삭제 요청 대응 기준을 정리한다. |
| AI-POLICY-005 | App Check enforcement, Functions 검증, API key 최소화 같은 보안 적용을 담당한다. |
| AI-POLICY-006 | 계정 삭제 시 데이터 삭제/비식별화/보관 기준을 담당한다. |

---

# 주요 작업

## 1. 공개 정책 페이지 초안 준비

준비할 페이지:

- 개인정보처리방침
- 이용약관
- AI 생성 콘텐츠 및 신고 안내
- 계정 삭제 안내

포함해야 하는 핵심 내용:

- Umma가 언어 학습을 위해 AI 대화, 교정, Flashcard, 반복학습을 제공한다는 점
- 음성 입력 또는 transcript가 대화, 교정, 학습 기록, 신고 검토에 사용될 수 있다는 점
- AI 응답은 언어 학습 보조용이며 의료, 법률, 금융 등 전문 조언이 아니라는 점
- 사용자가 AI 응답을 앱 안에서 신고할 수 있다는 점
- 계정 삭제 시 삭제되는 데이터와 제한 보관되는 운영 데이터가 구분된다는 점

## 2. Firebase Hosting 배포 준비

권장 URL:

```text
https://umma-app.web.app/privacy
https://umma-app.web.app/terms
https://umma-app.web.app/ai-safety
https://umma-app.web.app/account-deletion
```

주의:

- URL은 로그인 없이 열려야 한다.
- 지역 제한이 없어야 한다.
- 모바일 브라우저에서 읽을 수 있어야 한다.
- 배포된 페이지 내용과 앱 내 링크가 같은 URL을 바라봐야 한다.

## 3. 앱 내 정책 접근 경로 추가

권장 위치:

```text
마이페이지
→ 약관 및 정책
   → 개인정보처리방침
   → 이용약관
   → AI 생성 콘텐츠 및 신고 안내
   → 계정 삭제 안내
```

작업 기준:

- 외부 브라우저 또는 앱 내 Custom Tab 중 프로젝트 기존 패턴에 맞는 방식을 사용한다.
- 링크 열기 실패 시 앱이 크래시 나지 않고 오류 안내만 보여준다.
- 정책 메뉴는 Chat, Correction, SRS, Statistics 흐름과 결합하지 않는다.

## 4. Play Console 입력 초안 작성

준비할 입력:

- Privacy Policy URL
- Data Safety 수집 데이터와 처리 목적
- AI 생성 콘텐츠 신고 기능 설명
- App access 리뷰어 접근 안내
- 계정 삭제 안내 URL
- 콘텐츠 등급 설문 답변 기준
- 타깃 연령과 Families 정책 적용 여부
- 광고 포함 여부
- 스토어 등록정보와 이미지 자산 체크리스트

초안 작성 원칙:

- 실제 코드가 저장하는 데이터만 적는다.
- AI 제공자에게 전송되는 데이터 범위를 숨기지 않는다.
- 사용자가 삭제를 요청할 수 있는 데이터와 운영상 제한 보관되는 데이터를 구분한다.

## 5. 정책·코드 정합성 점검

점검 대상:

- AI 신고 저장 데이터와 정책 문구
- 계정 삭제 Cloud Function 동작과 계정 삭제 안내 문구
- Firestore Rules/App Check 적용 상태와 보안 설명
- 음성/transcript/AI 응답/교정/Flashcard/LangState/Statistics 저장 흐름과 Data Safety 입력

---

# 제외 범위

- App Check enforcement 적용
- Cloud Functions 보안 검증 추가
- 계정 삭제 Cloud Function 구현 변경
- AI 신고 schema 변경
- Chat/Correction 안전 필터 정책 변경
- 온보딩 약관 동의 UI 개편
- 관리자용 신고 검토 화면 구현
- 첫 App Bundle 업로드 이후 필요한 upload key SHA 등록

---

# 예외 처리

- Firebase Hosting URL이 아직 배포되지 않은 상태에서는 repository 문서 초안을 기준으로 리뷰하되, Play Console 제출 전에는 반드시 공개 URL로 전환한다.
- 정책 문구는 법률 검토가 필요할 수 있으므로 개발 문서만으로 최종 확정하지 않는다.
- 앱 내 정책 링크가 일시적으로 열리지 않아도 Chat, Correction, SRS, Statistics 주요 기능이 중단되지 않아야 한다.
- Google Play 정책이 변경되면 제출 전 공식 문서를 다시 확인하고 문구를 갱신한다.

---

# 테스트 방법

- 공개 URL을 로그아웃 상태의 브라우저에서 열어 접근 가능한지 확인한다.
- 앱 마이페이지에서 각 정책 링크를 눌러 올바른 URL이 열리는지 확인한다.
- 네트워크 오류 또는 잘못된 URL을 임시로 재현해 앱이 크래시 나지 않는지 확인한다.
- Data Safety 초안의 데이터 항목이 실제 Firestore/Functions 저장 흐름과 맞는지 대조한다.
- App access 초안만으로 리뷰어가 로그인, Chat, AI 신고, 교정, Flashcard 저장 흐름을 이해할 수 있는지 확인한다.
- 계정 삭제 안내가 `AI-POLICY-006`의 삭제/보관 정책과 맞는지 확인한다.

---

# 검증 기준

- 공개 정책 URL은 활성 상태이고 로그인 없이 접근 가능하다.
- 앱 안에서 정책 링크에 접근할 수 있다.
- 정책 문구와 Data Safety 초안은 실제 코드의 데이터 저장·전송 흐름과 충돌하지 않는다.
- AI 신고 기능 설명은 실제 `AI-POLICY-001` 구현 범위를 과장하지 않는다.
- 계정 삭제 안내는 `AI-POLICY-006`의 삭제/보관 기준과 맞다.
- App Check, API key, Firestore Rules 관련 보안 설명은 `AI-POLICY-005` 범위를 침범하지 않는다.
- 기존 Chat, Correction, Flashcard, SRS, LearningState, Statistics 기능은 변경되지 않는다.

---

# 완료 후 기대 상태

- Sprint3에서 남은 `AI-POLICY-003`의 제출 준비 항목이 Sprint4 작업 단위로 분리된다.
- Google Play 제출 전에 필요한 공개 URL, 앱 내 링크, Console 입력 초안이 한 문서에서 추적된다.
- 보안 적용, 계정 삭제 구현, 정책 고지 작업의 책임 경계가 섞이지 않는다.
