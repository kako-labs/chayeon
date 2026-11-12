# 앱 통합 기록

## 실행과 소유권

`ChayeonApplication`이 `VehicleGraph`, `SocialGraph`, `SignaturePublisher`, `AppEvents`를 생성해요. `MainActivity`는 권한과 전경 상태를 전달하고, `ChayeonApp`은 등록 상태·주행 정책·발견 상태를 수집해 화면을 연결해요. 운영 그래프에 Empty 저장소는 없어요. 기존 Empty 구현은 테스트 대역으로만 참조하며 R8 릴리스에서 제거돼요.

`SignatureRules`는 앱과 기존 서버 모두 `DefaultSignatureEngine`을 사용해요. 게시 재시도는 같은 명령을 유지하고 계정 종료와 잠금을 공유해요. 로컬 계정 연결 전에는 빈 시그니처를 게시하지 않아요. 기록·위치 게이트는 현재 세션에서 확인한 동의로 열어요. 같은 세션의 불러오기·통신 오류 중에는 마지막 동의를 유지하고, 새 동의 응답에 철회를 반영해요. 세션 종료·계정 전환에는 값을 지우며 로그아웃·삭제 때 두 게이트를 먼저 닫아요.

추가 연결 작업은 없어요. 외부 호스트에서 그래프를 직접 구성한다면 아래 연결이 필요해요.

- `SocialGraph.recordingSettings` → `VehicleGraph.gateInput.update()`
- `SocialGraph.messageSignals` → `AppEvents` 생성자의 `signals`
- `VehicleGraph.gateInput.permission`의 계정 → `SignaturePublisher`의 `localAccount`
- Activity 전경 → `VehicleGraph.setForeground()`, 권한 결과 → `permissionsChanged()`
- `ChayeonApp(graph, events, publisherFailure, recordingFailure)`로 상태와 알림을 전달해요.

## 정상 설치와 릴리스

`python3 scripts/dev.py e2e --with-real-drive`는 `CHAYEON_EMULATOR_SERIAL`로 지정한 에뮬레이터, 또는 AVD 이름이 `Pleos_Connect_v2_0`인 에뮬레이터 한 대의 사용자 10에서 앱을 제거하고 정상 설치해요. 권한은 화면에서 허용하며 shell 권한을 앱에 빌려주지 않아요. Virtual Console은 포트 8088을 전달하고 약 1초마다 세션을 유지해요.

`python3 scripts/dev.py e2e --server`는 호스트 서버와 실제 HTTP 상대 계정을 만들어요. 앱은 `http://10.0.2.2:18765`로 연결하며 이 debug 구성에만 해당 호스트의 cleartext를 허용해요. 서버 프로세스는 검증 명령이 직접 시작하고 끝내요.

`python3 scripts/dev.py release`는 R8와 리소스 축소를 적용해 `apk/chayeon-release.apk`를 만들어요. 기본 서버 주소는 없고 시연은 켜져 있어요. 서버 없이 SOLO 모드와 시연용 가상 상대를 사용할 수 있어요. `build/signing/`에 만든 로컬 설치용 키와 비밀번호는 Git에서 제외돼요. 마켓 배포용 키가 아니에요.

`scripts/audit_apk.py`는 서명 검증과 DEX 클래스 정의 검사를 수행해요. 테스트·probe 클래스, debuggable, 특별한 주행 상태 권한을 거절하고 기본 릴리스의 cleartext 차단을 확인해요. 단순 문자열 검색으로 테스트 포함 여부를 판단하지 않아요.

## 확인한 경로

1. 첫 실행 → 혼자 둘러보기 → 프로필 → 동의와 세 권한 화면을 통과했어요.
2. 합성 주행 세 번을 1분 안에 공개하고 내 프로필에서 `합성 주행`을 확인했어요.
3. 가상 상대의 칭호·궁합, 후보의 매듭·특성·궁합을 확인했어요.
4. 연결 요청을 가상 상대가 수락하고 인사를 주고받았어요.
5. D 정차, N, D 주행에서 앱 내용과 숫자가 숨겨지고 `나가기`만 동작해요. P로 돌아오면 같은 대화를 복원해요.
6. 앱 자신의 차량 클라이언트를 끊으면 UNKNOWN이 돼요. 공유 차량 서비스나 에뮬레이터는 종료하지 않았어요.
7. AWAY → 확인 중 → 거리 종료 → 기록 상세 → 애프터 수락 → 메시지 전송을 확인했어요.
8. 애프터에서 차단하면 대화가 닫히고 발견 목록에서 상대가 사라져요.
9. Virtual Console로 210초 주행한 뒤 P로 바꾸면 `차량 주행` 요약이 열려요. SQLite에 합성 3건과 VEHICLE 1건이 있었고, 차량 기록은 ELIGIBLE이며 주행 시간이 180초 이상이었어요. `build/e2e/drive-proof.json`에 원시 신호 없는 집계만 남겼어요.
10. 강제 종료 후 다시 열어 프로필의 `합성 주행`과 거리 종료 기록을 확인했어요. 로그아웃하면 첫 실행으로 돌아오고 파생 주행 행 수가 0이 돼요.

에뮬레이터 전체 화면과 UI XML, 단계별 단언은 `build/e2e/`에 저장돼요. 차량 플랫폼 신호 검증도 에뮬레이터가 만든 신호를 사용하며 실차 검증은 아니에요.

실제 HTTP 서버 검증도 성공했어요. 앱에서 A 계정으로 가입하고 B 계정은 HTTP로 참여해 요청·수락·양방향 인사를 교환했어요. D 정차 중 B가 보낸 인사는 내용 없는 CATEGORY_MESSAGE 알림이 되고, P 복귀 후 나가면 양쪽에 기록이 생겨요. `build/e2e/server/01-http-candidate.png`부터 `05-http-encounter.png`와 `notification-proof.json`에 근거가 있어요. B는 가상 상대에게서도 요청을 받으므로 스크립트가 A의 요청을 식별해서 수락해요. 계정 간 버전을 비교하지 않아요.

## 검증

실행한 검증 명령, 테스트 수와 스크린샷 기준 이미지 수는 [README의 검증 표](../README.md#검증)를 기준으로 확인해요. `verify`는 구조 검사, Kotlin 컴파일, 단위·서버 테스트, lint, Roborazzi 비교와 debug 빌드를 포함해요.

`scripts/e2e.py`는 가상 상대의 애프터 수락과 내 메시지 전송뿐 아니라 `별보는밤`의 자동 답장도 기다리고 확인해요. 답장은 `core/engine/Transitions.kt`의 예약 이벤트에서 처리해요. 기기에서 다시 실행한 결과는 README 검증 기록과 구분해 확인해야 해요.

에뮬레이터 증거는 `build/e2e/`, 릴리스 화면은 `build/e2e/release/`, HTTP 화면은 `build/e2e/server/`에 저장돼요. 차량 클라이언트 연결 해제 검증 수신기는 debug 빌드에만 있어요.

## 범위 밖 변경

공유 계약 `core/model`과 서버는 변경하지 않았어요. 새 외부 의존성도 없어요.

- `data/social/.../SocialGraph.kt`: 주행 중에도 기록 동의와 내용 없는 알림 신호를 제공하는 두 서비스용 Flow를 추가했어요.
- `feature/history/.../OnboardingViewModels.kt`: 서버와 같은 방 코드 검증을 추가했어요. `HistoryViewModelTest.kt`에서 잘못된 문자·짧은 코드를 검사해요.
- `feature/history/.../SafetyViewModels.kt`: 복구 비밀값, 상대의 닫힌 관계와 상대 메시지에 관한 정리 문구를 바로잡았어요. `feature/history/src/test/screenshots/`의 `cleanup_confirm_delete_account.png`, `cleanup_confirm_sign_out.png`, `cleanup_empty.png`, `cleanup_failure.png`, `cleanup_loading.png`, `cleanup_populated.png`를 갱신했어요.
- `feature/meet/.../MeetViewModel.kt`, `MeetNavigationTest.kt`, `MeetViewModelTest.kt`: 수신 요청이 후보 탐색을 강제로 끊지 않게 하고 앱 배너로 열도록 했어요. 재시작 시 이미 끝난 연결이 홈을 다시 가로채지 않도록 고치고 회귀 테스트를 추가했어요.
- `feature/signature/.../SignatureViewModelTest.kt`: 유효한 엔진 투영 보존 테스트에 실제 `DefaultSignatureEngine` 결과를 사용해요.

## 남은 제한

실차 신호 품질, 실제 차량 서비스 프로세스의 강제 종료·복구, 장시간 백그라운드 유지, App Market 승인은 검증하지 않았어요. 기록 프로세스가 종료되면 메모리의 미완료 주행은 복원하지 않아요.

SOLO 화면의 출처 문구는 첫 모드 선택과 내 시그니처의 특성 상세 한 줄로 모았어요. 위 기기 기록의 `합성 주행`·`차량 주행` 캡션은 당시 화면 문구예요. 현재 동선과 문구는 README `3분 둘러보기`를 따라요.
