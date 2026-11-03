# 차연

길 위에서 시작되는 인연.

차연은 **주행 습관과 차량 관리가 프로필이 되는** 차량용 인연 앱입니다. 사진과 글로 꾸민 자기소개 대신, 옆 사람이 놀라지 않게 미리 멈추는 습관이나 떠나기 전에 차를 살피는 습관으로 나를 보여 줍니다. 근처에 있는 사람과 서로 원할 때만 잠시 이어지고, 다시 원할 때만 계속됩니다.

Pleos Connect(Android Automotive)용 앱과 서버를 담은 모노레포입니다. HMG 앱 공모전 출품작이며 이름은 가칭입니다.

## 드라이브 시그니처

차 안에서 주행과 차량 관리 신호를 요약해 상대에게 매력으로 읽히는 프로필을 만듭니다.

| 구성 | 내용 |
|---|---|
| 특성 12가지 | 주행 스타일 7가지와 차량 관리 5가지. 예: `여유로운 제동`, `깜빡이 매너`, `미리 채우는 습관`, `실내 공기 챙김` |
| 칭호 | 공개한 특성으로 만드는 한 줄 소개. 예: `떠나기 전에 살피는 드라이버` |
| 시그니처 매듭 | 습관에 따라 모양이 달라지는 붉은 3D 매듭. 궁합 화면에서는 두 사람의 매듭이 빨강과 진주색으로 엮입니다 |
| 드라이브 궁합 | 점수 없는 문장. 예: `둘 다 출발 전에 안전부터 챙겨요` |
| 주행 후 요약 | 주차하면 이번 주행의 변화를 나에게 먼저 보여 줍니다. 다른 사람이 운전했다면 뺄 수 있습니다 |
| 공개 범위 | 특성마다 공개를 고르고, 상대에게 보이는 모습을 미리 봅니다 |

원시 신호는 메모리에서만 처리하고 저장하거나 전송하지 않습니다. 상대와 서버에는 내가 공개한 특성과 단계만 갑니다. 수치, 순위, 낮은 평가는 상대에게 보이지 않고, 속도를 칭찬하지 않으며, 주행 중에는 결과를 보여 주지 않습니다. 자세한 원칙은 [드라이브 시그니처 명세](docs/product/drive-signature.md)에 있습니다.

## 3분 둘러보기

차와 서버 없이 에뮬레이터만으로 제품 전체를 볼 수 있습니다. 시연 데이터에는 항상 `합성 주행`, `합성 위치`, `시연용 가상 상대`가 표시됩니다.

1. **첫 실행:** `혼자 둘러보기(가상 상대)`를 고르고 `이 계정으로 시작`을 누릅니다.
2. **준비:** 프로필을 만들고, 차량 데이터·위치·공개 범위에 동의합니다. 차량 권한을 묻는 창에서 허용합니다.
3. **시그니처 탄생:** 홈의 `시그니처 만들러 가기`에서 주행 성격을 하나 고릅니다. 약 30초 뒤 매듭이 그려지며 칭호와 특성이 나타납니다. 다른 성격을 고르면 다른 칭호와 매듭이 나옵니다.
4. **공개:** `상대에게 어떻게 보일지 고르기`에서 시그니처를 공개합니다.
5. **만남:** 오늘의 인연에서 상대를 열어 특성과 궁합을 봅니다. 연결을 요청하면 상대가 수락하고, 인사를 주고받습니다.
6. **주행 잠금:** Virtual Console에서 기어를 D로 바꾸면 화면이 잠기고 `나가기`만 남습니다. P로 돌아오면 그대로 이어집니다.
7. **이별과 애프터:** 상단 `시연 제어`에서 `서로 멀어져요`를 누르면 확인 상태를 거쳐 연결이 끝납니다. `기록 보기`에서 궁합을 다시 보고 `애프터 신청`을 보냅니다.
8. **실제 신호:** Virtual Console로 3분 이상 주행하고 주차하면 `차량 주행` 요약이 나옵니다.

## 실행 방법

### APK 설치

Pleos 에뮬레이터의 앱 사용자는 10입니다.

```sh
adb -s emulator-5556 install --user 10 apk/chayeon-release.apk
adb -s emulator-5556 shell am start --user 10 -n dev.chayeon/.MainActivity
```

### 소스에서 빌드

JDK 17과 Android SDK 35가 필요합니다. Vehicle SDK는 빌드할 때 Pleos 저장소에서 받습니다.

```sh
python3 scripts/dev.py doctor     # 환경 확인
python3 scripts/dev.py setup-sdk  # 필요한 SDK 설치
python3 scripts/dev.py emulator   # Pleos 에뮬레이터 실행
python3 scripts/dev.py verify     # 구조 검사, 테스트, 스크린샷 비교, lint, 빌드
python3 scripts/dev.py release    # apk/chayeon-release.apk 생성
```

`release`는 시연 제어를 켜고 서버 주소 없이 빌드합니다. 서명에는 빌드할 때 만드는 로컬 일회용 키를 쓰며, 이 키는 Git에 넣지 않습니다.

### 두 대로 연결 (선택)

서버를 켜고 서버 주소를 넣어 빌드하면 첫 화면에 `두 대로 연결(서버)`이 나타납니다. 같은 방 코드에 서로 다른 테스트 계정으로 들어가면 실제 통신으로 이어집니다.

```sh
python3 scripts/dev.py server
./gradlew :app:assembleDebug -Pchayeon.serverUrl=http://10.0.2.2:8080
```

서버만 빌드할 때는 Android SDK가 필요 없습니다. `server/Dockerfile`도 있으나 이미지 실행은 확인하지 않았습니다.

```sh
./gradlew -Pchayeon.serverOnly=true :server:build
```

### 에뮬레이터 주행

```sh
python3 scripts/dev.py drive care --duration-seconds 210
```

시나리오는 `gentle`, `early-brake`, `signal-manners`, `care`, `unsupported`입니다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect v2.1.0, API 34, AVD `Pleos_Connect_v2_0` |
| 앱 창 | 1682×1210dp, 160dpi, 사용자 ID 10 |
| 차량 입력 | Virtual Console |
| 호스트 | macOS arm64 |
| 도구 | JDK 17, Gradle 8.12, Android Gradle Plugin 8.10.1, Kotlin 2.1.20 |
| SDK | compileSdk·targetSdk 35, minSdk 28, Vehicle SDK 2.3.2 |

실차와 App Market 승인 환경에서는 시험하지 않았습니다.

## APK 위치

`python3 scripts/dev.py release`를 실행하면 `apk/chayeon-release.apk`가 생깁니다. 제출할 때는 공모전 형식인 `팀명_앱이름_release.apk`로 이름을 바꿉니다.

## 검증

2026-11-04 기준으로 아래 명령을 실제로 실행한 결과입니다.

| 항목 | 명령 | 결과 |
|---|---|---|
| 구조, 테스트, 스크린샷, lint, 빌드 | `python3 scripts/dev.py verify` | 깨끗한 상태에서 통과. 테스트 714개, 스크린샷 기준 이미지 371장 |
| 에뮬레이터 종단 (release APK) | `python3 scripts/dev.py e2e --with-real-drive --skip-build --apk apk/chayeon-release.apk` | 통과. 첫 실행부터 시그니처, 연결, 주행 잠금, 이탈, 애프터, 차단, 210초 실제 신호 주행, 재시작, 로그아웃까지 |
| 실제 서버 연결 | `python3 scripts/dev.py e2e --server` | 통과. 앱과 스크립트 계정이 실제 HTTP로 요청, 수락, 인사, 종료, 기록까지 |
| APK 감사 | `python3 scripts/audit_apk.py` | 통과. 서명 확인, 테스트 코드 없음, 비디버그, 평문 통신 차단 |
| 서버 단독 빌드 | `./gradlew -Pchayeon.serverOnly=true :server:build` | Android SDK 환경 변수 없이 통과 |

확인하지 못한 것: 실차 동작, App Market 권한 승인, 물리 차량 두 대의 동시 동작, 스크린 리더 낭독, 차량 화면의 실제 가독성과 프레임 성능, Docker 이미지 실행, CI 실행.

## 한계

- 시그니처는 차량 안 클라이언트에서 계산하므로 변조를 완전히 막지 못합니다. 공개 서비스로 가려면 서명된 차량 신호나 서버 검증이 필요합니다.
- 위치는 합성 값입니다. 실제 위치 기반 근접은 구현 경로만 있고 시연하지 않습니다.
- 이 에뮬레이터 이미지는 타이어와 소모품 신호를 지원하지 않습니다. 실제 신호 주행에서 이 특성들은 `이 차량에서 제공되지 않아요`로 표시됩니다.
- 임계값은 제품 기준이며 안전이나 의학 기준이 아닙니다. 실차 센서 노이즈에 맞춘 보정은 하지 않았습니다.
- 공개 서버를 운영하지 않습니다. 시연용 로그인과 연령 자기 확인은 실제 인증이 아닙니다.
- 사용자 수요와 재방문은 측정하지 않았습니다.

## 구조

```txt
app/             앱 조립, 내비게이션, 시연 제어, 알림
core/model       도메인 모델, Repository 인터페이스, API 계약
core/signature   시그니처 계산, 칭호, 궁합, 매듭 시드
core/engine      서버와 기기 안 모드가 함께 쓰는 관계 상태 기계
core/ui          디자인 시스템, 매듭, 입체 컴포넌트
data/social      기기 안 백엔드와 서버 연동
data/vehicle     차량 신호, 주행 기록, 시그니처 저장
feature/meet     발견, 요청, 일시 연결, 주행 잠금
feature/history  첫 실행, 동의, 기록, 애프터, 차단·신고, 정리
feature/signature 내 프로필, 내 시그니처, 공개 범위, 주행 후 요약
server/          Ktor 서버
fixtures/drive   합성 주행 시나리오
scripts/         개발, 종단 검증, 릴리스 도구
```

## 문서

- [문서 색인](docs/README.md)
- [기획서](docs/proposal.md)
- [아키텍처](docs/architecture.md)
- [공통 계약](docs/contracts.md)
- [드라이브 시그니처 명세](docs/product/drive-signature.md)
- [경험 명세](docs/product/experience-spec.md)
- [품질 기준](docs/product/quality-bar.md)
- [디자인 시안](docs/design/README.md)
- [개발 안내](CONTRIBUTING.md)
