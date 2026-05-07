Umma Project

🎙️ AI-Native English Tutor: Project Tasty
"Real-time i+1 Leveling English Conversation Service powered by Gemini Live"

본 프로젝트는 사용자의 영어 실력에 맞춘 실시간 대화 및 맞춤형 피드백을 제공하는 안드로이드 네이티브 애플리케이션입니다. 단순한 챗봇을 넘어, 최신 AI 기술과 학습 이론을 결합하여 실제 원어민과 대화하는 듯한 경험을 제공합니다.

🚀 Key Features
1. Real-time Audio Interaction (Toggle-based)
   Toggle-to-Talk: 사용자 편의성과 기술적 안정성을 고려한 토글 방식의 마이크 제어.

Low-Latency Streaming: PCM 오디오 데이터를 WebSocket을 통해 실시간으로 스트리밍하여 끊김 없는 대화 구현.

Barge-in Logic: AI 답변 도중 사용자가 말을 시작하면 즉시 재생을 중단하고 경청 상태로 전환.

2. i+1 Adaptive Learning
   Dynamic Leveling: 사용자의 현재 수준보다 한 단계 높은 수준(i+1)의 문장과 어휘를 선택하여 최적의 학습 효율 제공.

Speed Control: 유저의 숙련도에 따라 AI의 발화 속도를 0.7x에서 1.5x까지 실시간 조절 가능.

3. Smart Feedback & SRS
   Instant Correction: 대화 종료 후 Gemini가 실시간으로 문법 교정 및 더 나은 표현 제안.

Spaced Repetition System (SRS): SuperMemo-2 알고리즘을 적용하여 교정된 문장을 최적의 타이밍에 복습하도록 관리.

🏗️ Architecture & Tech Stack
Architecture
Clean Architecture: 비즈니스 로직(Domain)을 외부 환경(UI, DB)으로부터 완전히 분리.

State-driven MVVM: 단일 상태 객체(UiState)를 활용한 단방향 데이터 흐름(UDF)으로 상태 불일치 방지.

Tech Stack
Language: Kotlin

UI: Jetpack Compose (Declarative UI)

Async: Coroutines & Flow (Asynchronous stream processing)

DI: Hilt (Dependency Injection)

Networking: OkHttp (WebSocket), Retrofit

Database: Room (Local), Firestore (Remote Sync)

AI Engine: Gemini Live API (Multimodal LLM)

Backend: Firebase Cloud Functions (Python)

📂 Project Structure (Interface-Driven)
Plaintext
app/src/main/java/com/project/tasty
├── di/                     # Dependency Injection Modules
├── domain/                 # Pure Kotlin Business Logic
│   ├── model/              # Domain Entities
│   ├── repository/         # Data Access Interfaces
│   └── usecase/            # Single Responsibility Logic
├── data/                   # Data Implementations
│   ├── repository/         # Repository Implementations
│   ├── source/             # Remote(Socket/API) & Local(Room) Sources
│   └── mapper/             # Data Mapping (DTO ↔ Entity)
└── presentation/           # UI & State Management
├── feature_chat/       # Voice Chat UI & ViewModel
├── feature_srs/        # Review System
└── component/          # Common Compose Components
🛠️ Getting Started
Prerequisites
Android Studio Ladybug 이상

JDK 17

Gemini API Key (via Google AI Studio)

Installation
이 저장소를 클론합니다.

local.properties 파일에 API Key를 설정하거나 Firebase Secret Manager를 연동합니다.

Gradle을 싱크하고 앱을 실행합니다.

👨‍💻 Team: Team 3 (Tasty)
Park Jaemin (Lead Developer): Architecture Design, Audio Pipeline, Gemini Live Integration

Team Member 1: UI/UX Implementation (Compose), Animation

Team Member 2: Data Persistence (Room/Firestore), SRS Algorithm

Team Member 3: Backend Proxy (Python Cloud Functions), Authentication