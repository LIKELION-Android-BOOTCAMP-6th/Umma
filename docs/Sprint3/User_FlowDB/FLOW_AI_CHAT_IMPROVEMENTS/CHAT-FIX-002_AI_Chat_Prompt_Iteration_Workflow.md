# [Fix] CHAT-FIX-002 AI Chat 프롬프트 반복 개선 운영 계획

## 목적

`CHAT-TUNE-005~007`에서 Chat 대화 능력 단계, 대화 능력 저장, 다음 세션 band 재사용 구조를 정리했다.
이제 실제 테스트 과정에서 발견되는 이상 대화를 신고, export, 수동 분석, 작은 prompt 수정, 재검증으로 반복해 원하는 대화 품질에 가까워지게 한다.

이번 작업은 새로운 prompt 구조를 다시 만드는 작업이 아니다.
이미 준비한 prompt review 도구, Firestore 신고 문서, `AiChatPromptTrace` 로그, `REVIEW_NOTES.md`를 사용해 반복 개선 사이클을 운영하는 작업이다.

---

# User Story

팀원이나 테스터는 AI Chat을 평소처럼 사용하다가 대화가 어색하다고 느끼면 신고 버튼으로 세션을 남길 수 있다.
개발자는 신고 세션과 로그를 시간순 문서로 export하고, 적용된 prompt version과 band를 확인해 원인을 좁힌다.
Umma는 한 번에 큰 구조를 바꾸지 않고, 반복되는 문제를 작게 수정하고 다음 테스트에서 개선 여부를 확인한다.

---

# 완료 기준(AC)

- [ ] 팀원은 대화 중 이상함을 느낀 세션을 신고 버튼으로 남길 수 있다.
- [ ] 신고 세션은 `chat_prompt_review_reports`에서 prompt version, band, 언어, `reportId`, 세션 ID로 찾을 수 있다.
- [ ] 개발자는 export 도구로 신고 세션의 prompt trace와 final transcript를 시간순 Markdown으로 확인할 수 있다.
- [ ] `AiChatPromptTrace` 로그에서 세션 시작 시 적용된 band, source, speed, prompt section을 확인할 수 있다.
- [ ] 프롬프트 개선은 `REVIEW_NOTES.md`의 열린 개선 항목을 기준으로 누적 관리한다.
- [ ] 같은 원인으로 보이는 신고는 새 문단을 계속 늘리지 않고 기존 개선 항목에 누적한다.
- [ ] 한 번의 수정은 가장 명확한 원인 하나를 겨냥한다.
- [ ] 수정 전에는 기존 prompt에 같은 의미의 지시가 이미 있는지 확인한다.
- [ ] 수정 전에는 `persona`, `language_use`, `conversation_principles`, `current_style`, `style_reference` 중 어느 section의 책임인지 먼저 정한다.
- [ ] 특정 band만 계속 비대해지지 않도록 인접 band의 역할도 함께 확인한다.
- [ ] `IntentOnly` 수정 시에는 `PhraseEmerging`이 기준언어와 학습언어를 적극적으로 섞는 다음 단계로 자연스럽게 이어지는지 같이 확인한다.
- [ ] prompt에는 실패 문구 예시, raw metric, 내부 enum, 나이 비유, 이모지 지시를 넣지 않는다.
- [ ] 개선 후 관련 prompt/chat 테스트와 컴파일 검증을 수행한다.

---

# 포함 범위

- 신고 세션 수집 운영 방식
- Firestore review 문서 확인 방식
- export Markdown 생성 방식
- `AiChatPromptTrace` 로그 확인 기준
- `REVIEW_NOTES.md` 작성/정리 규칙
- 프롬프트 개선 우선순위와 수정 단위
- 개선 후 검증 기준

---

# 제외 범위

- prompt review 도구의 신규 UI 기능 추가
- Firestore schema 대규모 변경
- Realtime transport 변경
- SessionMemory 저장 구조 변경
- usage tracking 변경
- Correction prompt 변경
- Statistics/Dashboard 지표 변경
- 회원탈퇴 cleanup 구현
- 자동 품질 판정 모델 구축

---

# 기준 문서

- [CHAT_PROMPT_TUNING_GUIDE](./CHAT_PROMPT_TUNING_GUIDE.md)
- [CHAT-TUNE-005 대화 능력 단계 정의 재조정](./CHAT-TUNE-005_Conversation_Band_Definition_Recalibration.md)
- [CHAT-TUNE-006 Chat 대화 근거 LangState 연동](./CHAT-TUNE-006_Chat_Evidence_LangState_Integration.md)
- [CHAT-TUNE-007 Chat band source를 LangState summary로 일원화](./CHAT-TUNE-007_Chat_Evidence_Summary_Band_Source.md)
- [CHAT-TUNE-008 Conversation Frame과 Snapshot 기반 대화 흐름 보조](./CHAT-TUNE-008_Conversation_Frame_Snapshot.md)
- [AI Chat Prompt Review Tool](../../../../tools/chat_prompt_review/README.md)

---

# 사용 도구와 자료

| 자료 | 위치 | 용도 |
| --- | --- | --- |
| 신고 인덱스 | `chat_prompt_review_reports/{reportId}` | 신고된 세션 목록과 report note 확인 |
| 세션 리뷰 | `users/{uid}/chat_prompt_reviews/{reportId}` | prompt trace와 turn event 원본 확인 |
| export 도구 | `tools/chat_prompt_review/export_review_doc.js` | Firestore 문서를 Markdown으로 변환 |
| export 결과 | `tools/chat_prompt_review/output/chat_prompt_review_{reportId}.md` | 시간순 대화와 prompt 적용 내역 분석 |
| 신고 목록 export | `tools/chat_prompt_review/output/chat_prompt_review_reports.md` | 최근 신고 세션 후보 확인 |
| 리뷰 노트 | `tools/chat_prompt_review/output/REVIEW_NOTES.md` | 문제 태그, 원인 가설, 개선 시도, 결과 누적 |
| prompt trace 로그 | `tag:AiChatPromptTrace` | 실제 세션 시작/능력 저장/적용 band 확인 |

`tools/chat_prompt_review/output/*` 산출물은 Git에 올리지 않는다.
분석 자료는 반복 개선을 위한 임시 자료이며, 실제 구현 기준은 Flow 문서와 백로그 AC를 따른다.

---

# 테스트와 신고 흐름

1. 팀원이나 테스터가 최신 브랜치 앱으로 AI Chat을 평소처럼 사용한다.
2. 대화 품질에 이상함을 느끼면 신고 버튼을 누르고 짧은 불편 메모를 입력한다.
3. 앱은 해당 세션의 prompt trace와 final transcript event를 Firestore review 문서로 flush한다.
4. 개발자는 `chat_prompt_review_reports`에서 최근 신고 세션과 `reportId`를 확인한다.
5. 필요하면 `AiChatPromptTrace` 로그로 실제 적용 band와 `LangStateSummary` 적용 여부를 함께 확인한다.
6. export 도구로 신고 세션 Markdown을 생성한다.
7. 생성된 문서를 기준으로 원인을 수동 분석한다.
8. 같은 원인인지, 새 원인인지 판단해 `REVIEW_NOTES.md`에 누적한다.
9. 원인이 충분히 좁혀졌을 때만 prompt를 작게 수정한다.
10. 수정 후 테스트와 컴파일 검증을 수행하고 다음 신고 세션에서 개선 여부를 본다.

---

# export 실행 방법

신고 목록 확인:

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --reports \
  --limit 20
```

특정 신고 세션 export:

신고 목록에서 확인한 `reportId`가 있으면 이 방식을 우선 사용한다.

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --uid USER_UID \
  --report REPORT_ID
```

기존 로그처럼 세션 ID만 알고 있으면 호환 조회를 사용한다.

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --uid USER_UID \
  --session SESSION_ID
```

최신 세션 export:

```bash
node tools/chat_prompt_review/export_review_doc.js \
  --project umma-6804c \
  --uid USER_UID
```

---

# 로그 확인 기준

Logcat 필터:

```text
tag:AiChatPromptTrace
```

우선 확인할 항목:

- `session_start`: 세션 시작 시 적용된 prompt version, band, source 확인
- `conversationEvidence`: `applied`, `band`, `source` 확인
- `chat_ability scheduled`: 세션 종료 후 대화 능력 분석 예약 확인
- `chat_ability analysis_started`: Gemini 분석 시작 확인
- `chat_ability langstate_saved`: LangState summary 저장 확인
- `chat_ability analyzed`: 분석 결과의 confidence, band, snapshot/LangState 반영 여부 확인
- `chat_ability pending_*`: 실패/재시도 경계 확인

분석 시 중요한 질문:

- 이번 세션에 어떤 band가 적용됐는가?
- 그 band는 `LangStateSummary`에서 온 것인가, fallback인가?
- 사용자가 느낀 불편이 band mismatch인지, prompt 지시 문제인지, 음성/자막/지연 같은 비프롬프트 문제인지?
- Gemini 분석 결과가 다음 세션에 반영될 수 있게 저장됐는가?

---

# 리뷰 노트 작성 규칙

`REVIEW_NOTES.md`는 원문 대화 저장소가 아니다.
원문은 export Markdown에 두고, 리뷰 노트에는 반복 판단에 필요한 최소 정보만 남긴다.

기록 항목:

- report id
- review id
- session id
- 언어
- 적용 band
- evidence source
- 신고 메모
- 문제 태그
- 원인 가설
- 개선 시도 ID
- 다음 검증 항목
- 개선 결과: `improved`, `unchanged`, `worse`, `new_issue`

정리 규칙:

- 같은 원인은 기존 열린 개선 항목에 report ID와 세션 ID만 추가한다.
- 해결된 긴 원인 기록은 줄이고 통계와 개선 결과만 남긴다.
- 실패 사례 문장을 prompt에 그대로 복사하지 않는다.
- 원인 가설이 불확실하면 수정하지 않고 추가 세션을 기다린다.
- 비프롬프트 문제는 프롬프트 리뷰 문서에 누적하지 않는다.

---

# 개선 판단 기준

프롬프트 수정 대상으로 본다:

- 같은 band에서 반복적으로 같은 대화 실패가 발생한다.
- 적용 band는 맞지만 AI 행동이 기획 의도와 어긋난다.
- prompt 문구가 특정 실패 행동을 유도하는 원인으로 좁혀진다.
- 기존 지시가 중복되어 모델이 특정 행동을 과하게 해석한다.

프롬프트 수정 대상으로 보지 않는다:

- AI 응답 로딩 지연
- 자막 오인식 또는 언어 표시 오류
- 음성 재생 끊김
- 마이크 상태 문제
- 세션 종료/재연결 문제
- LangState band 계산 자체의 오류
- Gemini evidence 저장 실패

위 문제는 별도 fix 또는 tuning 이슈로 분리한다.

---

# 수정 원칙

- 새 구조를 만들기보다 기존 `persona`, `language_use`, `conversation_principles`, `current_style`, `style_reference`, `context`의 책임 재배치를 우선한다.
- prompt 문장을 추가하기 전에 같은 의미가 이미 있는지 확인한다.
- 금지 문장을 계속 늘리지 말고, 실패를 유도한 기존 문구를 제거하거나 상위 행동 원칙으로 합친다.
- 한 번에 하나의 주된 원인만 수정한다.
- prompt가 비대해지면 문장 추가보다 중복 제거를 우선한다.
- 실제 prompt에는 실패 문구 예시, raw metric, 내부 enum, 나이 비유, 이모지 지시를 넣지 않는다.
- band별 예시는 복사 템플릿이 아니라 길이, 비율, 리듬 참고용으로 유지한다.

---

# 반복 사이클

```text
신고 수집
→ reports 확인
→ 세션 export
→ prompt trace / band / transcript 수동 분석
→ REVIEW_NOTES에 원인과 태그 누적
→ 가장 명확한 원인 하나만 수정
→ prompt/chat 테스트와 컴파일 검증
→ 최신 브랜치 공유
→ 새 신고 세션에서 개선 여부 확인
```

권장 운영:

- 한 PR에서는 관련 원인 묶음 하나만 다룬다.
- 수정 직후 같은 세션을 완벽히 재현하려고 하기보다, 다음 일반 테스트 신고에서 좋아진 점과 새 문제를 본다.
- 팀원 테스트는 같은 브랜치/커밋 기준으로 맞춘 뒤 다시 수집한다.
- 여러 신고가 쌓였을 때는 language, band, source, 문제 태그별로 묶어서 우선순위를 정한다.

---

# 검증 기준

- `git diff --check`
- `BuildPromptUseCaseTest`
- `ChatPromptIntegrationUseCaseTest`
- 필요 시 `LearningStateWriteUseCasesTest`
- 가능하면 `:app:compileDevDebugKotlin`
- 가능하면 `:app:compileMockDebugKotlin`
- 실제 앱에서 신고 버튼 동작 확인
- `AiChatPromptTrace`에서 세션 시작 band/source 확인
- `chat_prompt_review_reports`에서 신고 인덱스 확인
- export Markdown에서 prompt trace와 final transcript가 시간순으로 정렬되는지 확인

---

# 후속 작업

- 신고 세션이 많아지면 `REVIEW_NOTES.md`의 통계와 열린 개선 항목을 더 압축한다.
- 반복적으로 비프롬프트 문제가 신고되면 별도 `CHAT-FIX` 이슈로 분리한다.
- prompt version 또는 branch 기준 비교가 필요해지면 report index의 분류 기준을 확장한다.
