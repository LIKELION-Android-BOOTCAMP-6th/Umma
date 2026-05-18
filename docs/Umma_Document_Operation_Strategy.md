# Umma 개발 문서 운영 전략 (Flow DB + GitHub SSOT 가이드)

## 1. 문서 운영 목적

Umma 프로젝트는:

- AI
- 실시간 음성 대화
- 사용자 학습 상태 관리
- 통계 및 반복 학습

등 구조 복잡도가 높은 프로젝트이다.

따라서:

```
“기능 단위(User Flow) 중심으로 빠르게 개발하되,
필요한 공통 구조(System Flow)는 최소 단위로 선행 구축한다.”
```

를 핵심 개발 전략으로 사용한다.

---

# 2. 전체 문서 구조

Umma는 크게 3단계 문서 구조를 사용한다.

| 문서 | 역할 | 목적 |
| --- | --- | --- |
| Product Brief | 프로젝트 전체 방향 정의 | 서비스 목표 공유 |
| Flow DB | 개발 단위 관리 | Sprint / 개발 흐름 관리 |
| GitHub Issue (SSOT) | 실제 구현 명세 | 팀원 작업 기준 |

---

# 3. Flow DB 구조

Flow DB는:

```
“무엇을 개발할 것인가?”
```

를 관리하는 문서이다.

---

# 4. Flow 종류

Umma는 2가지 Flow를 사용한다.

| Flow 종류 | 역할 |
| --- | --- |
| System Flow | 공통 인프라 및 구조 구축 |
| User Flow | 실제 사용자 기능 개발 |

---

# 5. System Flow란?

사용자 기능을 구현하기 전에 필요한:

- 공통 구조
- 상태 관리
- Firebase 연결
- Realtime 환경
- Memory 구조

등을 구축하는 Flow.

---

## 예시

| ID | 설명 |
| --- | --- |
| SYS-COMMON-INFRA | 공통 앱 구조 |
| SYS-LEARNING-STATE-INFRA | 학습 상태 구조 |
| SYS-REALTIME-INFRA | Realtime 음성 환경 |

---

# 6. User Flow란?

실제 사용자가 경험하는 기능 단위.

---

## 예시

| ID | 설명 |
| --- | --- |
| FLOW-ONBOARDING | 로그인 및 초기 설정 |
| FLOW-DASHBOARD | Dashboard 화면 |
| FLOW-AI-CHAT | AI 음성 대화 |
| FLOW-CORRECTION | 교정 및 Flashcard 저장 |

---

# 7. Flow DB 운영 원칙

## 핵심 원칙

```
Flow는 “기능 구현 단위”가 아니라
“사용자 경험 흐름 단위”로 작성한다.
```

---

## 좋은 예

```
FLOW-AI-CHAT
```

사용자 경험 중심.

---

## 좋지 않은 예

```
STT 구현
WebSocket 연결
```

너무 기술 단위.

---

# 8. System Flow 작성 원칙

System Flow는:

```
“다음 User Flow를 가능하게 만드는 최소 기반”
```

만 작성한다.

---

## 중요한 원칙

초반부터:

- 거대한 ERD
- 과도한 설계
- 모든 구조 완성

을 목표로 하지 않는다.

---

## 예시

### Dashboard 개발 전

필요:

```
SYS-LEARNING-STATE-INFRA
```

---

### AI Chat 개발 전

필요:

```
SYS-REALTIME-INFRA
```

---

# 9. GitHub Issue (SSOT) 역할

GitHub Issue는:

```
“실제 구현 기준 문서”
```

이다.

---

## 팀원은:

Flow DB만 보고 개발하지 않는다.

반드시:

- 해당 SSOT Issue
- Acceptance Criteria (AC)
- Edge Cases

를 기준으로 구현한다.

---

# 10. SSOT 작성 원칙

SSOT는:

```
“구현 가능한 수준까지 구체적이어야 한다.”
```

---

## 포함해야 하는 내용

| 항목 | 목적 |
| --- | --- |
| User Story | 사용자 목표 |
| AC | 완료 기준 |
| 구현 범위 | 작업 범위 명확화 |
| Loading/Error 정책 | 상태 통일 |
| Edge Cases | 예외 대응 |
| 기술 설계 가이드 | 구조 통일 |
| Firebase 데이터 구조 | 데이터 일관성 |
| 테스트 시나리오 | 검증 기준 |

---

# 11. SSOT 작성 수준 기준

## 좋은 SSOT

```
로그인 중 버튼 비활성화
Firebase User null 처리
BackStack 초기화
```

처럼:

구현자가 바로 작업 가능한 수준.

---

## 좋지 않은 SSOT

```
로그인 기능 구현
```

처럼:

구현 방식이 불명확한 수준.

---

# 12. 팀 역할 구조

---

# 팀장 / 부팀장 역할

## 역할

### 1. 다음 User Flow 준비

현재 Sprint의:

- 다음 User Flow
- 필요한 System Flow

를 선행 준비한다.

---

### 2. 공통 구조 관리

예:

- Navigation
- Global State
- Firebase 구조
- Realtime 구조
- Memory 구조
- Design System

---

### 3. SSOT 작성 및 관리

구현 전에:

- GitHub Issue 작성
- Acceptance Criteria 정의
- 구조 기준 통일

을 담당한다.

---

# 팀원 역할

## 역할

### 1. User Flow 구현

SSOT 기준으로:

- 화면
- 상태 처리
- 기능 구현

수행.

---

### 2. 구현 범위 준수

SSOT 범위를 넘는:

- 독자적 구조 변경
- 임의 모델 변경

금지.

---

### 3. PR 기준 준수

PR에는:

- 관련 Issue 번호
- 구현 내용
- 테스트 결과

포함.

---

# 13. Sprint 개발 방식

Umma는 다음 흐름으로 개발한다.

---

## STEP 1

다음 User Flow 선정.

예:

```
FLOW-DASHBOARD
```

---

## STEP 2

필요한 최소 System Flow 선행.

예:

```
SYS-LEARNING-STATE-INFRA
```

---

## STEP 3

SSOT Issue 작성.

예:

```
DASH-001 Dashboard Header
DASH-002 Summary Card
```

---

## STEP 4

팀원에게 Issue 분배 후 구현.

---

## STEP 5

Merge 및 테스트 후 다음 Flow 진행.

---

# 14. 중요한 개발 원칙

---

# 1. 과설계 금지

초반부터:

- 모든 구조 완성
- 모든 데이터 설계

를 목표로 하지 않는다.

---

# 2. User Flow 중심 개발

기술보다:

- 사용자 경험
- 실제 동작 흐름

우선.

---

# 3. 공통 구조는 최소 단위만

System Flow는:

“현재 Sprint에 필요한 만큼만”

구축.

---

# 4. SSOT 없는 구현 금지

모든 구현은:

반드시 GitHub Issue 기준으로 진행.

---

# 5. 상태 처리 통일

모든 화면은:

- Loading
- Error
- Empty

상태를 공통 규칙으로 처리.

---

# 15. Umma 문서 운영 철학

Umma는:

```
“빠르게 개발하지만,
구조는 무너지지 않게 유지한다.”
```

를 핵심 철학으로 사용한다.

---

# 핵심 전략 요약

```
Product Brief
→ 서비스 방향 정의

Flow DB
→ 개발 흐름 관리

GitHub Issue(SSOT)
→ 실제 구현 기준
```

---

# 최종 목표

```
“팀원들이 구조 고민보다
사용자 기능 구현에 집중할 수 있도록 만든다.”
```
