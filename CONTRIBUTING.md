# 함께 개발하기

JDK 17과 Android SDK 35를 사용합니다. `python3 scripts/dev.py doctor`로 확인하고,
필요한 SDK 패키지는 `python3 scripts/dev.py setup-sdk`로 설치합니다.
`JAVA_HOME`, `ANDROID_HOME` 또는 Git에서 제외된 `local.properties`를 설정할 수 있습니다.
도구는 macOS·Linux·Windows의 표준 SDK 위치도 확인하며 개인 경로를 저장하지 않습니다.

## 작업과 검토

- 역할별 브랜치(`foundation/…`, `feature/…`, `data/…`, `server/…`)에서 작업합니다.
- 담당 경로를 먼저 확인합니다. 다른 역할의 변경은 되돌리지 않습니다.
- 공유 계약은 `core:model`과 [계약 문서](docs/contracts.md)에서 확인합니다.
- 새 모듈이나 의존 방향은 [아키텍처](docs/architecture.md)와 `allowedEdges`를 함께 검토합니다.
- PR에는 바뀐 동작, 검증 명령·결과, 시각 변경의 스크린샷, 확인하지 못한 플랫폼 조건을 적습니다.
- 공유 자료에는 역할명만 씁니다. 실명, 개인 계정, 비밀 값, 로컬 경로, SDK·AVD·캐시는 넣지 않습니다.
- 작업자는 커밋하지 않습니다. 통합 담당자가 검토한 변경을 `type: 간결한 설명`으로 커밋합니다.
  `--no-verify`, 게시된 커밋 수정, main 강제 push를 사용하지 않습니다.

## 검증

```sh
python3 scripts/dev.py verify
./gradlew :core:ui:recordRoborazziDebug
./gradlew :core:ui:verifyRoborazziDebug
env -u ANDROID_HOME -u ANDROID_SDK_ROOT ./gradlew -Pchayeon.serverOnly=true :server:build
```

`verify`는 허용 의존 방향, 모든 JVM·Android 호스트 테스트, Roborazzi 비교,
경고를 오류로 처리하는 lint, debug 조립을 실행합니다. Kotlin 컴파일도 포함됩니다.
의도적으로 디자인을 바꿀 때만 스크린샷을 다시 기록하고 PNG를 직접 검토합니다.

기준 이미지는 저장소 크기를 줄이려고 절반 크기로 저장합니다. 원본 크기로 보려면 `./gradlew :core:ui:recordRoborazziDebug -Pchayeon.screenshotScale=1.0`으로 기록한 뒤, 커밋 전에 기본 크기로 다시 기록합니다.
기준 이미지는 각 UI 모듈의 `src/test/screenshots`에 둡니다.
테스트 규격은 `config/android-test.properties`의 1682×1210dp, landscape, mdpi입니다.

버전은 `gradle/libs.versions.toml`에 고정했습니다. 병렬 작업 중 lock 파일 경합을 막기 위해
dependency locking은 사용하지 않습니다. 새 의존성을 임의로 추가하지 않고 빌드 담당자와 조율합니다.
Ktor 3.1.3은 Kotlin 2.1.20·coroutines 1.10.2와 같은 도구 체계를 사용합니다.
Kotlin 컴파일은 Gradle 프로세스 안에서 실행하며 개인별 Gradle home을 설정하지 않습니다.
Robolectric 4.14.1의 다운로드 잠금은 사용자 홈에 고정되어 있어 Android 테스트 JVM만
`.gradle/robolectric/home`을 홈으로 사용합니다. Maven 캐시는 기존 `.m2/repository`를 유지합니다.
Gradle 자체의 홈과 프로젝트 밖 설정은 바꾸지 않습니다.

## 서버와 시연 도구

```sh
python3 scripts/dev.py server
docker build -f server/Dockerfile -t chayeon-server .
python3 scripts/dev.py emulator --port 5570 --grpc 8590
python3 scripts/dev.py drive gentle
python3 scripts/dev.py e2e --skip-build --apk apk/chayeon-release.apk
python3 scripts/dev.py e2e --server
python3 scripts/dev.py release
python3 scripts/public_probe.py <운영 주소>
```

- **서버:** `server`는 로컬에서 전체 서버(REST 명령, `/v1/sync` 긴 폴링, `/v1/voice` WebSocket)를 실행합니다. Android SDK 없이 `-Pchayeon.serverOnly=true`로 서버와 순수 Kotlin 모듈만 빌드합니다. 운영은 [배포 안내](deploy/README.md)를 따릅니다.
- **서버 주소:** 앱의 서버 주소는 빌드할 때 넣습니다. `CHAYEON_SERVER_URL=https://… python3 scripts/dev.py release` 또는 `-Pchayeon.serverUrl=https://…`이며, 비우면 서버 모드 없이 `혼자 둘러보기`로 시작합니다.
- **release:** R8을 적용한 APK를 Git에서 제외된 `apk/`에 만들고, `build/signing/`에 생성한 로컬 일회용 키로 서명합니다. `scripts/audit_apk.py`가 서명, 시험용 클래스 제외, 디버그 불가, 평문 통신 제한을 검사합니다.
- **에뮬레이터:** Pleos AVD 이름은 `Pleos_Connect_v2_0`이고 이미지는 Pleos SDK Update Site에서 따로 설치합니다. `emulator`는 `-no-snapshot -no-boot-anim -gpu host -netdelay none -netspeed full`로 띄우며 기본은 창 없이(`--visible`로 화면 표시) 소리는 켭니다. adb Activity 명령은 Pleos 사용자 `--user 10`을 씁니다.
  - 종단 도구와 영상 키트는 `emulator-5570`(`--port 5570`)을 씁니다. 화면·소리 녹화는 gRPC가 필요하므로 `--grpc 8590`을 붙입니다.
  - 맥을 재부팅한 직후 띄운 에뮬레이터는 DNS를 받지 못해 모든 도메인이 풀리지 않을 수 있습니다. 앱에는 "연결이 끊겨 처리 결과를 확인하지 못했어요"가 뜹니다. 기기 재부팅이나 비행기 모드로는 고쳐지지 않으므로 `adb -s emulator-5570 emu kill` 뒤 `--dns 1.1.1.1,8.8.8.8`을 붙여 다시 띄웁니다.
  - 동시에 무거운 빌드와 종단 검증을 돌리면 에뮬레이터가 불안정해질 수 있습니다. 순서대로 실행합니다.
- **차량 신호:** `drive`는 포트 8088을 전달하고 Virtual Console 세션을 갱신하면서 `fixtures/drive`의 합성 주행(`gentle`, `early-brake`, `signal-manners`, `care`, `unsupported`)을 넣습니다. Virtual Console HTTP는 에뮬레이터에서 관찰한 동작이며 공식 지원 API가 아닙니다.
- **종단 검증:** `e2e`는 실행 중인 Pleos 에뮬레이터 한 대에 사용자 10으로 정상 설치하고 실제 UI를 조작합니다. 기본은 debug, `--apk`는 지정 APK, `--with-real-drive`는 210초 실제 신호 주행, `--server`는 로컬 서버와 HTTP 상대를 띄워 두 계정 통화를 확인합니다. 화면과 결과는 `build/e2e/`에 남습니다. 에뮬레이터를 시작하거나 끄지 않습니다.
- **공개 서버 점검:** `public_probe.py`는 공개 주소에서 두 계정 음성 왕복과 제3자 거절을 확인하고 점검 계정을 지웁니다.

실차, App Market 권한 승인, 포그라운드 서비스 장시간 유지, 마켓 배포 서명은 별도 검증이 필요합니다.
