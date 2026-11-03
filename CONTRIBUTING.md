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
python3 scripts/dev.py emulator
python3 scripts/dev.py drive gentle
python3 scripts/dev.py release
```

서버 실행 골격은 `/health`만 제공합니다. 사회 기능 라우트는 서버 담당자가 구현합니다.
Android SDK 없이 `-Pchayeon.serverOnly=true`로 서버와 순수 Kotlin 모듈만 포함합니다.
앱의 서버 주소는 `-Pchayeon.serverUrl=https://…`로 빌드에 넣으며 기본값은 빈 문자열입니다.
release 도구는 서명되지 않은 APK를 Git에서 제외된 `apk/`에 만듭니다.

Pleos AVD 이름은 `Pleos_Connect_v2_0`입니다. 이미지는 Pleos SDK Update Site에서 별도 설치합니다.
에뮬레이터는 `-no-snapshot -no-audio -no-boot-anim -gpu host -netdelay none -netspeed full`로 실행하며,
기본은 `-no-window`이고 `--visible`로 화면을 띄웁니다. adb Activity 명령은 Pleos 사용자 `--user 10`을 씁니다.
`drive`는 포트 8088을 전달하고 Virtual Console 세션을 갱신하면서 합성 신호를 넣습니다.
지원 시나리오는 `park`, `drive`, `neutral`, `reverse`, `gentle`, `stop-and-go`입니다.
Virtual Console HTTP는 에뮬레이터에서 관찰한 동작이며 공식 지원 API가 아닙니다.
`e2e`는 담당 기능이 구현되기 전까지 설명과 종료 코드 2를 반환합니다. 성공으로 간주하지 않습니다.

실차, App Market 권한 승인, 포그라운드 서비스 장시간 유지, APK 서명·배포는 별도 검증이 필요합니다.
