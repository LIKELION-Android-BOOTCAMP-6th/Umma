# [Improvement] CHAT-EMOJI-001 첫 대화 가이드와 자막 이모지 힌트

## 목적

`CHAT-EMOJI-001`은 사용자가 특정 `selectedLang`으로 처음 AI 음성 대화를 시작할 때, 자막 사용을 안내하고 화면 자막에 작은 이모지 힌트를 덧붙이는 UI 보조 작업이다.

이 작업은 AI가 말한 원문이나 저장되는 final transcript를 바꾸는 작업이 아니다.
OpenAI Realtime에서 받은 AI final transcript는 그대로 유지하고, 화면에 표시되는 `ChatSubtitleItem`에만 필요한 경우 이모지를 덧붙인다.
첫 대화 가이드는 사용자의 실제 실력을 낮게 단정하기 위한 장치가 아니라, 해당 언어에 대한 앱의 학습 데이터가 아직 없을 때 대화 진입 부담을 낮추기 위한 안내다.
가이드는 별도 대화 공간이 아니며, 사용자는 안내를 닫은 뒤 기존 Chat 화면에서 그대로 대화를 진행한다.

---

# User Story

사용자는 특정 학습 언어로 처음 대화를 시작할 때, 자막을 켜고 편하게 말해도 된다는 안내를 받는다.
사용자는 학습 언어를 거의 모르거나 단어 조각만 말해도, Umma의 응답 자막에 붙은 작은 이모지 힌트를 보고 대화의 의미를 더 쉽게 짐작할 수 있다.
Umma는 별도 퀴즈나 반복 따라 말하기를 강요하지 않고, 자연스러운 대화 흐름 안에서 단어와 상황을 시각적으로 조금 더 쉽게 연결해 준다.

---

# 완료 기준(AC)

- [ ] 사용자는 특정 `selectedLang`으로 처음 대화할 때 첫 대화 가이드 다이얼로그를 볼 수 있다.
- [ ] 첫 대화 가이드는 사용자가 자막을 켜고 편하게 대화하도록 안내한다.
- [ ] 첫 대화 가이드는 기존 Chat 화면의 자막 버튼 사용법을 텍스트로 안내한다.
- [ ] 첫 대화 가이드는 이후 교정/플래시카드 저장을 통해 해당 언어능력 데이터가 생기면 다시 열리지 않는다는 점을 안내한다.
- [ ] 첫 대화 가이드 표시 여부는 별도 완료 flag가 아니라 `selectedLang`의 의미 있는 LangState 데이터 존재 여부로 판단한다.
- [ ] 첫 대화 가이드를 닫아도 사용자는 기존 Chat 화면에서 대화를 계속할 수 있다.
- [ ] 첫 대화 가이드 세션 또는 초저숙련 대화에서는 AI 자막에 이모지 힌트가 표시될 수 있다.
- [ ] 첫 대화 가이드 세션에서는 사용자가 fluent하더라도 `selectedLang` 학습 데이터가 없으면 이모지 힌트가 허용된다.
- [ ] `IntentOnly`와 `PhraseEmerging` 수준의 대화에서는 구체적인 사물, 감정, 장소, 행동이 있을 때만 이모지가 표시된다.
- [ ] `SimpleSentence` 이상 단계에서는 기본적으로 이모지 힌트가 표시되지 않는다.
- [ ] 이모지는 AI final transcript 원문에 저장되지 않는다.
- [ ] 이모지는 SessionMemory, Correction 후보 추출, usage tracking 데이터에 섞이지 않는다.
- [ ] 한 자막에는 이모지를 1개만 표시한다.
- [ ] 이모지는 단어를 대체하지 않고 의미를 보조한다.
- [ ] 이모지 적용 실패나 매핑 누락이 있어도 기존 자막은 그대로 표시된다.
- [ ] 기존 OpenAI Realtime transport, final transcript 수신, 마이크 상태, AI 음성 재생 흐름은 변경하지 않는다.

---

# 관련 문서

- [CHAT-FIX-001-D final 자막 대화형 표시](./CHAT-FIX-001/CHAT-FIX-001-D_Final_Subtitle_Conversation_UX.md)
- [CHAT-TUNE-002 대화 능력 기반 Prompt 세분화 전략](./CHAT-TUNE-002_Conversation_Ability_Prompt_Strategy.md)

---

# 범위

## 포함

- selectedLang별 첫 대화 가이드 다이얼로그
- AI final subtitle의 화면 표시용 이모지 힌트
- 첫 대화 가이드 세션, `IntentOnly`, `PhraseEmerging` 단계 중심의 적용 조건
- 로컬 이모지 매핑 테이블
- subtitle 표시용 변환 정책
- 자막 표시와 저장 데이터 분리 검증

## 제외

- AI prompt에 이모지를 직접 넣도록 지시하는 작업
- AI 음성 응답에 이모지를 포함하는 작업
- 외부 이미지 API, Iconify API, cue card 다이얼로그
- SessionMemory 저장 모델 변경
- Correction 후보 추출 정책 변경
- OpenAI Realtime transport 변경
- 사용자 subtitle 이모지 적용
- 전체 온보딩 플로우 개편
- 교정 또는 플래시카드 저장 기능 구현 변경

---

# 핵심 결정

- 문서와 코드 명칭은 `Emoji` 철자를 사용한다.
- 이모지는 AI가 생성한 transcript에 포함시키지 않는다.
- 이모지는 화면 표시용 `ChatSubtitleItem.text`에만 적용한다.
- 저장 원문과 표시 문구를 분리해, 학습 데이터와 교정 데이터가 이모지로 오염되지 않게 한다.
- 첫 selectedLang 대화에서는 실제 사용자가 fluent하더라도 학습 데이터가 없으므로 자막과 이모지 힌트를 켤 수 있다.
- 첫 대화 이후에는 사용자의 대화 능력 정책에 따라 초저숙련 단계에서만 이모지 힌트를 유지한다.
- 첫 대화 가이드 표시 여부는 `selectedLang`의 LangState 데이터가 있는지로 판단한다.
- 첫 대화 가이드는 기존 Chat 화면 진입 전에 보여주는 안내일 뿐이며, 대화 자체는 기존 Chat 화면에서 진행한다.
- 첫 대화 가이드는 `CHAT-TUNE-002`의 first selectedLang fallback 정책과 연결된다.
- `low confidence`는 이모지 힌트 허용 조건으로 사용할 수 있지만, 단독으로 첫 대화 가이드 다이얼로그를 반복 실행시키지 않는다.
- 별도 이미지 다이얼로그보다 자막 안의 작은 이모지 힌트를 먼저 검증한다.
- 외부 이미지 로딩보다 로컬 이모지 매핑을 먼저 사용한다.

---

# 첫 대화 가이드

첫 대화 가이드는 사용자가 특정 `selectedLang`으로 처음 AI Chat을 시작할 때 보여주는 짧은 안내 다이얼로그다.
이 다이얼로그는 사용자의 실제 언어 능력을 낮게 판정했다는 뜻이 아니라, 해당 언어에 대한 앱의 LangState 데이터가 아직 없다는 뜻이다.
사용자는 이 다이얼로그 안에서 대화하지 않는다.
다이얼로그를 닫으면 기존 Chat 화면에서 음성 대화를 시작한다.

1차 가이드는 3페이지 정도의 짧은 안내로 구성한다.

| 페이지 | 목적 | 안내 방향 |
| --- | --- | --- |
| 1 | 첫 대화 안내 | `selectedLang`으로 처음 대화한다는 점을 알려준다. |
| 2 | 자막 사용 안내 | 이번 대화에서는 자막을 켜고 편하게 말해도 된다고 안내한다. |
| 3 | 다음 단계 안내 | 대화 후 교정/플래시카드 저장을 통해 언어능력 데이터가 생기면 첫 대화 가이드가 다시 열리지 않는다고 안내한다. |

예시 문구:

```text
1. selectedLang으로 처음 대화하시는군요.
2. 이번 대화는 자막을 켜고 편하게 대화해 주세요.
3. 이후 교정과 플래시카드 저장으로 언어능력 데이터가 생기면 첫 대화 가이드가 다시 열리지 않습니다.
```

안내 정책:

- 첫 대화 가이드는 자막 상태를 직접 바꾸지 않는다.
- 가이드 문구는 기존 Chat 화면의 자막 버튼을 눌러 자막을 켤 수 있다고 안내한다.
- 사용자가 가이드를 닫으면 기존 Chat 화면으로 돌아간다.
- 사용자가 같은 화면 세션에서 가이드를 닫은 경우, 화면 재구성 등으로 같은 안내가 즉시 반복 표시되지 않게 한다.

첫 대화 가이드 트리거:

- selectedLang별 첫 AI Chat 진입이다.
- 해당 selectedLang의 의미 있는 LangState 데이터가 없다.
- 해당 selectedLang의 LangState가 없거나 initial 상태다.

`meaningful LangState`가 없는 상태:

이 기준은 [CHAT-TUNE-002 대화 능력 기반 Prompt 세분화 전략](./CHAT-TUNE-002_Conversation_Ability_Prompt_Strategy.md)의 첫 selectedLang 대화 fallback 기준과 동일하게 해석한다.

- selectedLang의 `LangState`가 없다.
- `LangState.lastAnalyzedAt`이 없다.
- `LangState.analysisMeta.metricEvidence`가 비어 있다.
- 주요 `LangState.internal` metric이 모두 초기값에 가깝다.

첫 대화 가이드가 표시되지 않는 조건:

- 해당 selectedLang에 의미 있는 LangState 데이터가 있다.
- 교정/플래시카드 저장 흐름을 통해 해당 selectedLang의 언어능력 데이터가 생성되어 있다.
- 즉, 별도 `firstChatGuideCompleted` flag보다 LangState 데이터 존재 여부를 기준으로 판단한다.

반복 방지:

- `low confidence`만으로 첫 대화 가이드 다이얼로그를 다시 열지 않는다.
- 첫 대화 가이드 완료 후에도 분석 confidence가 낮을 수 있으므로, 다이얼로그와 이모지 힌트 조건은 분리한다.
- 사용자가 같은 Chat 화면 세션 안에서 다이얼로그를 닫은 경우에는 화면 재구성 등으로 같은 안내가 즉시 반복 표시되지 않게 한다.
- 같은 화면 세션의 반복 방지는 저장 모델이 아니라 ViewModel/UI transient state로 처리한다.

---

# 이모지 힌트 적용 조건

이모지 힌트는 아래 조건을 만족할 때 적용한다.

1. 대상 말풍선이 AI final subtitle이다.
2. 현재 대화가 첫 대화 가이드 세션이거나 초저숙련 단계다.
3. 첫 대화 가이드 세션에서는 사용자가 fluent하게 말하더라도 이모지 힌트를 허용한다.
4. 첫 대화 이후의 초저숙련 단계는 `IntentOnly` 또는 `PhraseEmerging`이다.
5. AI 응답 또는 직전 사용자 발화에서 구체적인 cue 후보를 찾을 수 있다.
6. 이미 같은 subtitle에 이모지가 붙지 않았다.

적용 판단 원칙:

- `ChatViewModel`은 raw `LangState` metric을 직접 읽어 이모지 대상 여부를 판단하지 않는다.
- 첫 대화 가이드 세션 여부와 `IntentOnly` / `PhraseEmerging` 판단은 `CHAT-TUNE-002`의 band/fallback 결과를 사용한다.
- 이 문서의 이모지 정책은 `CHAT-TUNE-002`가 만든 Chat adaptation 결과를 화면 표시용 subtitle에 적용하는 후속 UI 정책이다.

`low confidence`의 역할:

- `low confidence`는 이모지 힌트를 허용할 수 있는 보조 조건이다.
- `low confidence`는 첫 대화 가이드 다이얼로그를 반복 실행하는 조건이 아니다.
- 분석 근거가 부족할 때 사용자를 실제 초저숙련으로 단정하지 않고, 보수적인 자막 보조를 제공한다.

---

# 표시 방식

원문은 보존하고, 화면 표시용 자막에만 이모지를 붙인다.

```text
AI final transcript:
You want an apple? Are you hungry?

SessionMemory 저장:
You want an apple? Are you hungry?

화면 AI 자막:
You want an apple? 🍎 Are you hungry?
```

이모지는 문장 끝에 무조건 붙이지 않는다.
가능하면 의미가 연결되는 단어 가까이에 붙이되, 구현이 복잡해지면 1차에서는 문장 끝에 1개만 붙여도 된다.

```text
1차 허용:
You want an apple? Are you hungry? 🍎

추후 개선:
You want an apple? 🍎 Are you hungry?
```

---

# Emoji cue 후보

1차 매핑은 외부 검색이나 API 호출을 쓰지 않고 로컬 `EmojiCueDictionary`로 시작한다.
모든 단어를 매핑하지 않고, 첫 대화와 초저숙련 대화에서 자주 쓰이는 핵심 개념만 관리한다.
외부 검색을 쓰지 않는 이유는 대화 중 즉시성이 중요하고, 엉뚱한 이모지가 붙으면 오히려 의미 이해를 방해할 수 있기 때문이다.

| 범주 | 예시 cue | 이모지 |
| --- | --- | --- |
| 음식 | apple, banana, rice, bread | 🍎 🍌 🍚 🍞 |
| 음료 | water, milk, coffee | 💧 🥛 ☕ |
| 감정 | happy, sad, angry, tired | 😊 😢 😠 😴 |
| 장소 | home, school, store, park | 🏠 🏫 🏪 🌳 |
| 사람 | mom, dad, friend, teacher | 👩 👨 🧑‍🤝‍🧑 🧑‍🏫 |
| 행동 | eat, drink, sleep, walk | 🍽️ 💧 😴 🚶 |
| 날씨 | sunny, rainy, cold, hot | ☀️ 🌧️ 🥶 🥵 |
| 몸 상태 | hungry, sick, hurt | 🍽️ 🤒 🤕 |

추상 단어는 1차에서 제외한다.

- good
- bad
- difficult
- interesting
- awkward
- grammar
- nuance

확장 원칙:

- 1차는 핵심 cue 30~50개 정도로 시작한다.
- 영어/일본어를 동시에 확장할 수 있도록 cue별 alias를 둘 수 있다.
- alias는 selectedLang 단어뿐 아니라 primaryLang 단어도 포함할 수 있다.
- 다만 첫 구현에서는 너무 많은 언어를 한 번에 늘리지 않고, 실제 테스트가 가능한 범위부터 시작한다.

예상 구조:

```text
APPLE:
- aliases: apple, 사과, りんご, リンゴ
- emoji: 🍎

WATER:
- aliases: water, 물, みず, 水
- emoji: 💧
```

---

# 책임 경계

## `LearnerAdaptationProfile` 또는 후속 Chat profile

- 사용자가 이모지 힌트 대상인지 판단할 수 있는 정책 값을 제공한다.
- raw `LangState` metric을 presentation layer가 직접 해석하지 않게 한다.
- `IntentOnly`, `PhraseEmerging`, first selectedLang session, low confidence 같은 판단은 domain 정책에서 정한다.
- `CHAT-EMOJI-001`은 이 결과를 받아 화면 출력용 subtitle에만 이모지를 붙인다.

## 첫 대화 가이드 상태

- selectedLang의 LangState 데이터 존재 여부로 첫 대화 가이드 표시 여부를 판단한다.
- 별도 첫 대화 가이드 완료 flag를 만들지 않는다.
- 같은 화면 세션에서 다이얼로그가 반복 표시되지 않도록 transient UI state만 둔다.
- 다이얼로그 표시 여부와 이모지 힌트 표시 여부를 같은 상태로 뭉치지 않는다.

## `ChatViewModel`

- final transcript를 화면 표시용 `ChatSubtitleItem`으로 변환할 때 이모지 힌트를 적용한다.
- 저장용 final transcript는 변경하지 않는다.
- 이모지 매핑 실패 시 원문 자막을 그대로 사용한다.

## `ChatScreen`

- `ChatSubtitleItem.text`를 그대로 렌더링한다.
- 이모지 정책을 직접 판단하지 않는다.

## `ChatRepositoryImpl`

- OpenAI Realtime event를 기존처럼 `AIEvent.FinalTranscription`으로 전달한다.
- 이모지 정책을 알지 않는다.

## `SessionMemoryRepository`

- final transcript 원문만 저장한다.
- 화면 표시용 이모지 문자열을 저장하지 않는다.

## `Correction`

- 교정 후보 추출에는 이모지가 없는 원문 transcript를 사용한다.
- 이모지 cue는 교정 데이터가 아니라 presentation hint로만 취급한다.

---

# 구현 방향

1. selectedLang별 첫 대화 가이드 표시 판단과 같은 화면 세션의 반복 방지 상태를 정의한다.
2. Chat 진입 시 selectedLang의 의미 있는 LangState 데이터가 있는지 확인한다.
3. LangState 데이터가 없거나 initial이면 첫 대화 가이드 다이얼로그를 보여준다.
4. 같은 화면 세션에서 이미 닫은 다이얼로그는 다시 표시하지 않는다.
5. subtitle 표시용 변환 함수에 `SubtitleEmojiDecorator`를 추가한다.
6. decorator는 AI subtitle인지 먼저 확인한다.
7. decorator는 현재 Chat adaptation 상태 또는 첫 대화 가이드 세션이 이모지 대상인지 확인한다.
8. decorator는 AI final transcript와 직전 사용자 final transcript에서 cue 후보를 찾는다.
9. cue 후보가 있으면 가장 구체적인 이모지 1개만 고른다.
10. 이모지를 붙인 표시용 문자열을 `ChatSubtitleItem.text`에 넣는다.
11. `persistFinalTurn(event)`에는 원본 `event.text`를 그대로 전달한다.

예상 흐름:

```text
AIEvent.FinalTranscription(text)
→ persistFinalTurn(event.text)
→ toSubtitleItem(displayText = decorateForDisplay(event.text))
→ ChatScreen
```

---

# 실패/예외 처리

- cue 후보가 없으면 원문 자막만 표시한다.
- 매핑 테이블에 단어가 없으면 원문 자막만 표시한다.
- 이모지 대상 단계인지 판단할 수 없으면 원문 자막만 표시한다.
- 사용자 subtitle에는 이모지를 붙이지 않는다.
- 이미 이모지가 포함된 AI transcript가 들어오면 표시용 decorator가 추가 이모지를 붙이지 않는다.
- low confidence가 계속 유지되어도 첫 대화 가이드 다이얼로그가 반복해서 열리면 안 된다.
- 같은 화면 세션 안에서 다이얼로그를 닫은 뒤에는 LangState가 아직 없더라도 즉시 반복 표시하지 않는다.
- LangState 확인에 실패해도 AI Chat 자체는 계속 사용할 수 있어야 한다.
- 긴 자막의 스크롤 정책은 기존 `CHAT-FIX-001-D` 기준을 유지한다.

---

# 테스트 방법

- selectedLang 첫 AI Chat 진입 시 첫 대화 가이드 다이얼로그가 표시되는지 확인한다.
- 첫 대화 가이드 다이얼로그가 3페이지 안내 흐름으로 표시되는지 확인한다.
- 첫 대화 가이드가 기존 Chat 화면의 자막 버튼 사용법만 안내하고 `showSubtitle`을 직접 변경하지 않는지 확인한다.
- 다이얼로그를 닫으면 기존 Chat 화면에서 대화를 계속할 수 있는지 확인한다.
- selectedLang의 LangState가 없거나 initial이면 첫 대화 가이드가 표시되는지 확인한다.
- `lastAnalyzedAt`이 없고 `analysisMeta.metricEvidence`가 비어 있으면 의미 있는 LangState가 없는 것으로 처리되는지 확인한다.
- selectedLang의 의미 있는 LangState가 있으면 첫 대화 가이드가 표시되지 않는지 확인한다.
- 같은 화면 세션에서 다이얼로그를 닫은 뒤 즉시 반복 표시되지 않는지 확인한다.
- low confidence만으로 첫 대화 가이드 다이얼로그가 반복 표시되지 않는지 확인한다.
- 첫 대화 가이드 세션에서는 fluent한 AI 응답에도 구체 cue가 있으면 화면 자막에 이모지가 표시되는지 확인한다.
- `IntentOnly` 상태의 AI final subtitle에 `apple`이 포함되면 화면 자막에 `🍎`가 표시되는지 확인한다.
- `PhraseEmerging` 상태의 AI final subtitle에 `hungry`가 포함되면 화면 자막에 음식 관련 이모지가 1개만 표시되는지 확인한다.
- `SimpleSentence` 이상 상태에서는 같은 문장에도 이모지가 표시되지 않는지 확인한다.
- 사용자 final subtitle에는 이모지가 표시되지 않는지 확인한다.
- SessionMemory에 저장된 turn text에는 이모지가 포함되지 않는지 확인한다.
- Correction 후보 추출 입력에는 이모지가 포함되지 않는지 확인한다.
- cue 매핑이 없는 문장에서도 자막 표시가 실패하지 않는지 확인한다.
- 이미 이모지가 포함된 transcript에 중복 이모지가 붙지 않는지 확인한다.

---

# 검증 기준

- 문서 기준으로 첫 대화 가이드와 이모지 힌트가 분리되어 있다.
- 문서와 코드에서 `Emoji` 철자를 사용한다.
- 이모지 힌트가 presentation-only 정책으로 분리되어 있다.
- 저장 원문과 화면 표시 문구의 source of truth가 혼동되지 않는다.
- Chat prompt가 이모지 생성을 직접 지시하지 않는다.
- `ChatRepositoryImpl`, OpenAI Realtime transport, SessionMemory 저장 흐름이 변경되지 않는다.
- `ChatViewModel`이 화면 표시용 변환 책임만 갖고, raw `LangState` metric을 직접 해석하지 않는다.
- `ChatScreen`은 이모지 정책을 모르고 문자열 렌더링만 담당한다.
- selectedLang 첫 대화에서는 데이터 부족을 보완하기 위해 이모지 cue가 허용된다.
- 첫 대화 이후에는 이모지 cue가 초저숙련 사용자의 이해 보조로만 쓰이고, 중급 이상 대화에는 과하게 노출되지 않는다.
