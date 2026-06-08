# [UX] COR-UX-005 교정 결과 카드 설명 아이콘 크기·위치 정리

> 헤더(`nativeText`)와 'i' 설명(`explanation`)이 비슷해 보이는 문제는 라벨이 아니라 **생성 내용** 문제로 판명되어 [COR-FIX-010](COR-FIX-010_Correction_NativeText_Intended_Meaning.md)에서 다룬다. 본 문서는 순수 시각 디테일(설명 아이콘 크기·위치)만 다룬다.

## User Story

사용자는 교정 결과 카드에서 설명 옆 'i' 아이콘이 본문을 압도하지 않는, 적당한 크기/위치로 정돈된 카드를 본다.

---

# 배경

설명 행의 Info 아이콘([CorrectionResultCard.kt:169-174](../../../../app/src/main/java/com/app/umma/presentation/correction/component/CorrectionResultCard.kt))은 `Modifier.size(IconSizeSmall)`(24.dp, [Dimens.kt:27](../../../../app/src/main/java/com/app/umma/core/theme/Dimens.kt))에 `Alignment.Top`이다. 본문 대비 다소 크고, 위치를 약간 위로 올리고 싶다는 UI 디테일 피드백.

→ **`IconSizeSmall`은 상단 바·칩·Before/After 아이콘·헤더 스피커가 공유하는 전역 토큰**이므로 토큰 값을 바꾸면 안 된다. 이 Info 아이콘만 **로컬 크기**로 줄이고 살짝 위로 올린다.

---

# 완료 기준(AC)

- [ ] 설명 행 Info 아이콘을 **약간 작게** 한다(로컬 `size`, 예: 18~20.dp). 시각 확인 후 미세조정.
- [ ] 아이콘을 **약간 위로** 올린다(`Alignment.Top` 유지 + 소폭 음수 `offset`, 예: `y = (-2).dp`).
- [ ] **`IconSizeSmall` 전역 토큰을 변경하지 않는다.** Before/After 아이콘·헤더 스피커·다른 화면 인라인 아이콘 크기는 영향 없음.
- [ ] 상태/ViewModel/도메인 로직 변경 없음(순수 시각 작업).

---

# 기준 문서

- [CorrectionResultCard.kt](../../../../app/src/main/java/com/app/umma/presentation/correction/component/CorrectionResultCard.kt) (대상 컴포저블, Preview 3종 포함)
- [Dimens.kt](../../../../app/src/main/java/com/app/umma/core/theme/Dimens.kt) (`IconSizeSmall` 공유 토큰 — 변경 금지 근거)
- [COR-FIX-010](COR-FIX-010_Correction_NativeText_Intended_Meaning.md) (헤더/설명 내용 구분 — 분리된 별도 건)

---

# 핵심 결정

- **로컬 오버라이드.** 공유 토큰 `IconSizeSmall`을 건드리면 다른 아이콘이 모두 작아지므로, Info 아이콘에만 로컬 `size`/`offset`을 적용한다.
- **순수 시각 작업.** `CorrectionResultCard` 한 파일과 Preview로 끝난다. 텍스트/상태/도메인은 손대지 않는다.

---

# 책임 경계

| 영역 | 책임 |
| --- | --- |
| CorrectionResultCard (설명 행) | Info 아이콘 로컬 `size` 축소 + 소폭 위로 `offset` |
| Dimens.kt (범위 밖) | `IconSizeSmall` 토큰 불변 |
| CorrectionViewModel / UiState / 도메인 (범위 밖) | 변경 없음 |
| nativeText/explanation 내용 (범위 밖) | → COR-FIX-010 |

---

# 주요 작업

1. **Info 아이콘** (설명 행): `Modifier.size(IconSizeSmall)` → 로컬 `Modifier.size(18.dp)`(또는 20.dp), `Alignment.Top` 유지 + `Modifier.offset(y = (-2).dp)` 추가(offset import).
2. **Preview 확인**: 기존 `CorrectionResultCardPreview`(정상/선택됨/긴 설명) 3종으로 아이콘 크기·위치 점검.

---

# 예외 처리

- 설명이 여러 줄로 길어질 때(긴 설명 Preview) 아이콘 위치/정렬이 어색하지 않은지 확인 — `offset` 값은 한 줄/여러 줄 모두에서 자연스러운 선에서 미세조정.

---

# 검증 기준

- `:app:compileDevDebugKotlin` 빌드 통과.
- Compose Preview 3종에서 'i' 아이콘이 더 작고 살짝 위로 정렬되는지(시각).
- Before/After 아이콘·헤더 스피커·다른 화면 인라인 아이콘 크기가 그대로인지(`IconSizeSmall` 미변경 확인).

---

# Out of Scope

- 헤더(`nativeText`)와 'i' 설명(`explanation`)의 **내용 구분** (→ [COR-FIX-010](COR-FIX-010_Correction_NativeText_Intended_Meaning.md)).
- 카드 전체 레이아웃·색상 체계 개편.
- 헤더 스피커 동작(→ [COR-UX-004](COR-UX-004_Correction_Card_TTS_Pronunciation.md)).
