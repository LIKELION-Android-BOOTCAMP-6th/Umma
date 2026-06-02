# [Implementation] CHAT-TUNE-001-D Chat Prompt Integration

## 목적

AI Chat 시작과 재연결 시 저장된 `LangState`를 `LearnerAdaptationProfile`로 해석하고, 같은 profile 기반 system instruction을 사용하도록 연결한다.

이번 작업은 prompt 정책 적용만 다룬다.
OpenAI Realtime transport, SessionMemory 저장, usage tracking, final transcript, 마이크 버튼 상태는 변경하지 않는다.

---

# 포함 범위

- `BuildPromptUseCase`가 raw `LangState.external.vocabularyLevel.name` 중심이 아니라 profile 기반 instruction을 생성하도록 변경
- `StartSessionUseCase`가 profile 기반 prompt를 사용
- `RetryConnectionUseCase`가 start와 같은 prompt 경로를 사용
- null/initial/low-confidence profile fallback 적용

# 제외 범위

- OpenAI Realtime transport 변경
- SessionMemory 저장 정책 변경
- usage tracking 변경
- final transcript 표시 정책 변경
- mic button UX 변경
- Correction prompt 실제 튜닝

---

# 책임 경계

- `BuildLearnerAdaptationProfileUseCase`: `LangState?`를 profile로 해석
- `BuildPromptUseCase`: profile을 Chat system instruction으로 변환
- `StartSessionUseCase`: 선택 언어의 LangState를 읽고 prompt 생성
- `RetryConnectionUseCase`: 같은 profile/prompt 경로로 재연결 prompt 생성
- `ChatRepositoryImpl`: 완성된 instruction 전달만 담당

---

# Prompt 적용 원칙

- raw score를 prompt에 직접 넣지 않는다.
- profile enum/policy를 instruction text로 변환한다.
- low confidence 상태에서는 beginner-safe 또는 conservative instruction을 사용한다.
- active focus가 있어도 한 번에 너무 많은 학습 초점을 넣지 않는다.
- Chat은 fluency와 confidence가 낮을수록 한 번에 하나의 짧은 질문을 우선한다.
- Stretch는 새 표현 1개 정도만 자연스럽게 도입하는 범위로 제한한다.

---

# Start / Retry 일관성

```text
StartSessionUseCase
→ observe current LangState
→ BuildLearnerAdaptationProfileUseCase
→ BuildPromptUseCase(profile)
→ ChatRepository.startSession(instruction)

RetryConnectionUseCase
→ observe current LangState
→ BuildLearnerAdaptationProfileUseCase
→ BuildPromptUseCase(profile)
→ ChatRepository.reconnectSession(instruction)
```

start와 retry가 서로 다른 prompt 정책을 쓰면 같은 세션에서 난이도가 바뀔 수 있으므로, 반드시 같은 builder 경로를 사용한다.

---

# 검증 기준

- `LangState == null`에서도 prompt 생성이 실패하지 않는다.
- `LangState.initial()`에서 A1 확정이 아니라 low-confidence conservative prompt가 생성된다.
- Chat start와 retry가 같은 profile 기반 prompt를 사용한다.
- prompt에 raw numeric metric이 직접 포함되지 않는다.
- transport, SessionMemory, usage tracking, final transcript, mic button 상태가 변경되지 않는다.
