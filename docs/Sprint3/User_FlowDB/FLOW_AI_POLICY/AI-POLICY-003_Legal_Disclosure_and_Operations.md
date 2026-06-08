# [Improvement] AI-POLICY-003 약관·개인정보·신고 운영 정책 정리

## 목적

AI 안전 대응은 앱 코드만으로 끝나지 않는다.
Google Play 배포를 위해서는 개인정보처리방침 공개 URL, 앱 내 정책 접근 경로, Data Safety 입력, 신고 운영 절차가 함께 준비되어야 한다.

이 작업은 법률 문서 최종 작성 자체가 아니라, Umma 앱이 어떤 데이터를 수집/저장/검토하는지 제품·개발 관점에서 정리하고 앱 내 접근 경로와 운영 흐름을 만든다.

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
- 실제 삭제 자동화는 별도 후속 작업으로 분리할 수 있지만, 개인정보처리방침에는 처리 기준을 먼저 명시한다.

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

5. **운영 신고 처리 기준 정리**
   - 신고 상태값
   - 검토 담당자
   - prompt/filter 개선으로 연결하는 기준
   - 기본 1년 보관과 사용자 삭제 요청 처리 기준

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
