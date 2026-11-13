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

## 음성 통화 이전에 확인한 경로

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

## 이전 통합에서의 범위 밖 변경

당시 통합에서는 공유 계약 `core/model`과 서버를 변경하지 않았고 새 외부 의존성도 없었어요. 아래 음성 통화 통합에서는 계약·서버와 WebSocket 의존성을 함께 추가했어요.

- `data/social/.../SocialGraph.kt`: 주행 중에도 기록 동의와 내용 없는 알림 신호를 제공하는 두 서비스용 Flow를 추가했어요.
- `feature/history/.../OnboardingViewModels.kt`: 서버와 같은 방 코드 검증을 추가했어요. `HistoryViewModelTest.kt`에서 잘못된 문자·짧은 코드를 검사해요.
- `feature/history/.../SafetyViewModels.kt`: 복구 비밀값, 상대의 닫힌 관계와 상대 메시지에 관한 정리 문구를 바로잡았어요. `feature/history/src/test/screenshots/`의 `cleanup_confirm_delete_account.png`, `cleanup_confirm_sign_out.png`, `cleanup_empty.png`, `cleanup_failure.png`, `cleanup_loading.png`, `cleanup_populated.png`를 갱신했어요.
- `feature/meet/.../MeetViewModel.kt`, `MeetNavigationTest.kt`, `MeetViewModelTest.kt`: 수신 요청이 후보 탐색을 강제로 끊지 않게 하고 앱 배너로 열도록 했어요. 재시작 시 이미 끝난 연결이 홈을 다시 가로채지 않도록 고치고 회귀 테스트를 추가했어요.
- `feature/signature/.../SignatureViewModelTest.kt`: 유효한 엔진 투영 보존 테스트에 실제 `DefaultSignatureEngine` 결과를 사용해요.

## 남은 제한

실차 신호 품질, 실제 차량 서비스 프로세스의 강제 종료·복구, 장시간 백그라운드 유지, App Market 승인은 검증하지 않았어요. 기록 프로세스가 종료되면 메모리의 미완료 주행은 복원하지 않아요.

SOLO 화면의 출처 문구는 첫 모드 선택과 내 시그니처의 특성 상세 한 줄로 모았어요. 위 기기 기록의 `합성 주행`·`차량 주행` 캡션은 당시 화면 문구예요. 현재 동선과 문구는 README `3분 둘러보기`를 따라요.

## 2026-11-13 음성 통화 통합

Application이 VoiceCallController를 소유해 화면 이탈과 무관하게 통화 수명을 관리해요. `RemoteVoiceTransport`만 WebSocket을 열고 `AndroidVoiceAudio`는 core:model 채널을 통해 PCM을 주고받아요. 집 보호, 세션 종료, CHECKING/ENDED, 통화 끊기는 로컬 오디오와 서비스를 닫아요. 통화 끊기는 기존 leave 명령을 재사용해요.

MainActivity는 선택 RECORD_AUDIO 런처를 소유하며 현재 ALLOWED이고 RESUMED일 때만 요청해요. 실제 상대 통화에서 권한 거부·입력 없음은 수신 전용이에요. SOLO와 SERVER_DEMO의 데모 상대는 `ScriptedDemoPeerVoice`와 `PleosSpeechEngine`을 사용하고 마이크·음성 WebSocket을 열지 않아요. SDK 시작 콜백은 실제 소리 시작을 보장하지 않으므로 발화 중 표시를 만들지 않아요. microphone FGS는 녹음 허가가 있을 때만 사용하며 수신 전용과 데모 상대 통화는 mediaPlayback 타입으로 유지해요. 알림 action도 같은 통화 끊기 경로예요.

PCM16 16 kHz mono 20 ms 프레임은 방향당 약 256 kbps예요. 짧은 메모리 큐만 사용하고 음성을 저장·로그하지 않아요. Opus는 후속 단계예요. Android 14의 백그라운드 마이크 시작 제한, Pleos 오디오 포커스·개인정보 스위치·장시간 서비스는 실제 기기에서 추가 확인해야 해요.

SOLO e2e는 통화 화면·주차 중 이야깃거리·D 중 음소거/해제·두 버튼만 허용을 검증해요. 임시 연결의 인사 전송과 메시지 스레드 검증은 제거했고 애프터 인사 흐름은 유지해요. server_e2e는 주차 중 온보딩의 `마이크 · 선택`을 허용한 뒤 HTTP 상대의 인증 WebSocket에서 440 Hz PCM을 송신하고 장치 프레임 또는 마이크 없음 상태, 제3자 거절과 거리 종료 시 소켓 폐쇄를 검사해요. `emulator-5570`의 AVD 조회가 연결 거부여서 이번 작업에서는 다른 기기를 사용하지 않았어요.

### 음성 통화 작업 검증 결과

최초 음성 통화 구현에서 `python3 scripts/dev.py verify`를 실행해 성공했어요(3분 1초, Gradle 작업 492개). Kotlin/서버/Compose 테스트 887개와 Python 도구 테스트 8개가 모두 통과했고 실패·오류·건너뜀은 없어요. Kotlin 컴파일, 모듈 그래프, 모든 Android lint, Roborazzi 비교, debug APK 빌드를 포함해요. 기준 이미지는 총 437장이고 이번 작업에서 15장을 추가하고 76장을 갱신했어요. 변경한 91장을 모두 육안 확인했어요. 아래 표는 그 최초 검증 이력이며, 이후 이야깃거리·Pleos 음성·중계 정책 수정 전체의 최종 검증 수가 아니에요.

| 모듈 | 테스트 수 | 변경 파일(스크린샷 제외) |
|---|---:|---|
| core:model | 17 | Voice.kt, Profile.kt, ApiRequests.kt |
| core:engine | 46 | ProfileCommands.kt |
| core:ui | 103 | SignaturePresentation.kt, ScreenKitBehaviorTest.kt |
| data:social | 41 | RemoteVoiceTransport.kt, SocialCoordinator.kt, SocialRepositories.kt, SocialCoordinatorTest.kt, build.gradle.kts |
| data:voice | 22 | VoiceCallController.kt, VoicePlatform.kt, AndroidVoiceAudio.kt, VoiceCallService.kt, AndroidManifest.xml, VoiceCallControllerTest.kt, VoicePlatformTest.kt, build.gradle.kts, README.md (새 모듈) |
| feature:meet | 184 | CallScreens.kt, ConnectedViewModel.kt, RequestViewModel.kt, MeetDeps.kt, MeetState.kt, MeetScreens.kt, MeetViewModel.kt, MeetNavigation.kt, VoiceInteractionTest.kt, VoiceViewModelTest.kt, MeetFixtures.kt, MeetInteractionTest.kt, MeetNavigationTest.kt, MeetScreenshotTest.kt, MeetViewModelTest.kt |
| feature:history | 179 | HistoryDeps.kt, HistoryNavigation.kt, HistoryViewModel.kt, OnboardingScreens.kt, OnboardingViewModels.kt, FakeHistoryDeps.kt, HistoryScreenshotTest.kt, HistoryViewModelTest.kt |
| feature:signature | 117 | MyProfileScreen.kt, MyProfileViewModel.kt, SignatureNavigation.kt, SignatureStates.kt, CallSettingsTest.kt, SignatureFakes.kt, SignatureScreenshotTest.kt |
| app | 65 | AppGraph.kt, ChayeonApplication.kt, ChayeonApp.kt, MainActivity.kt, VoiceCallTarget.kt, VoiceCallTargetTest.kt, VoiceNavigationTest.kt, MainActivitySmokeTest.kt, HomeZoneNavigationTest.kt, TestGraph.kt, build.gradle.kts, INTEGRATION.md |
| server | 32 | VoiceRelay.kt, VoiceRoutes.kt, Rooms.kt, Server.kt, VoiceRelayTest.kt, VoiceServerTest.kt, build.gradle.kts |
| core:signature / data:vehicle | 53 / 28 | 변경 없이 회귀 검증 |

루트 build.gradle.kts·settings.gradle.kts·gradle/libs.versions.toml에 모듈과 WebSocket 의존성을 선언했어요. AGENTS.md, README.md, docs/architecture.md, docs/contracts.md, docs/product/experience-spec.md, docs/product/quality-bar.md, docs/proposal.md를 갱신했어요. 두 e2e 스크립트도 변경했으며 Python 문법 검사와 diff 공백 검사가 통과했어요. 커밋하지 않았어요.

### 통화 모델과 종료 경로

통화 상태는 IDLE / CONNECTING / ACTIVE / RECONNECTING이며 관계 상태와 독립적이에요. 연결 ID, 음소거, 마이크 상태만 담아요. 마이크 권한 관찰은 별도 Flow로 두어 SOLO·통화 전에도 실제 권한 표시를 복원해요. 같은 연결의 재접속은 음소거 선택을 유지하고 새 연결은 기본 음소거 해제로 시작해요.

| 경로 | 오디오와 연결 처리 |
|---|---|
| 수락 → CONNECTED | 양측 자동 참여. 서버는 현재 당사자만 허용 |
| 통화 끊기 / 알림 action | 로컬 오디오·서비스를 먼저 닫고 기존 leave 명령 실행 |
| 서로 멀어짐 / 위치 확인 불가 | CHECKING 진입부터 소켓과 오디오 종료. 기존 유예와 종료 사유 유지 |
| 발견 끔 / 차단 / 계정 삭제 | 엔진 전이 후 양측 소켓 폐쇄, 앱 관찰에서 자원 정리 |
| 로그아웃 / 토큰 교체·폐기 | 폐기 토큰 소켓과 상대 소켓 폐쇄. 이후 잘못된 토큰 입장 거절 |
| 집 보호 | 서버 응답 전 마이크·재생 중지. 주행 중 가려진 통화는 주차 전 재개하지 않음. 상대는 기존 이탈 이유만 확인 |
| 만료 | 만료 요청은 통화를 만들지 않음. 서버 presence lease 만료는 CHECKING과 기존 이탈 종료 경로 사용 |
| 네트워크 단절 | 마이크를 닫고 재접속. 연결이 아직 CONNECTED일 때만 복구, 종료 후 재참여 거절 |

기존 일시 연결에는 별도 통화 시간 TTL이 없어요. 이번 작업은 요청·presence 만료를 검사하며 새로운 관계 만료 정책을 만들지 않았어요.

서버 테스트는 같은 방의 제삼자·다른 방·알 수 없는 연결·끝난 연결 거절, 상대에게만 중계, 모든 종료 경로와 토큰 폐기, 재참여, 640 byte 크기 제한, 초당 50개·burst 50 허용, 순간 초과 DROP과 초당 100개 초과 5개 연속 1초 구간에서의 종료, 5프레임 drop-oldest 큐, 저장 모델 불변을 확인해요. 전송률 예산과 과부하 관측은 재접속으로 초기화하지 않아요. 입장·명령·revision·250ms ticker에서 검증하고 프레임마다 엔진 상태를 내보내거나 전체 소켓을 재검증하지 않아요. 실제 호스트 서버와 Python WebSocket 프록시를 통한 양방향 PCM·제삼자 거절·거리 종료·늦은 접속 거부도 통과했어요.

### 중계 정책 수정 검증

서버 테스트 38개가 통과했어요. 50프레임 순간 버스트와 초과 DROP, 재접속 예산 유지, 잘못된 크기 즉시 종료, 100 fps 초과가 5초 이어질 때 종료하는 경로를 검사했어요. 실제 `RoomRegistry`에서 10,000프레임 중계와 수신 큐 소비 동안 엔진 상태 조회 증가분은 정확히 0회였어요. leave·거리 이탈·차단·계정 삭제부터 양쪽 실제 WebSocket의 closeReason까지 각각 300ms 이내라는 테스트도 통과했어요. 기존 호스트 프록시 PCM 검증은 이 정책 수정 이전에 실행한 결과이며 기기 검증과 구분해요.

### 데모 상대의 Pleos 음성

`ai.pleos.playground:TextToSpeech:3.3.1`을 `data:voice`에서 직접 사용해요. `Mode.ON_DEVICE`만 사용하며 일반 권한 `pleos.car.permission.TTS_SERVICE`를 선언해요. 보안 그룹 권한은 추가하지 않아요. Application이 `ScriptedDemoPeerVoice`와 `PleosSpeechEngine`을 연결하고, SOLO 또는 DEMO_PEER인 상대에게는 서버 모드에서도 마이크·음성 WebSocket을 열지 않아요.

연결에서 초기 `prepare`로 SDK를 준비한 뒤 상대별 세 문장을 재생해요. 각 문장의 시작 시각부터 약 20초 뒤 다음 문장을 시작해요. 발화 완료까지 기다리되 그 시간을 간격에 포함해요. 오디오 포커스가 없으면 재생하지 않으며 포커스 상실·통화 종료·집 보호에는 즉시 `stop`해요. SDK `onStart`를 실제 소리 시작으로 해석하지 않아 `상대가 말하는 중` UI는 없어요.

팀의 이전 앱에서 관찰한 초기 약 4.5초·이후 약 3초 지연은 차연의 측정값이 아니에요. 차연에서 Pleos 음성의 실제 발화·초기 준비 효과·완료 이벤트·지연은 기기 검증하지 않았어요. 서버 통화 PCM은 초기 4프레임(오디오 80ms, 첫 프레임 도착부터 약 60ms)을 모아 재생하며, 수신 큐는 5프레임으로 제한하고 blocking AudioTrack sink가 재생 속도를 맞춰요.

### 통화 기준 이미지

기존 parked·driving·narrow 통화와 요청·종료 화면 이미지를 갱신했어요. 신규 파일은 다음과 같아요.

- feature:meet: connected_call_connecting.png, connected_call_reconnecting.png, connected_call_muted.png, connected_call_driving_muted.png, connected_call_narrow_muted.png, connected_call_mic_denied.png, connected_call_mic_unavailable.png, connected_call_driving_mic_unavailable.png.
- feature:meet: request_incoming_driving.png, request_incoming_driving_home.png, request_incoming_driving_narrow.png, request_incoming_driving_off.png.
- feature:signature: profile_incoming_on.png, profile_incoming_off.png.
- feature:history: consent_microphone_granted.png.

### 기기 확인의 한계

허용된 emulator-5570의 AVD 이름 조회가 연결 거부로 끝나 기기 스크립트는 실행하지 않았어요. 다른 에뮬레이터는 사용하지 않았어요. 컨트롤러의 기기 게이트가 필요해요. 실제 마이크·스피커 음질, Pleos 개인정보 스위치, 두 실제 차량 통신, 내비게이션과 장시간 동시 실행, App Market 승인은 확인하지 못했어요. 통화 시작 후 다른 앱으로 이동하는 수명은 구현했지만, 앱이 이미 백그라운드인 상태에서 최초 통화가 성립하면 Android FGS 시작 제한을 받을 수 있어요. 이 경우 마이크를 열지 않고 실패를 처리해요.


## 통화 검토 수정 결과

최종 `python3 scripts/dev.py verify`는 3분 32초, Gradle 작업 492개로 통과했어요. Kotlin·서버·Compose 919개, Python 8개, 기준 이미지 440장을 검증했으며 실패·오류·건너뜀은 0이에요. 모듈별 테스트는 app 67, core:engine 46, core:model 17, core:signature 53, core:ui 103, data:social 43, data:vehicle 28, data:voice 37, feature:history 179, feature:meet 191, feature:signature 117, server 38개예요. Kotlin 컴파일, 모듈 그래프, strict lint, Roborazzi 비교와 debug APK 빌드가 포함돼요.

컨트롤러 검토의 C1–C8을 반영했어요. 임시 연결의 인사 계약은 유지하고 화면·알림에서만 제외했으며, 애프터 동작은 그대로예요.

| 항목 | 변경과 검증 근거 |
|---|---|
| C1 | 4프레임 초기·언더런 버퍼 뒤 blocking 쓰기로 연속 재생. `tenSecondsAtFiftyFramesPerSecondHasNoDropOrPlaybackGap`: 10초 500프레임의 순서·누락 0·재생 간격 20ms 검사 |
| C2 | 50프레임 버스트, 초과 DROP, 잘못된 크기 또는 초당 100개 초과의 연속 5초에 종료. `stallBurstOfFiftyFramesIsForwardedAndOverrateDropsWithoutClosing`, `sustainedTrafficAboveTwiceTheRateClosesAfterFiveSeconds`, `wrongSizeFramesCloseImmediately` |
| C3 | join·명령·revision·tick에서만 멤버십 검증. `registryHotPathPerformsZeroEngineStateReadsForTenThousandFrames`: 10,000프레임 상태 조회 증가 0회. `leaveDistanceBlockAndDeletionCloseBothSocketsWithinThreeHundredMilliseconds`: 네 경로의 양쪽 소켓 종료 300ms 이내 |
| C4 | CAN_DUCK에 녹음·재생 유지, LOSS/LOSS_TRANSIENT에 쉬고 GAIN에 복구. `navigationDuckingRetainsCallFocusButLossSuspendsIt` |
| C5 | 통화 hero 확대, 공개 정보만 이야깃거리로 표시, 차단·신고 통합. `CallTopicsTest`와 Compose 상태 검증. `temporaryConnectionGreetingsNeverCreateBannersOrNotificationsInAnyDrivingPolicy`, `SocialMessageSignalsTest`로 임시 인사 알림 제거·애프터 유지 검증 |
| C6 | 주행 중 수신에 `통화 요청` 제목. Compose 단언과 주행 수신 세 기준 이미지 검증 |
| C7 | `ScriptedDemoPeerVoiceTest` 8개: 대사·간격·종료·실패·포커스·초기화 중 종료. 빠른 포커스 복구와 8초 걸린 정상 발화도 검사. 컨트롤러 테스트로 서버 데모도 마이크·WS 없음과 모든 종료 시 포커스 해제 전 음성 중단 검증 |
| C8 | 작업 계획 `docs/superpowers/` 삭제. 새 임시 파일은 저장소 밖에서만 사용 |

데모 대사는 각 30자 이하이며 아래 순서로 재생해요. 실제 소리 시작 시각은 화면 상태로 추측하지 않아요.

| 상대 | 대사 순서 |
|---|---|
| 별보는밤 | 안녕하세요, 별보는밤이에요. / 밤 풍경을 사진에 담는 걸 좋아해요. / 사진에 남기고 싶은 순간이 있나요? |
| 재즈한잔 | 안녕하세요, 재즈한잔이에요. / 재즈를 들으면 마음이 편안해져요. / 요즘 자주 듣는 곡이 있나요? |
| 느린오후 | 안녕하세요, 느린오후예요. / 잔잔한 음악과 커피를 좋아해요. / 어떤 커피를 즐겨 마시나요? |
| 바다보러가요 | 안녕하세요, 바다보러가요라고 해요. / 여행 이야기를 듣는 게 좋아요. / 서핑을 해 본 적이 있나요? |
| 숲길산책 | 안녕하세요, 숲길산책이에요. / 캠핑하며 쉬는 시간을 좋아해요. / 캠핑할 때 꼭 챙기는 게 있나요? |

이번 검토 수정 전과 비교해 기존 이미지 23장을 갱신하고 3장을 추가했어요. 변경된 26장을 모두 육안 확인했으며 주행 중 통화 이미지는 변경되지 않았어요. 아래 파일의 예전 greeting 이름은 인사 UI가 다시 나타나지 않는 회귀 검증을 위해 유지해요.

갱신:

- `app/src/test/screenshots/app-server-connected.png`
- `app/src/test/screenshots/app-solo-connected.png`
- `feature/meet/src/test/screenshots/connected_call_connecting.png`
- `feature/meet/src/test/screenshots/connected_call_mic_denied.png`
- `feature/meet/src/test/screenshots/connected_call_mic_unavailable.png`
- `feature/meet/src/test/screenshots/connected_call_muted.png`
- `feature/meet/src/test/screenshots/connected_call_narrow_muted.png`
- `feature/meet/src/test/screenshots/connected_call_reconnecting.png`
- `feature/meet/src/test/screenshots/connected_checking.png`
- `feature/meet/src/test/screenshots/connected_greeting_failed.png`
- `feature/meet/src/test/screenshots/connected_greetings_empty.png`
- `feature/meet/src/test/screenshots/connected_greetings_failure.png`
- `feature/meet/src/test/screenshots/connected_greetings_loading.png`
- `feature/meet/src/test/screenshots/connected_leave_failure.png`
- `feature/meet/src/test/screenshots/connected_leaving.png`
- `feature/meet/src/test/screenshots/connected_narrow.png`
- `feature/meet/src/test/screenshots/connected_populated.png`
- `feature/meet/src/test/screenshots/connected_reaching_350ms.png`
- `feature/meet/src/test/screenshots/connected_sending.png`
- `feature/meet/src/test/screenshots/connected_server.png`
- `feature/meet/src/test/screenshots/request_incoming_driving.png`
- `feature/meet/src/test/screenshots/request_incoming_driving_home.png`
- `feature/meet/src/test/screenshots/request_incoming_driving_narrow.png`

신규:

- `feature/meet/src/test/screenshots/connected_call_no_topics.png`
- `feature/meet/src/test/screenshots/connected_call_public_titles.png`
- `feature/meet/src/test/screenshots/connected_call_shared_interests.png`
