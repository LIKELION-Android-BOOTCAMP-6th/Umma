# Demo Scenario - FLOW-WATCH-NOTIFICATION

이 문서는 현재 코드 기준의 Umma V1 워치 알림 데모 및 QA 기준이다.

V1 알림 정책은 다음을 전제로 한다.

- 알림의 source of truth는 폰이다.
- 알림 생성은 기존 폰 FCM / 로컬 알림 경로를 사용한다.
- 워치는 Galaxy Wearable / Wear OS 시스템 notification mirroring으로 폰 알림을 받는다.
- V1에서 워치 자체 FCM token 등록 및 direct watch push는 구현하지 않는다.
- 현재 코드 기준 SRS / 마케팅 알림 모두 폰 알림으로 생성되고, 워치에는 시스템 미러링 결과로 노출된다.
- 현재 코드 기준 워치 전용 알림 액션은 구현하지 않는다.
- 개발/QA용 테스트 알림 생성기는 마이페이지의 devDebug 전용 액션에서 호출한다.

---

## 1. 실행 기준

- 폰 앱은 로그인된 상태여야 한다.
- 폰의 `POST_NOTIFICATIONS` 권한은 허용 상태여야 한다.
- 현재 기기가 `notification_devices`에 active device로 등록되어 있어야 한다.
- 테스트하려는 알림 종류의 설정이 ON이어야 한다.
  - SRS: `users/{uid}/notification_settings/srs_review.enabled == true`
  - 마케팅: `users/{uid}/notification_settings/marketing.enabled == true`
- 워치는 폰과 페어링되어 있어야 한다.
- 워치가 폰 알림 미러링을 받을 수 있도록 Galaxy Wearable 설정이 켜져 있어야 한다.
- 워치의 방해 금지 모드 또는 취침 모드가 켜져 있지 않아야 한다.

---

## 1-1. 워치 알림 설정 방법

기준 기기: Galaxy S23급 Android 기기 및 Galaxy Watch 7

1. 스마트폰에서 `Galaxy Wearable` 앱을 실행한다.
2. 워치 설정 메뉴로 이동한 뒤 `알림`을 선택한다.
3. `두 기기에서 함께 알림 받기`를 켠다.
4. 앱 알림 메뉴에서 `움마`를 찾아 알림을 허용한다.
5. 워치의 방해 금지 모드와 취침 모드가 꺼져 있는지 확인한다.

---

## 2. 현재 구현 범위

포함:

- SRS 알림 FCM 수신 및 폰 로컬 알림 표시
- 마케팅 알림 FCM 수신 및 폰 로컬 알림 표시
- SRS 알림 채널: `srs_review_notifications`
- 마케팅 알림 채널: `marketing_notifications`
- SRS 알림 탭 시 `MainActivity`에 `notification_type=srs_review`, `notification_route=srs_study`, `notification_lang`, `notification_history_id` 전달
- 마케팅 알림 탭 시 `MainActivity`에 `notification_type=marketing`, `notification_route=home`, `notification_history_id` 전달
- 폰 알림의 워치 시스템 미러링 확인
- devDebug 마이페이지 테스트 알림 생성기
- 테스트 알림 생성기의 설정 ON/OFF 게이트

제외:

- 워치 자체 FCM token 등록
- watch direct push
- 워치 전용 notification action
- 워치에서 복습 액션
- 10분 뒤 다시 알림 / snooze 액션
- 워치 알림 기반 phone handoff command
- Tile / Complication
- 마케팅 알림 전용 워치 UX

---

## 3. 테스트 알림 생성기 기준

테스트 알림 생성기는 Firebase callable function `sendTestNotification`을 호출한다.

호출 조건:

- 로그인된 사용자만 호출 가능하다.
- 호출자는 자기 자신의 active notification device에만 발송할 수 있다.
- 요청 `type`은 `srs_review` 또는 `marketing`만 허용한다.
- 요청한 알림 종류의 `notification_settings/{type}.enabled`가 `true`여야 한다.
- active notification device가 하나 이상 있어야 한다.
- `srs_review`는 선택 학습 언어가 있어야 한다.

동작:

- SRS 테스트 알림은 data payload `type=srs_review`, `route=srs_study`, `historyId`, `title`, `body`, `lang`을 포함한다.
- 마케팅 테스트 알림은 data payload `type=marketing`, `route=home`, `historyId`, `title`, `body`를 포함한다.
- invalid FCM token은 기존 helper를 통해 비활성화한다.
- `notification_history/{historyId}`에 `isTest=true`로 발송 결과를 기록한다.

주의:

- 이 도구는 즉시 발송 QA용이다.
- 실제 정기 스케줄러의 due time 계산을 검증하는 도구가 아니다.
- 폰 알림 권한이 꺼져 있으면 서버 발송은 성공할 수 있어도 폰/워치 알림은 표시되지 않을 수 있다.

---

## 4. 데모 시나리오

### 시나리오 1 - SRS 알림 미러링 수신

1. 폰 앱에 로그인한다.
2. 마이페이지에서 학습 알림을 ON으로 둔다.
3. 폰 알림 권한이 허용되어 있는지 확인한다.
4. devDebug 빌드의 마이페이지 상단 테스트 알림 버튼을 누른다.
5. `학습`을 선택하고 전송한다.
6. 폰에 SRS 알림이 표시되는지 확인한다.
7. 같은 알림이 워치에 미러링되어 표시되는지 확인한다.
8. 워치 알림 제목/본문이 과도하게 길지 않은지 확인한다.

기대 결과:

- 폰에 SRS 알림이 `srs_review_notifications` 채널로 표시된다.
- 워치는 direct push 없이 시스템 미러링으로 같은 알림을 보여준다.
- 알림 본문은 짧고 민감한 상세 데이터를 노출하지 않는다.

### 시나리오 2 - 마케팅 알림 미러링 수신

1. 폰 앱에 로그인한다.
2. 마이페이지에서 마케팅 알림을 ON으로 둔다.
3. 폰 알림 권한이 허용되어 있는지 확인한다.
4. devDebug 빌드의 마이페이지 상단 테스트 알림 버튼을 누른다.
5. `마케팅`을 선택하고 전송한다.
6. 폰에 마케팅 알림이 표시되는지 확인한다.
7. 같은 알림이 워치에 미러링되어 표시되는지 확인한다.

기대 결과:

- 폰에 마케팅 알림이 `marketing_notifications` 채널로 표시된다.
- 워치는 direct push 없이 시스템 미러링으로 같은 알림을 보여준다.
- 마케팅 알림에는 별도 워치 전용 action이 없어야 한다.

### 시나리오 3 - 알림 탭 라우팅

1. SRS 테스트 알림을 폰에 표시한다.
2. 폰에서 SRS 알림을 탭한다.
3. 앱이 `srs_study` route로 진입하는지 확인한다.
4. 마케팅 테스트 알림을 폰에 표시한다.
5. 폰에서 마케팅 알림을 탭한다.
6. 앱이 `home` route로 진입하는지 확인한다.

기대 결과:

- SRS 알림 tap intent에는 `notification_type=srs_review`, `notification_route=srs_study`, `notification_lang`, `notification_history_id`가 전달된다.
- 마케팅 알림 tap intent에는 `notification_type=marketing`, `notification_route=home`, `notification_history_id`가 전달된다.
- 기존 앱 백스택을 불필요하게 중복 생성하지 않는다.

### 시나리오 4 - 테스트 알림 설정 OFF

1. 마이페이지에서 학습 알림을 OFF로 둔다.
2. 테스트 알림 생성기에서 `학습`을 선택하고 전송한다.
3. 발송 실패 안내가 표시되는지 확인한다.
4. 마이페이지에서 마케팅 알림을 OFF로 둔다.
5. 테스트 알림 생성기에서 `마케팅`을 선택하고 전송한다.
6. 발송 실패 안내가 표시되는지 확인한다.

기대 결과:

- 설정이 OFF인 알림 종류는 FCM 발송 전에 차단된다.
- 함수는 `failed-precondition`으로 실패한다.
- 폰과 워치 모두 새 알림을 표시하지 않는다.

### 시나리오 5 - 알림 권한 거부

1. 폰의 `POST_NOTIFICATIONS` 권한을 거부한다.
2. 테스트 알림 생성기에서 SRS 또는 마케팅 알림을 전송한다.
3. 폰과 워치 모두 알림이 뜨지 않는지 확인한다.

기대 결과:

- `UmmaFirebaseMessagingService`는 권한이 없으면 로컬 알림을 생성하지 않는다.
- 폰 알림이 생성되지 않으므로 워치 미러링도 발생하지 않는다.
- 앱은 마이페이지 알림 설정 흐름에서 권한 요청 또는 설정 유도로 복구할 수 있어야 한다.

### 시나리오 6 - 워치 미러링 설정 OFF

1. Galaxy Wearable에서 움마 앱 알림 미러링을 끈다.
2. 폰에서 SRS 또는 마케팅 알림을 생성한다.
3. 폰에는 알림이 표시되고 워치에는 표시되지 않는지 확인한다.

기대 결과:

- 폰 알림 경로는 정상 동작한다.
- 워치 미노출은 앱 direct push 실패가 아니라 OS / Galaxy Wearable 미러링 설정 결과로 판정한다.

---

## 5. 데모 체크포인트

| Test ID | 확인 항목 | 확인 방법 | 기대 결과 | Pass/Fail | 비고 |
| --- | --- | --- | --- | --- | --- |
| TC-WN-01 | SRS 미러링 | 학습 알림 ON 후 테스트 발송 | 폰 SRS 알림이 워치에 미러링됨 |  |  |
| TC-WN-02 | 마케팅 미러링 | 마케팅 알림 ON 후 테스트 발송 | 폰 마케팅 알림이 워치에 미러링됨 |  |  |
| TC-WN-03 | SRS 탭 라우팅 | 폰 SRS 알림 탭 | `srs_study` route 진입 |  |  |
| TC-WN-04 | 마케팅 탭 라우팅 | 폰 마케팅 알림 탭 | `home` route 진입 |  |  |
| TC-WN-05 | 설정 OFF 차단 | 해당 알림 OFF 후 테스트 발송 | 발송 실패, 신규 알림 미표시 |  |  |
| TC-WN-06 | 알림 권한 거부 | `POST_NOTIFICATIONS` OFF 후 발송 | 폰/워치 모두 알림 미노출 |  |  |
| TC-WN-07 | 워치 미러링 설정 OFF | Galaxy Wearable 알림 미러링 OFF | 폰만 알림 표시, 워치 미노출 |  |  |
| TC-WN-08 | 개인정보 노출 | 긴 title/body 또는 민감 정보 포함 여부 확인 | 워치 본문이 짧고 안전하게 표시됨 |  |  |

---

## 6. 합격 기준

- 폰이 알림 SSOT로 유지된다.
- SRS / 마케팅 알림은 폰 로컬 알림으로 정상 생성된다.
- 워치는 direct push 없이 시스템 notification mirroring으로 알림을 수신한다.
- 테스트 알림 생성기는 알림 종류별 enabled 설정을 존중한다.
- 알림 권한이 없으면 폰/워치 모두 알림이 노출되지 않는다.
- 워치 미노출 이슈를 앱 로직 문제와 Galaxy Wearable / Wear OS 설정 문제로 구분해 판정할 수 있다.
- 현재 코드 기준 구현되지 않은 워치 전용 action, snooze, direct watch push를 QA 합격 기준에 포함하지 않는다.
