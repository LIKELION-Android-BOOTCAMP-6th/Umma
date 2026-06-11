# [UX] COR-UX-004 교정 결과 카드 스피커 TTS 연결(afterText)

## User Story

사용자는 교정 결과 카드의 **스피커 아이콘**을 누르면, 학습(SRS) 카드 뒷면과 **동일한 방식**으로 교정 후 문장(`afterText`)을 발음으로 들을 수 있다. 눈으로 본 교정문을 귀로도 확인해 자연스러운 발음을 익힌다.

---

# 배경

교정 카드의 스피커 아이콘은 현재 **동작하지 않는 정적 아이콘**이다.

확인된 현황:

- [CorrectionResultCard.kt:112-118](../../../../app/src/main/java/com/app/umma/presentation/correction/component/CorrectionResultCard.kt)의 헤더 스피커는 `onClick`이 없는 `Icon`이고, 주석에도 `정적 아이콘 — TTS 동작은 후속 backlog 에서 연결`, contentDescription `음성 듣기 (준비 중)`로 명시돼 있다.
- 반면 학습(SRS)은 **이미 동작하는 TTS**가 있다. [SrsStudyScreen.kt](../../../../app/src/main/java/com/app/umma/presentation/srsstudy/SrsStudyScreen.kt) 뒷면 스피커 `IconButton`이 `viewModel.onPlayPronunciation()`을 호출하고, [SrsStudyViewModel.kt:294-305](../../../../app/src/main/java/com/app/umma/presentation/srsstudy/SrsStudyViewModel.kt)가 `ttsController.setLanguage(lang)` 후 `ttsController.speak(card.backText)`를 호출한다.
- TTS 인프라는 [TextToSpeechController.kt](../../../../app/src/main/java/com/app/umma/core/tts/TextToSpeechController.kt)(`setLanguage(LangCode): Boolean`, `speak(text){onComplete}`)이고, [TtsModule.kt](../../../../app/src/main/java/com/app/umma/di/TtsModule.kt)로 **Singleton 주입**된다.

방향은 **"SRS와 동일한 TTS 경로를 교정 카드에 그대로 연결"** 이다. 새 TTS 인프라를 만들지 않고 기존 `TextToSpeechController`를 재사용한다. 읽을 문장은 학습 효과가 있는 **교정 후 문장 `afterText`**(학습 대상 언어)다 — SRS가 뒷면 `backText`(대상 언어)를 읽는 것과 같은 맥락이다(모국어 헤더 `nativeText`가 아니다).

---

# 완료 기준(AC)

- [ ] 교정 카드의 스피커 아이콘이 **탭 가능**해지고, 누르면 해당 카드의 `afterText`를 현재 학습 언어로 발음한다.
- [ ] 발음 언어는 `selectedLearningLanguage`로 설정한다. `setLanguage`가 실패하면(미지원 등) 재생하지 않는다(no-op).
- [ ] 재생 중인 카드의 아이콘은 **색이 바뀐다**(SRS와 동일: 재생 중 `ThemePrimary`). 재생이 끝나면 원래 색으로 돌아온다.
- [ ] 동작은 SRS 발음과 체감이 같다(같은 `TextToSpeechController`, 같은 `QUEUE_FLUSH` 동작).
- [ ] 새 TTS 매니저/엔진을 추가하지 않는다(기존 Singleton 재사용).
- [ ] `Content`/`Retry` 두 phase 모두에서 동작한다(둘 다 카드 목록을 그린다).

---

# 기준 문서

- [SrsStudyViewModel.kt:294-305](../../../../app/src/main/java/com/app/umma/presentation/srsstudy/SrsStudyViewModel.kt) (`onPlayPronunciation` — 미러 원본)
- [SrsStudyScreen.kt](../../../../app/src/main/java/com/app/umma/presentation/srsstudy/SrsStudyScreen.kt) (뒷면 스피커 `IconButton`·재생 중 tint)
- [TextToSpeechController.kt](../../../../app/src/main/java/com/app/umma/core/tts/TextToSpeechController.kt) / [TtsModule.kt](../../../../app/src/main/java/com/app/umma/di/TtsModule.kt) (TTS 인프라·DI)
- [COR-UX-001](COR-UX-001_Correction_Loading_Flashcard_Review.md) (로딩 복습 카드는 TTS 제외 — 본 카드는 결과 카드라 TTS 연결)

---

# 핵심 결정

- **읽는 문장은 `afterText`(교정 후, 대상 언어).** 헤더 `nativeText`는 모국어 의도 문장(앞면)이라 발음 학습 가치가 낮다. SRS가 뒷면(대상 언어)을 읽는 것과 일관되게 교정문을 읽는다. (PM 합의: `afterText`.)
- **재생 상태는 id 단위로 추적.** SRS는 카드 한 장이라 `isSpeaking: Boolean`이면 충분하지만, 교정은 목록이므로 `speakingSuggestionId: String?`로 어느 카드가 재생 중인지 구분해 그 카드만 tint를 바꾼다.
- **TTS 생명주기는 SRS와 일치.** `TextToSpeechController`는 Singleton이라 별도 shutdown 없이 재사용한다. 화면 이탈 시 진행 중 재생 중단 정책도 SRS와 동일하게 맞춘다 — `CorrectionViewModel.stopPronunciation()`을 `CorrectionScreen`의 `LifecycleEventObserver`(ON_STOP) 와 `DisposableEffect`(onDispose) 에서 호출해 홈 버튼 백그라운드 진입 및 화면 전환 시 TTS를 즉시 중단한다. `onCleared()`만으로는 홈 버튼 백그라운드 진입을 커버하지 못하므로 화면 레이어에서 직접 연결한다. (COR-FIX-014)

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionViewModel | `TextToSpeechController` 주입, `onPlaySuggestionAudio(suggestion)` 추가(`setLanguage`→`speak(afterText)`→`speakingSuggestionId` 갱신) |
| CorrectionUiState | `speakingSuggestionId: String?` 필드 추가 |
| CorrectionResultCard | 정적 `Icon` → `IconButton(onClick = onSpeak)`, 재생 중 `tint = ThemePrimary`. `onSpeak`·`isSpeaking` 파라미터 |
| CorrectionResultList / CorrectionScreen | 콜백·`speakingSuggestionId`를 카드까지 전달(Content·Retry 분기) |
| TextToSpeechController / TtsModule (범위 밖) | 재사용만. 엔진/DI 변경 없음 |

---

# 주요 작업

1. **상태 확장** (`CorrectionUiState`): `speakingSuggestionId: String? = null` + KDoc.
2. **ViewModel** (`CorrectionViewModel`): `private val ttsController: TextToSpeechController` 주입. `onPlaySuggestionAudio(suggestion: CorrectionSuggestion)` — `selectedLearningLanguage`로 `setLanguage`, 성공 시 `speak(suggestion.afterText){ speakingSuggestionId=null }`, 시작 시 `speakingSuggestionId = suggestion.id`.
3. **카드** (`CorrectionResultCard`): 헤더 스피커를 `IconButton`으로 교체, `onSpeak: () -> Unit`·`isSpeaking: Boolean` 추가, 재생 중 tint. contentDescription `발음 듣기`.
4. **배선** (`CorrectionResultList`, `CorrectionScreen`): `onSpeak = { viewModel.onPlaySuggestionAudio(it) }`, `isSpeaking = (it.id == uiState.speakingSuggestionId)`를 Content·Retry 두 분기에서 전달.
5. **테스트**: `onPlaySuggestionAudio`가 `setLanguage` 실패 시 no-op, 성공 시 `speakingSuggestionId` 세팅/해제(가짜 `TextToSpeechController`로 검증, SRS 테스트 패턴 참고).

---

# 예외 처리

- `selectedLearningLanguage == null` 또는 `setLanguage` 실패 → 재생하지 않음(no-op), `speakingSuggestionId` 변경 없음.
- 다른 카드 재생 중 새 카드 탭 → `QUEUE_FLUSH`로 새 문장이 즉시 재생되고 `speakingSuggestionId`가 새 id로 갱신.
- `afterText`가 비어 있는 비정상 카드 → 재생 시도해도 무음(방어적으로 blank면 skip 가능).

---

# 검증 기준

- `:app:compileDevDebugKotlin` 빌드 통과.
- 각 카드 스피커 탭 → `afterText`가 학습 언어로 발음되고, 재생 중 해당 카드 아이콘 색이 바뀌는지(수동, 실기기).
- SRS 뒷면 발음과 체감(언어/음성)이 동일한지(수동).
- 미지원 언어/STT 미설정 환경에서 크래시 없이 no-op인지(수동).
- 단위 테스트: `onPlaySuggestionAudio` 상태 전이 통과.

---

# Out of Scope

- 헤더 `nativeText`(모국어) 발음.
- 로딩 복습 카드(COR-UX-001)의 TTS — 그 카드는 복습 표시 전용으로 TTS 제외 유지.
- 자동 재생/연속 재생, 속도·음성 선택 등 TTS 설정 UI.
