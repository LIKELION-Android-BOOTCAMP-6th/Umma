# User Flow — FLOW-ONBOARDING

## 목표

- 사용자가 Google 계정으로 인증 후, 기본 계정 설정을 완료하고 Umma 서비스를 사용할 수 있다

---

## 시작 조건

- 앱이 최초 실행된 상태
- 사용자가 로그인되지 않은 상태
- 인터넷 연결 가능 상태

---

## 성공 조건

- 사용자가 로그인 후, 최초 설정을 완료하고, Dashboard 화면까지 정상 진입한다.
- 최초 설정에서 모국어와 주 학습 언어가 저장되고, 주 학습 언어가 현재 선택된 언어로 설정된다.

---

# 주요 단계

| 단계 | 사용자 행동 | 시스템 반응 | 성공 분기 | 실패 분기 | 상태 | 예외 처리 | Flow 상세 페이지 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 앱 진입 | 앱 실행 | 스플래시 표시 및 세션 확인 | 유효 세션 존재 시 홈 화면 이동 / 세션 없으면 온보딩 진입 | 세션 확인 실패 | Splash Loading | 만료 세션 자동 제거 / 네트워크 불안정 시 재시도 | AUTH-002 |
| Google 로그인 | Google 로그인 버튼 클릭 및 계정 선택 | Google 인증 및 Firebase 사용자 인증 진행 | 로그인 성공 후 초기 설정 화면 이동 | 인증 취소 / Firebase 인증 실패 | Loading / Error | Play Services 상태 확인 / 인증 재시도 가능 | AUTH-001 |
| 초기 설정 | 관심사, 목표, 모국어, 주 학습 언어 설정 | User Profile / UserLangPref 저장 및 Language State 초기화 | 저장 완료 후 Dashboard 이동 | 저장 실패 시 재시도 버튼 표시 | Loading / Saving | 일부 데이터 저장 실패 시 rollback 처리 검토 | AUTH-004 |
| 로그아웃 | 로그아웃 클릭 | Google 세션 및 앱 세션 제거 | 로그아웃 완료 및 로그인 화면 이동 | 로그아웃 실패 시 에러 Toast 표시 | Loading / Error | 네트워크 끊김 | AUTH-003 |

---

# GitHub Issue (실행 기준 / SSOT)

예:

- AUTH-001 Google 로그인 구현
- AUTH-002 자동 로그인 구현
- AUTH-003 로그아웃 구현
- AUTH-004 초기 프로필 및 학습 언어 설정 저장 구현

---

# 초기 설정 데이터

## UserLangPref

온보딩 완료 시 앱의 기본 학습 언어 컨텍스트를 함께 저장한다.

```json
{
  "nativeLanguage":"ko",
  "primaryLearningLanguage":"en",
  "selectedLearningLanguage":"en",
  "learningLanguages":["en"]
}
```

## 필드 역할

| 필드 | 역할 |
| --- | --- |
| nativeLanguage | 힌트, 교정 앞면, 제한적 모국어 보조에 사용 |
| primaryLearningLanguage | 사용자가 처음 선택한 주 학습 언어 |
| selectedLearningLanguage | Dashboard / AI Chat / Flashcard / Statistics가 현재 바라보는 언어 |
| learningLanguages | 사용자가 학습 중인 언어 목록 |

## 초기화 정책

- 온보딩 완료 시 `selectedLearningLanguage = primaryLearningLanguage`로 설정한다.
- `primaryLearningLanguage`에 해당하는 Language State와 Dashboard Summary 초기값을 생성한다.
- 사용자가 이후 Dashboard에서 언어를 변경하면 `selectedLearningLanguage`만 변경한다.

---

# 데모 시나리오 (Demo Scenario)

1. 앱 실행
2. 회원가입 진행
3. 관심사 / 목표 / 모국어 / 주 학습 언어 설정
4. Dashboard 화면 진입
5. 로그아웃 후 로그인 재진입 확인

---

# 디자인 (Figma, 필요 시)

- Intro Screen
- Login Screen
- Register Screen
- Initial Setup Screen (관심사, 목표, 모국어, 주 학습 언어 설정)
- Dashboard Screen

---

# 범위 제외 (Out of Scope)

- 비밀번호 찾기
- 다중 프로필 지원
