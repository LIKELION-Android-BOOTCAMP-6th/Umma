# [Improvement] AI-POLICY-003 약관·개인정보·신고 운영 정책 정리

## 목적

AI 안전 대응은 앱 코드만으로 끝나지 않는다.
Google Play 배포를 위해서는 개인정보처리방침 공개 URL, 앱 내 정책 접근 경로, Data Safety 입력, 신고 운영 절차가 함께 준비되어야 한다.

이 작업은 법률 문서 최종 작성 자체가 아니라, Umma 앱이 어떤 데이터를 수집/저장/검토하는지 제품·개발 관점에서 정리하고 앱 내 접근 경로와 운영 흐름을 만든다.

이 문서는 `AI-POLICY-004`, `AI-POLICY-005`, `AI-POLICY-006`보다 실제 작업 순서가 뒤에 올 수 있다.
하지만 Google Play 제출 직전에는 본 문서를 최종 심사 체크리스트로 사용한다.

---

# User Story

사용자는 Umma가 음성, transcript, AI 응답, 교정 결과, Flashcard, 신고 데이터를 어떻게 처리하는지 확인할 수 있다.
운영자는 신고된 AI 생성 콘텐츠를 검토하고, 필요한 경우 prompt/filter 정책 개선으로 연결할 수 있다.
Google Play 심사자는 개인정보처리방침 URL과 앱 내 정책 안내를 통해 데이터 처리 방식을 확인할 수 있다.

---

# 완료 기준(AC)

- [ ] 개인정보처리방침은 공개 URL에서 확인할 수 있다.
- [ ] 앱 안에서 개인정보처리방침과 이용약관에 접근할 수 있다.
- [ ] AI 생성 콘텐츠와 신고 기능에 대한 안내를 앱 안에서 확인할 수 있다.
- [ ] 개인정보처리방침에는 음성/transcript, AI 응답, 교정 결과, Flashcard, 신고 데이터 처리 목적이 포함된다.
- [ ] 신고 데이터의 보관 목적, 검토 가능성, 기본 1년 보관, 삭제 요청 처리 정책이 정리된다.
- [ ] Play Console Data Safety에 입력해야 할 데이터 항목이 코드의 실제 수집/저장 흐름과 맞게 정리된다.
- [ ] 신고 데이터는 운영 상태를 가질 수 있고, prompt/filter 개선에 활용할 수 있다.
- [ ] Play Console 심사에 필요한 AI 생성 콘텐츠 설명, 데이터 보안 설명, 계정 삭제 안내 URL이 준비된다.
- [ ] 로그인 후 접근이 필요한 기능은 Play Console `App access`에 리뷰어 접근 방법이 준비된다.
- [ ] 콘텐츠 등급, 타깃 연령, 광고 포함 여부, 스토어 등록정보, 이미지 자산이 실제 앱 성격과 맞게 준비된다.
- [ ] 개인 개발자 계정의 closed testing 요구사항이 적용되는지 확인하고, 필요한 경우 테스트 계획이 준비된다.
- [ ] 운영 중 신고/정책/보안 문제가 발생했을 때 확인할 담당 영역과 기본 대응 절차가 정리된다.

---

# 기준 문서

- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455)
- [AI-POLICY-001 Chat 안전 프롬프트와 운영용 신고 기능](./AI-POLICY-001_Chat_Safety_Prompt_and_Report.md)
- [AI-POLICY-002 Correction 후보와 Flashcard 저장 안전 방어](./AI-POLICY-002_Correction_and_Flashcard_Safety_Guard.md)
- [FLOW-AI-CHAT 개선 문서 묶음](../FLOW_AI_CHAT_IMPROVEMENTS)
- [FLOW-COR 개선 문서 묶음](../FLOW_COR_IMPROVEMENTS)

---

# 핵심 결정

- 개인정보처리방침은 공개 URL이 필요하다.
- URL은 로그인 없이 접근 가능해야 하고, 활성 상태이며, PDF가 아니고, 지역 제한이 없어야 한다.
- 홈페이지가 없어도 Firebase Hosting으로 정적 정책 페이지를 배포한다.
- 앱 안에서도 정책 링크 또는 정책 본문에 접근할 수 있어야 한다.
- 운영용 신고 저장소는 개발용 prompt review 저장소와 분리한다.
- 운영용 신고 데이터의 기본 보관 기간은 신고 접수일 기준 1년으로 둔다.
- 사용자가 계정/데이터 삭제를 요청하면 관련 신고 데이터는 운영 검토에 필요한 최소 정보만 남기거나 삭제하는 정책으로 정리한다.

---

# 공개 URL 준비 방향

공개 정책 URL은 Firebase Hosting을 기본 경로로 사용한다.

예시:

```text
https://umma-app.web.app/privacy
https://umma-app.web.app/terms
https://umma-app.web.app/ai-safety
```

선택지:

| 방식 | 판단 |
| --- | --- |
| Firebase Hosting | 기본 선택. HTTPS 기본 제공, 정적 HTML 배포가 쉽고 Google Play 심사 대응에 안정적이다. |
| GitHub Pages | Firebase Hosting을 사용할 수 없을 때의 예비 선택지다. |
| Notion 공개 페이지 | 제출용으로 사용하지 않는다. 내부 초안 공유에만 사용할 수 있다. |
| Google Drive/PDF | 사용하지 않는다. Play 정책상 PDF는 부적절하고 접근 제한 문제가 생길 수 있다. |

---

# 앱 내 노출 위치

권장 위치:

```text
마이페이지
→ 약관 및 정책
   → 이용약관
   → 개인정보처리방침
   → AI 생성 콘텐츠 및 신고 안내
```

추가 고려:

- 본 작업에서는 마이페이지 접근 경로를 먼저 만든다.
- 회원가입/온보딩의 약관 동의 UI는 기존 온보딩 흐름 변경 폭이 크므로 별도 후속 작업으로 분리한다.
- 앱 내 문구는 정책 전문을 모두 복사하기보다 공개 URL로 연결해 최신성을 유지한다.

---

# 개인정보처리방침에 들어갈 데이터 항목

정책 문구는 법률 검토가 필요하지만, 개발 기준으로 아래 항목은 빠지면 안 된다.

- 사용자 계정 식별자
- primaryLang, selectedLang 등 학습 언어 설정
- 음성 입력 또는 음성에서 생성된 transcript
- AI Chat 응답 transcript
- SessionMemory에 저장되는 최근 대화 맥락
- 교정 후보, 교정 결과, 설명
- Flashcard 앞면/뒷면/설명/SRS 학습 기록
- LangState, DashSummary, 통계 history
- AI 콘텐츠 신고 데이터
- 신고 시점 기준 최근 6턴 대화 컨텍스트 snapshot
- 앱 버전, 모델 버전, prompt version 같은 운영 추적 정보

각 항목마다 처리 목적도 함께 정리한다.

예:

```text
AI 응답 transcript: 대화 화면 표시, 교정 후보 추출, 신고 검토, 품질 개선
Flashcard: 반복 학습 제공, 학습 통계 계산
신고 데이터: 부적절한 AI 생성 콘텐츠 검토와 안전 정책 개선
최근 6턴 신고 컨텍스트: 신고된 AI 응답의 맥락 판단
```

---

# Play Console Data Safety 정리 대상

Play Console에는 실제 수집/저장 흐름과 일치하게 입력해야 한다.
문서와 코드가 다르면 심사 리스크가 생긴다.

정리 대상:

- 수집하는 데이터 종류
- 수집 목적
- 제3자 공유 여부
- 암호화 전송 여부
- 사용자가 삭제를 요청할 수 있는지
- 데이터 보관 기간
- AI 제공자에게 전송되는 데이터 범위

주의:

- 음성 자체를 장기 저장하지 않더라도 transcript가 저장되면 텍스트 데이터 수집에 해당한다.
- 신고 데이터에는 민감한 AI 응답/사용자 발화가 포함될 수 있다.
- 전체 세션 원문을 기본 저장하지 않고, 신고 시점 기준 최근 6턴 snapshot만 저장한다.
- Firestore local-first/pending sync 구조도 실제 저장 흐름에 포함해 설명해야 한다.

---

# Play Console 심사 준비 항목

Google Play 제출 전에는 앱 안의 기능 구현뿐 아니라 Console 입력값과 공개 문서가 서로 맞아야 한다.
이 문서는 법률 문구 최종본을 대신하지 않고, 개발/제품 기준으로 빠뜨리면 안 되는 준비 항목을 정리한다.

필수 확인 항목:

- Privacy Policy URL
- 앱 내 개인정보처리방침 접근 경로
- 앱 내 계정 삭제 기능 또는 삭제 안내 경로
- 앱을 삭제한 사용자도 계정 삭제를 요청할 수 있는 공개 계정 삭제 URL
- Data Safety 입력 항목
- AI 생성 콘텐츠 신고 기능 설명
- AI 응답은 언어 학습 보조용이며 전문 조언이 아니라는 안내
- 음성/transcript/AI 응답/교정/Flashcard/신고 데이터 처리 목적
- 로그인 후 접근 기능에 대한 `App access` 심사 안내
- 콘텐츠 등급 설문
- 타깃 연령과 Families 정책 적용 여부
- 광고 포함 여부 선언
- 스토어 등록정보와 이미지 자산
- 개인 개발자 계정 closed testing 요구사항

주의:

- 공개 URL은 로그인 없이 열려야 한다.
- 정책 URL은 Play Console 제출 시점에 실제로 접근 가능해야 한다.
- 앱 설명과 Data Safety가 실제 코드 저장 흐름과 다르면 심사 리스크가 생긴다.
- AI 신고 기능은 앱 밖 이메일 문의만으로 대체하지 않고, 앱 안에서 신고할 수 있어야 한다.
- 계정 삭제 공개 URL은 개인정보처리방침의 일부 문단에 묻히지 않고, 사용자가 삭제 요청 경로를 쉽게 찾을 수 있어야 한다.
- Umma의 "아기 수준", "3세 수준" 표현은 언어능력 비유이므로, 실제 타깃 연령을 어린이로 오해하게 만들지 않도록 스토어 문구와 Play Console 답변을 분리해 판단한다.
- Google 로그인만 제공하더라도 리뷰어가 막히지 않도록 `App access`에 테스트 접근 방법이나 특이사항을 적는다.

## Play Console 입력 체크리스트

이 체크리스트는 보안 구현 결과 보고서가 아니라, 제출 직전에 누락을 막기 위한 확인표다.
항목별 입력값은 코드와 정책 URL이 확정된 뒤 최종 작성한다.

| 항목 | 준비 내용 | 판단 기준 |
| --- | --- | --- |
| Privacy Policy | 공개 URL과 앱 내 접근 경로 | 로그인 없이 열리고 Umma 데이터 처리 흐름을 설명한다. |
| Data Safety | 수집 데이터, 처리 목적, 공유 여부, 삭제 요청 가능 여부 | 실제 코드의 음성/transcript/AI 응답/교정/Flashcard/신고 저장 흐름과 맞다. |
| Account deletion | 앱 내 삭제 경로와 공개 삭제 요청 URL | 앱을 삭제한 사용자도 웹에서 계정 삭제를 요청할 수 있다. |
| App access | 리뷰어가 로그인 후 핵심 기능을 볼 수 있는 방법 | Google 로그인, 테스트 계정, 접근 순서, 특이사항을 설명한다. |
| Content rating | 콘텐츠 등급 설문 | AI 대화, 음성 입력, 사용자 생성 발화 가능성을 고려해 답한다. |
| Target audience | 실제 대상 연령과 Families 적용 여부 | 언어능력 비유와 실제 아동 대상 여부를 혼동하지 않는다. |
| Ads | 광고 포함 여부 | 광고 SDK나 앱 안의 광고 노출이 있으면 실제 상태대로 선언한다. |
| Store listing | 앱 이름, 짧은 설명, 긴 설명, 카테고리, 연락처 | AI 언어 학습 보조 앱이라는 범위가 과장 없이 드러난다. |
| Preview assets | 스크린샷, feature graphic, 앱 아이콘 | 실제 화면과 기능을 오해 없이 보여준다. |
| Closed testing | 개인 개발자 계정 테스트 요구사항 | 계정에 12명/14일 조건이 적용되면 closed test 계획을 준비한다. |

## 계정 삭제와 데이터 정합성

계정 삭제 안내는 사용자에게 보이는 정책이므로, 실제 삭제 동작과 어긋나면 안 된다.
현재 삭제 구현은 `deleteAccount` Cloud Function을 통해 `users/{uid}` 아래 사용자 데이터를 삭제하고 Firebase Auth 계정을 삭제하는 흐름을 기준으로 한다.

추가로 확인해야 하는 데이터:

- `ai_content_reports`
- `chat_prompt_review_reports`
- 운영 검토를 위해 루트 컬렉션에 저장되는 제한 보관 데이터
- Cloud Functions 로그처럼 직접 삭제가 어렵고 보관 목적이 다른 운영 로그

이 항목들은 실제 삭제/비식별화 정책을 먼저 확정해야 개인정보처리방침과 Data Safety 설명을 정확히 작성할 수 있다.
구현 작업은 [AI-POLICY-006 계정 삭제 데이터 정리](./AI-POLICY-006_Account_Deletion_Data_Cleanup.md)를 기준으로 분리한다.

---

# 운영 신고 처리 흐름

권장 상태:

```text
New
Reviewed
Actioned
Dismissed
```

운영 흐름:

```text
사용자 신고
→ ai_content_reports/{reportId} 저장
→ 운영자가 신고 내용 검토
→ 실제 유해 응답이면 prompt/filter 개선 항목으로 연결
→ 조치 결과 status 업데이트
```

운영 도구는 초기에는 Firestore Console 조회로 시작한다.
신고가 늘어나면 export 도구 또는 관리자 화면을 별도 작업으로 만든다.

보관 정책:

- 운영용 신고 데이터는 기본 1년 보관한다.
- 신고 원문 컨텍스트는 기본적으로 최근 6턴 snapshot만 저장한다.
- 법적 요구, 정책 위반 조사, abuse 방지를 위해 더 오래 보관해야 하는 경우는 운영 정책 문서에 별도 사유를 남긴다.
- 사용자 삭제 요청이 들어오면 신고 검토에 필요한 최소 비식별 정보만 남기거나 삭제한다.
- AI 안전 신고 문서는 uid를 포함하지 않는 opaque reportId를 사용하고, 원문 대화는 분석 목적상 유지하되 사용자 식별자는 제거하거나 가명화한다.
- 본문에는 userId, email, nickname 같은 직접 식별자는 남기지 않는다.
- 실제 삭제 자동화는 별도 후속 작업으로 분리할 수 있지만, 개인정보처리방침에는 처리 기준을 먼저 명시한다.

---

# 운영 확인과 기본 대응 절차

초기 운영 도구는 Firestore Console과 Firebase Console 지표를 기준으로 시작한다.
전용 관리자 도구는 신고량과 운영 부담이 커진 뒤 별도 작업으로 분리한다.

기본 확인 경로:

- 신고 접수 확인: `ai_content_reports`
- 개발용 prompt review 확인: `chat_prompt_review_reports`
- Firestore 권한 오류: 클라이언트 로그와 Firestore Rules 변경 이력
- App Check 차단 의심: Firebase Console App Check 지표
- Functions 오류: Cloud Functions logs

기본 대응 원칙:

- 신고된 AI 응답은 먼저 실제 유해 응답인지 검토한다.
- prompt/filter 개선이 필요한 경우 실패 사례를 별도 tuning 자료로 남긴다.
- 보안 설정 변경 후 정상 사용 흐름이 막히면 최근 Rules/App Check/API key 변경을 우선 확인한다.
- 운영 상태 변경은 사용자가 직접 수정할 수 없고, 운영자 또는 Admin SDK 영역에서 처리한다.

---

# 주요 작업

1. **정책 문서 초안 작성**
   - 개인정보처리방침 초안
   - 이용약관 초안
   - AI 생성 콘텐츠 및 신고 안내 초안

2. **공개 URL 호스팅 준비**
   - Firebase Hosting으로 정적 HTML 페이지 배포
   - HTML 정적 페이지로 배포
   - URL이 로그인 없이 열리는지 확인

3. **앱 내 정책 접근 경로 추가**
   - 마이페이지에서 정책 링크를 열 수 있게 한다.
   - 외부 브라우저로 공개 URL을 연다.
   - 정책 링크 실패 시 사용자에게 단순 오류 안내를 제공한다.

4. **Play Console 입력 준비**
   - Privacy Policy URL
   - Data Safety 항목
   - AI 생성 콘텐츠 정책 대응 설명
   - 앱 설명 문구
   - 앱 내 계정 삭제 기능 또는 삭제 안내 경로
   - 공개 계정 삭제 요청 URL
   - App access 심사 안내
   - 콘텐츠 등급 설문
   - 타깃 연령과 Families 정책 적용 여부
   - 광고 포함 여부 선언
   - 스토어 등록정보와 이미지 자산
   - 개인 개발자 계정 closed testing 필요 여부
   - AI 생성 콘텐츠 신고 기능 설명

5. **운영 신고 처리 기준 정리**
   - 신고 상태값
   - 검토 담당자
   - prompt/filter 개선으로 연결하는 기준
   - 기본 1년 보관과 사용자 삭제 요청 처리 기준

6. **운영 확인 절차 정리**
   - 신고 저장 확인 경로
   - Firestore/App Check/Functions 오류 확인 경로
   - 배포 후 문제가 생겼을 때의 기본 대응 순서

---

# 예외 처리

- 공개 URL이 아직 준비되지 않은 개발 단계에서는 문서 초안을 repository에 두되, 배포 전에는 반드시 공개 URL로 옮긴다.
- 정책 문구는 개발 문서만으로 확정하지 않고, 배포 전 법률/운영 검토를 거친다.
- Notion/Drive 같은 임시 링크는 내부 검토용으로만 사용하고, Play Console 제출용으로는 안정적인 정적 URL을 사용한다.
- 앱 내 정책 링크가 열리지 않아도 앱 주요 기능이 크래시 나지 않게 한다.

---

# 검증 기준

- 개인정보처리방침 URL이 로그인 없이 열리는지 확인한다.
- URL이 PDF가 아니고, 공개 접근 가능하며, 지역 제한이 없는지 확인한다.
- 앱 내 마이페이지에서 개인정보처리방침/이용약관/AI 안전 안내에 접근할 수 있는지 확인한다.
- 개인정보처리방침 내용이 실제 코드의 데이터 수집/저장 흐름과 어긋나지 않는지 확인한다.
- Play Console Data Safety 입력 항목이 코드와 문서 기준으로 설명 가능한지 확인한다.
- 운영용 신고 데이터가 `ai_content_reports` 기준으로 검토 가능한지 확인한다.
- Play Console 제출에 필요한 AI 생성 콘텐츠 설명과 계정 삭제 공개 URL이 준비됐는지 확인한다.
- 로그인 후 기능 접근이 필요한 경우 `App access` 안내로 리뷰어가 Chat, Correction, Flashcard 흐름을 볼 수 있는지 확인한다.
- 콘텐츠 등급, 타깃 연령, 광고 포함 여부, 스토어 등록정보, 이미지 자산, closed testing 필요 여부가 제출 전에 확인됐는지 점검한다.
- 계정 삭제 설명이 `AI-POLICY-006`에서 확정한 실제 삭제/비식별화 범위와 맞는지 확인한다.
- 운영자가 신고, Firestore 권한 오류, App Check 차단 의심, Functions 오류를 확인할 경로를 알고 있는지 확인한다.
