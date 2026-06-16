# 🍼 Umma (움마)

## 🌱 프로젝트 소개

> "아기는 문법을 배워서 말하지 않습니다. '말하고 싶어서' 배웁니다."
>
> AI와의 실제 대화를 기반으로, 사용자의 언어 수준을 분석하고 교정하며, 반복학습까지 연결하는 **초개인화 언어 성장 서비스**

---

## 📖 서비스 배경

대한민국 평균 영어 학습 기간은 10년이 넘지만, EF EPI 기준 한국은 **막대한 학습 시간 대비 실제 의사소통 능력이 정체된 국가**로 분류됩니다. 우리는 공부를 안 한 게 아니라, **'말하는 법'을 배우지 않았습니다.**

기존 AI 영어 학습 앱들은 사용자의 실제 말하기 수준을 정밀하게 진단하지 못한 채 사전에 준비된 스크립트 기반 학습을 제공합니다. 학습한 표현이 반복되지 않아 쉽게 잊히고, 장기 기억으로 연결되지 못합니다.

Umma는 **"대화 → 교정(Correction) → 저장 → 반복학습 → 성장 추적"** 의 통합 루프를 통해, 학습자의 실제 발화를 기반으로 한 초개인화된 언어 성장 경험을 제공합니다.

---

## 🎯 대상 사용자

| 유형 | 설명 |
|------|------|
| 실전형 학습자 | 영어를 공부한 경험은 많지만 실제 말하기에 어려움을 느끼는 20~30대 직장인 |
| 꾸준형 학습자 | 학원이나 강의보다 가볍고 꾸준히 이어갈 수 있는 학습 방식을 선호하는 분 |
| 성장형 학습자 | 자신의 언어 성장 과정을 데이터로 체감하며 동기부여를 얻고 싶은 분 |

---

## ✨ 핵심 기능

### 1. AI 자유 회화 (Gemini Live) 🗣️

- **관심사 기반 주제 추천**: 사용자의 관심사를 반영하여 AI가 먼저 대화를 리드합니다.
- **수준 맞춤 대화**: 사용자의 현재 언어 수준보다 약간 높은(i+1) 난이도로 자연스럽게 대화를 이어갑니다.
- **실시간 음성 대화**: Firebase AI Logic 기반 Gemini Live로 양방향 음성 대화를 제공합니다.
- **음성 중심 UX**: MVP에서는 Push-to-Talk 방식으로 발화 시점을 명확히 제어합니다.

### 2. 문장 교정 (Correction) ✏️

- **통합 교정 파이프라인**: 대화 종료 후 세션 메모리의 `recentFullContext`에서 사용자 발화 후보를 추출하고 AI 교정을 생성합니다.
- **정교한 분석**: 단순히 틀린 곳을 찾는 것이 아니라, 사용자의 `LangState`를 고려하여 더 자연스러운 표현(`CorrectionSuggestion`)을 제안합니다.
- **선택적 저장**: 기억하고 싶은 교정 결과만 골라 플래시카드로 저장하며, 저장 시 세션 압축 및 상태 업데이트가 자동으로 이루어집니다.

### 3. Dashboard 📊

- **현재 학습 상태 요약**: 현재 선택 언어의 최근 대화, 교정 대기, 복습 카드, 성장 지표를 한 화면에서 확인합니다.
- **언어별 학습 컨텍스트**: `selectedLearningLanguage` 기준으로 Dashboard, AI Chat, 교정, Flashcard, Statistics가 같은 언어 컨텍스트를 공유합니다.
- **빠른 렌더링**: Dashboard는 원본 transcript를 직접 계산하지 않고 `DashSummary` 기반으로 렌더링합니다.

### 4. SRS 기반 반복학습 🃏

- **5단계 자기 평가**: MVP에서는 사용자가 직접 회상 후 5단계 버튼으로 기억 정도를 평가합니다.
- **복습 간격 조정**: 평가 결과에 따라 `interval`, `ease_factor`, `next_review_at`을 갱신합니다.
- **액티브 리콜**: 단순 재노출이 아닌 능동적 인출 연습으로 장기 기억화를 돕습니다.

### 5. 성장 추적 📈

Language State는 내부 분석용 지표와 사용자 통계 표시용 지표를 분리합니다.

- **Internal Metrics**: AI 대화 적응과 교정 분석에 사용하는 세부 지표
- **External Metrics**: Vocabulary Level, Grammar Accuracy, Expression Range, Fluency Score, Naturalness처럼 사용자가 직관적으로 볼 수 있는 성장 지표
- **비용 최적화**: 코드 계산/규칙 기반/AI 분석 지표를 분리하여 필요한 경우에만 AI 분석을 수행합니다.

---

## 📂 Project Structure

```text
├── .github/
│   ├── ISSUE_TEMPLATE/      # GitHub Issue 템플릿
│   └── pull_request_template.md
├── docs/                    # 설계 및 기획 문서 (System/User FlowDB)
│   ├── Demo/                # 데모 시나리오 및 테스트 시트
│   ├── System_FlowDB/       # 시스템 인프라 설계 문서
│   ├── User_FlowDB/         # 사용자 플로우 설계 문서
│   ├── drawio/              # 문서 이해를 돕는 draw.io 도식 원본
│   └── handover/            # 도메인 간 인계/후속 작업 문서
└── app/src/main/java/com/app/umma
    ├── core/                # 앱 전역 공용 모듈
    │   ├── navigation/      # 라우트, 네비게이션 호스트, 바텀바
    │   ├── theme/           # 색상, 타이포그래피, 테마
    │   ├── ui/              # 공통 UI 컴포넌트
    │   └── util/            # 공통 유틸리티
    ├── di/                  # 의존성 주입(Hilt) 모듈
    ├── domain/              # 순수 코틀린 비즈니스 로직
    │   ├── audio/           # 음성 입출력 도메인 계약
    │   ├── model/           # 도메인 모델
    │   │   ├── correction/  # 교정 후보 / 제안 / 결과 모델
    │   │   ├── flashcard/   # 플래시카드 / SRS 모델
    │   │   ├── learningstate/ # 학습 상태 / 대시보드 요약 / 유저 언어 설정
    │   │   ├── realtime/    # Session Memory / turn 모델
    │   │   ├── statistics/  # 통계 history / chart 모델
    │   │   └── user/        # 사용자 프로필 모델
    │   ├── repository/      # 저장소 인터페이스
    │   └── usecase/         # 유즈케이스 (비즈니스 규칙)
    │       ├── auth/
    │       ├── chat/
    │       ├── correction/
    │       ├── flashcardreview/
    │       ├── learningstate/
    │       ├── realtime/
    │       ├── statistics/
    │       └── user/
    ├── data/                # 데이터 계층 구현부
    │   ├── model/           # DTO / Entity 변환 모델
    │   ├── repository/      # 저장소 구현체와 fake repository
    │   └── source/
    │       ├── local/       # DataStore, Room, 기기 자원
    │       └── remote/      # Firebase, Google 인증, Gemini Live 연동
    └── presentation/        # UI 및 상태 관리
        ├── auth/            # 로그인, 앱 진입부
        ├── chat/            # AI 음성 채팅 화면 및 ViewModel
        ├── correction/      # 문장 교정 화면
        ├── dashboard/       # 대시보드 화면 및 요약 카드
        ├── srsstudy/        # 플래시카드 기반 SRS 반복학습 화면
        ├── statistics/      # 학습 통계 화면 및 chart 상태
        └── util/            # presentation 공통 유틸리티
```

---

## 💎 차별점

기존 AI 영어 학습 서비스는 "대화"와 "복습"이 분리되어 있고, 사용자의 실제 수준을 정밀하게 파악하지 못합니다. Umma는 다음과 같이 차별화됩니다.

- **하나의 통합 루프**: 대화 → 교정 → 저장 → 반복학습 → 성장 추적이 끊김 없이 연결됩니다.
- **사용자 발화 기반 학습**: 사전 스크립트가 아닌 사용자의 실제 발화에서 학습 데이터가 생성됩니다.
- **장기적 언어 상태 추적**: 일회성 평가가 아닌 사용자의 Language State를 누적 분석합니다.
- **욕구 중심 학습**: "공부"가 아닌 "말하고 싶은 욕구" 중심의 학습 경험을 제공합니다.

---

## 🖥️ 시연 영상

- **시연 영상**: [유튜브](https://youtu.be/LWJdo2ZqIyY?si=2CWgZk3H032pK4Xi)

---

## 📆 개발 인원 및 기간

- **개발 기간**: 2026.5.4 ~ 2026.6.17
- **개발 인원**: Android 4명

---

## 🙌 팀원 소개

| 이름 | GitHub |
|------|--------|
| 박재민 | [woals6318-hash](https://github.com/woals6318-hash) |
| 정원화 | [sangsangcat](https://github.com/sangsangcat) |
| 김명준 | [jssmt247-crypto](https://github.com/jssmt247-crypto) |
| 김태환 | [taehwan-dev](https://github.com/taehwan-dev) |

---

## 📖 팀원 역할

| 코드 | 역할 | 담당 기능 |
|------|------|-----------|
| F1 | 인증·세션 | 소셜 로그인, 자동 로그인, 로그아웃, 회원 탈퇴 |
| F2 | 앱 골격·네비·디자인 시스템 | 패키지 구조, Navigation Graph, 공통 테마/컴포넌트 |
| F3 | UI/UX 디자인 | 사용자 플로우 설계, 와이어프레임 제작, Figma 프로토타이핑, 브랜드 컬러 및 아이덴티티 구축 |
| F4 | AI 회화 | Firebase AI Logic / Gemini Live 연동, 실시간 음성 입출력 |
| F5 | 문장 교정 (Correction) | 세션 맥락 기반 문장 추출 및 AI 교정 파이프라인 구현 |
| F6 | 플래시카드 & SRS | 카드 저장(Room), 5단계 자기 평가 기반 복습 스케줄링 |
| F7 | 성장 통계 & 마이페이지 | 언어 데이터 분석 시각화, 프로필·설정 |
| F8 | 워치 연동 (Wear OS) | 워치 알림 연동, PTT 기반 실시간 음성 대화(폰 Gemini Live 세션 중계) |

| 이름 | 담당 |
|------|------|
| 박재민 | F1, F2, F4, F8 |
| 정원화 | F3, F4, F7 |
| 김명준 | F1, F3, F6, F7 |
| 김태환 | F3, F5 |

---

## 🧑‍💻 Tech Stacks

### 🏗️ Environment

<img src="https://img.shields.io/badge/Android%20Studio-3DDC84?style=flat-square&logo=android-studio&logoColor=white"> <img src="https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white"> <img src="https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=GitHub&logoColor=white"> <img src="https://img.shields.io/badge/Figma-F24E1E?style=flat-square&logo=Figma&logoColor=white"> <img src="https://img.shields.io/badge/Notion-000000?style=flat-square&logo=Notion&logoColor=white">

### 🎨 UI & Jetpack

<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=Kotlin&logoColor=white"> <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=Android&logoColor=white"> <img src="https://img.shields.io/badge/Material3-757575?style=flat-square&logo=materialdesign&logoColor=white"> <img src="https://img.shields.io/badge/Navigation-3DDC84?style=flat-square&logo=Android&logoColor=white"> <img src="https://img.shields.io/badge/Hilt-3DDC84?style=flat-square&logo=Android&logoColor=white">

### 🌐 Infrastructure & Library

<img src="https://img.shields.io/badge/Gemini-8E75B2?style=flat-square&logo=googlegemini&logoColor=white"> <img src="https://img.shields.io/badge/Firebase%20Auth-FFCA28?style=flat-square&logo=Firebase&logoColor=white"> <img src="https://img.shields.io/badge/Cloud%20Firestore-FFCA28?style=flat-square&logo=Firebase&logoColor=white"> <img src="https://img.shields.io/badge/Cloud%20Functions-FFCA28?style=flat-square&logo=Firebase&logoColor=white"> <img src="https://img.shields.io/badge/Cloud%20Storage-FFCA28?style=flat-square&logo=Firebase&logoColor=white"> <img src="https://img.shields.io/badge/FCM-FFCA28?style=flat-square&logo=Firebase&logoColor=white"> <img src="https://img.shields.io/badge/OkHttp-3E4348?style=flat-square&logo=square&logoColor=white"> <img src="https://img.shields.io/badge/Retrofit2-48B983?style=flat-square&logo=square&logoColor=white"> <img src="https://img.shields.io/badge/Room-3DDC84?style=flat-square&logo=Android&logoColor=white"> <img src="https://img.shields.io/badge/Coroutines%20%26%20Flow-7F52FF?style=flat-square&logo=Kotlin&logoColor=white"> <img src="https://img.shields.io/badge/Media3-3DDC84?style=flat-square&logo=Android&logoColor=white">

### ⚙️ Architecture

<img src="https://img.shields.io/badge/Clean%20Architecture-3DDC84?style=flat-square&logo=Android&logoColor=white"> <img src="https://img.shields.io/badge/MVVM-3DDC84?style=flat-square&logo=Android&logoColor=white"> <img src="https://img.shields.io/badge/Layered%20Package-3DDC84?style=flat-square&logo=Android&logoColor=white"> <img src="https://img.shields.io/badge/Hilt%20(DI)-3DDC84?style=flat-square&logo=Android&logoColor=white">

### 🔧 상세 구성

| 분류 | 기술 | 용도 |
|------|------|------|
| Language | Kotlin | - |
| UI Framework | Jetpack Compose | 선언형 UI |
| Async | Coroutines & Flow | 비동기 처리 및 반응형 데이터 스트림 |
| Architecture | MVVM + Clean Architecture | 책임 분리 및 테스트 가능한 구조 |
| DI | Hilt | 의존성 주입 |
| Local DB | Room | SRS 플래시카드 및 오프라인 대화 로그 저장 |
| Audio Engine | AudioRecord / AudioTrack (또는 Media3 ExoPlayer) | 음성 입출력 처리 |
| AI | Google Gemini with Firebase AI Logic | 실시간 음성 대화 모델 (Gemini Live) |
| Backend | Firebase | Auth, Firestore, Storage, Cloud Functions, FCM |

---

## 🏗️ 앱 구조 / 아키텍처

Umma는 책임 분리와 테스트 용이성을 위해 **Clean Architecture + MVVM** 패턴을 기반으로 설계되었습니다.

- **UI Layer (Composable)**: Jetpack Compose로 구성되며, ViewModel의 State를 관찰하여 자동으로 화면을 갱신합니다.
- **ViewModel**: State & Event를 분리하여 관리하고, UseCase를 호출해 비즈니스 로직을 처리합니다.
- **Domain Layer (UseCase)**: 비즈니스 규칙을 캡슐화하고, Repository 인터페이스에만 의존하여 계층 간 결합도를 낮춥니다.
- **Data Layer (Repository)**: Repository 패턴으로 외부 데이터 소스(Gemini Live, Firebase, Room)와의 통신을 추상화합니다.
- **DI (Hilt)**: 객체 생성과 주입을 자동화하여 모듈 간 의존성을 분리합니다.

```
UI (Composable) → ViewModel → UseCase → Repository(interface)
                                            ↓
                                       RepositoryImpl
                                            ↓
                            ┌───────────────┼─────────────────┐
                       Gemini Live      Firebase            Room
                     (Firebase AI Logic)    (Firestore/Storage)   (Local DB)
```

### 🎙️ 실시간 음성 스트리밍 구조

Firebase AI Logic에서 제공하는 Gemini Live와의 실시간 음성 대화를 위해 다음과 같은 스트리밍 파이프라인을 구성합니다.

- **Audio Input**: `AudioRecord`로 사용자 음성을 캡처하여 청크 단위로 서버에 전송합니다.
- **Audio Output**: `AudioTrack`으로 AI 응답 음성을 재생합니다.
- `AudioTrack`: 저레벨 API로 latency가 적지만, 버퍼·포맷을 수동 관리해야 함 (커스텀 자유도 ↑)
- **대화 주도권 전환**: MVP 단계에서는 **버튼 방식(Push-to-Talk)**으로 발화 시점을 명시적으로 제어하고, 추가 개발 기간에 **자동 감지(Barge-in) 방식**으로 전환할 예정입니다.

### 🧠 학습 데이터 구조

Umma의 학습 데이터는 언어별로 분리해서 관리합니다.

```text
users/{uid}
├── user_learning_preference/current
├── language_states/{lang}
├── dashboard_summaries/{lang}
├── session_summaries/{lang}
├── sessions/{lang} (recentFullContext 포함)
├── flashcards/{cardId}
└── statistics_history/{historyId}
```

- `lang`: Session, Flashcard, Statistics, Language State가 어떤 언어의 데이터인지 나타내는 소속 필드
- `selectedLearningLanguage`: Dashboard와 기능 이동이 현재 바라보는 앱 전역 언어 컨텍스트
- Dashboard는 `DashSummary[selectedLearningLanguage]`만 사용하여 빠르게 렌더링합니다.
- AI Chat은 언어별 재사용 Session Memory에 확정 turn 단위로 대화 맥락을 저장하며, 이는 Correction의 핵심 소스가 됩니다.
- Correction 진입 판단은 `SessionSummary[selectedLearningLanguage].correctionAvailable`을 기준으로 하고, Dashboard 교정 대기 표시는 `DashSummary[selectedLearningLanguage].correctionAvailable`을 기준으로 합니다.
- Statistics는 `StatisticsHistory`를 local-first로 관찰하고, Firestore refresh / pending sync는 화면 실패와 분리해서 처리합니다.

---

## 📋 팀 컨벤션

### 💬 소통 규칙

- **오전 정기 회의 (09:00 ~ 09:50)**: 한 것 공유 + 할 것 공유 + PR & Merge
- **오후 정기 회의 (17:00 ~ 17:30)**: 진행 상황 점검 및 이슈 공유
- **직관적 소통**: 미사어구를 최소화하고 핵심, 요점, 의견 위주의 명확한 표현 사용
- **이모지 반응**: 팀원의 메시지/공지 확인 시 이모지로 피드백 필수
- **의견 충돌 시**: 근거 및 장단점 비교 후 다수결로 결정
- **연락 확인**: 매일 오후 6시 ~ 8시 사이 연락 확인
- **책임 범위가 애매할 시**: 즉시 팀원에게 질문

### 🤝 협업 규칙

#### 브랜치 전략
- `main`: 배포 가능 상태
- `develop`: 통합 개발
- `feature/기능명`: 개인 작업 브랜치 (예: `feature/login`)
- `common`: 공통 작업 브랜치 (예: 공통 컴포넌트, 표준 UI 등)

#### 커밋 컨벤션

| 태그         | 설명 |
|------------|------|
| `feat`     | 기능 추가 |
| `fix`      | 버그 수정 |
| `refactor` | 리팩토링 |
| `docs`     | 문서 수정 |
| `chore`    | 빌드 업무, 설정 변경 등 |

**커밋 메시지 템플릿**
```text
feat(#42) AI 음성 대화 실시간 스트리밍 기능 구현

### 요약
- PTT 방식의 음성 캡처 및 서버 전송 기능 추가

### 상세 내용
- 상세1: 사용자가 실시간으로 AI와 대화할 수 있는 음성 인터페이스 필요
- 상세2: AudioRecord를 활용해 16kHz 모노 PCM 데이터를 청크 단위로 스트리밍하도록 구현
- 핵심변경: ChatViewModel, AudioCaptureManager

### 관련 이슈
- Resolves: #42
```

#### PR 규칙
- PR 등록 시 팀원 1명 이상의 리뷰 필수
- 추가로 이상한 부분이나 궁금한 점이 생기면 코드 리뷰 남기기
- 본인 approve 금지
- Merge 전 반드시 빌드 확인
- 피드백은 코드를 기준으로 진행

**PR 템플릿**
```text
## Type (해당되는 타입만 남기고 삭제)
- Feat
- Fix
- Refactor
- Docs
- Chore

---

## Summary
- 

---

## Related Issue (작업에 관련된 이슈 반드시 태그)
- Closes #

---

## What’s Done
- 

---

## How to Test (테스트가 불필요한 작업일 경우 삭제 가능)
1. 

---

## Notes (예: 해당 작업 이후 공유되어야 할 점)
- 
```

**PR 작성 예시**
```text
## Summary
- SYS-LEARNING-STATE-INFRA 전반 구현
- Language State, Dashboard Summary, User Learning Preference, Global Learning State, Local Sync, Update Policy 구조 정리

---

## Related Issue
- Closes #27 
- Closes #28 
- Closes #29 
- Closes #30 
- Closes #31 
- Closes #32 

---

## What’s Done
- `LangState` / `DashSummary` / `UserLangPref` / `GlobalLangState` 모델 정리
- Local Cache / Firebase Sync 정책 정리
- Language State 업데이트 입력/계약/UseCase 구조 정리
- 중복 업데이트 방지 및 batch update 흐름 반영
- 관련 문서와 코드 네이밍 일관성 맞춤

---

## How to Test
1. `./gradlew :app:compileDebugKotlin` 실행
2. 학습 상태 관련 모델/유스케이스 구조 확인
3. LS-001 ~ LS-006 문서와 코드명이 일치하는지 확인

---

## Notes
- Repository는 저장만, 정책 계산은 UseCase에서 처리하는 방향으로 맞췄다.
- 대화세션 관련 모델과 로직은 AI 대화 Flow 작업 전에 추가 구현할 예정이다.
- MVP 기준으로 필요한 핵심 구조부터 정리했다.
```

### 💻 코드 규칙

- **파일 네이밍**: PascalCase 사용 (예: `SignInViewModel`)
- **패키지 구조**: 레이어 중심 Clean Architecture (`presentation`, `domain`, `data`, `core`, `di`)
- **아키텍처**: Clean Architecture + MVVM
- **상태 관리**: State 기반
- **공통 UI**: `colorScheme` 사용
- **가독성**: 혼자만 알아볼 수 있는 코드 작성 금지, 필요 시 주석으로 설명
- **커밋 전 정리 습관**:
    - 코드 줄맞춤: `Ctrl + Alt + L` (Mac: `⌥(Option) + ⌘(Command) + L`)
    - 미사용 Import 정리: `Ctrl + Alt + O` (Mac: `⌃(Control) + ⌥(Option) + O`)

### 📅 일정 / 작업 규칙

- **작업 단위**: 이틀 이상 걸리는 작업은 분리하여 관리
- **완료 기준**: 기능 동작 + 예외 처리 + 코드 리뷰 완료 + 빌드 성공
- **타인 코드**: 함부로 수정 금지 (필요 시 담당자와 논의)

### 🌱 생활 규칙

- **지각/불참**: 최소 2시간 전 사전 공유
- **역할 책임**: 맡은 기능에 문제가 생기면 1시간 내로 팀에 공유

---

## 🚀 실행 방법

- Android Studio Otter 이상 권장
- JDK 17 이상
- Android SDK min 24 / target 36
- 프로젝트 오픈 후 Gradle Sync
- 에뮬레이터 또는 실기기에서 실행

## ⚙️ 환경 설정

- `google-services.json` 파일이 필요합니다.
- 로컬 키와 환경값은 커밋하지 않고 `local.properties`에서 관리합니다.
- Firebase / Gemini 관련 보안 키는 클라이언트에 직접 노출하지 않는 구조를 원칙으로 합니다.
- 실시간 음성 대화 테스트를 위해 마이크 권한 및 네트워크 환경이 필요합니다.

---

## 🚧 향후 확장 기능 (Future Plans)

- **자동 발화 감지 (Barge-in)**: MVP의 버튼 방식 대화 주도권을 자동 감지로 전환. 사용자 발화 감지 시 `audioTrack.flush()`를 호출하고 서버에 중단 신호 전송.
- **복습 알림**: FCM을 통해 복습 시점에 알림을 제공.
- **상황별 말투 모드**: Casual / Business / Travel 등 상황 기반 회화 모드
- **AI 페르소나 저장 및 선택**: 사용자가 선호하는 AI 캐릭터 저장 및 재사용
- **다국어 동시 지원**: 영어 외 일본어, 중국어 등 추가 언어 지원
- **학습 리포트 고도화**: 주간·월간 성장 리포트 및 약점 분석 자동 발송

---
