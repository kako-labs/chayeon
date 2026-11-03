# 차량 기록과 드라이브 시그니처 연결

`core:signature`는 순수 Kotlin 계산을, `data:vehicle`은 차량 신호·안전 상태·기록·저장을 맡아요. 원시 신호는 메모리에서만 처리하고 SQLite에는 파생 지표와 요약, 공개 설정, 멱등 명령 결과만 저장해요.

## 앱 조립

1. `Application`이 `VehicleGraphOwner`를 구현하고 `override val vehicleGraph = VehicleGraph(applicationContext)`를 한 번 만들어요. `onCreate()`에서 `vehicleGraph.start()`를 호출해요.
2. `AppGraph`의 기존 `EmptyDrivingStateProvider`, `EmptySignatureRepository`, `EmptyVehicleConsole` 자리에 각각 `vehicleGraph.drivingStateProvider`, `vehicleGraph.signatureRepository`, `vehicleGraph.vehicleConsole`을 넣어요. 기존 Empty 클래스는 그대로 남겨 두었어요.
3. 인증된 `SessionState.Active.session.accountId`와 `ConsentScope.VEHICLE_DATA == GRANTED`를 함께 읽어 `vehicleGraph.gateInput.update(accountId, vehicleDataConsent)`로 전달해요. 세션이 없으면 `update(null, false)`를 호출해요. 기기 안 공개 설정 토글을 차량 데이터 동의로 대신 사용하지 않아요.
4. 로그아웃·계정 삭제에서는 먼저 gate를 닫고 `signatureRepository.clearLocalData()` 성공을 확인해요. 정리는 모든 로컬 계정 데이터를 지우고, 이미 진행 중이던 기록 generation의 늦은 저장도 거부해요.
5. `VehicleRecordingService`는 gate가 열려 있을 때 포그라운드에서 `TripRecorder.start()`를 호출해요. `START_NOT_STICKY`이므로 프로세스가 끝나면 미완료 주행은 복구하지 않아요.
6. 공개 범위 변경·주행 제외 후 `SignatureRepository.signature`의 최신 공개 투영을 `ProfileRepository.publishSignature()`로 게시하거나 철회해요. 서버 게시와 차량 저장은 서로 다른 경계예요.
7. `recaps`의 미확인 항목은 **현재** 주행 정책이 `ALLOWED`일 때만 화면에 보여요. ViewModel의 driving-derived `StateFlow`도 `WhileSubscribed(5_000, replayExpirationMillis = 0)`을 사용해요.

`VehicleGraph(context, gatewayFactory)`의 factory는 차량 어댑터를 주입하는 조립 지점이에요. 주행 정책과 특성 규칙은 factory에 넣지 않아요. 자동차 기능이 있는 기기에서는 기본적으로 `PleosVehicleGateway`, 그 밖의 기기에서는 `SimulatedVehicleGateway`를 선택해요. 실제 차량 서비스가 있는 환경에서 임의로 시뮬레이터로 바꿔 잠금을 우회하면 안 돼요.

## 안전 상태와 플랫폼 권한

- 시작은 `UNKNOWN`이에요. 기어 P와 실제 주행 상태 0이 모두 신선할 때만 `ALLOWED`예요.
- 관측이 2.5초를 넘기거나 오류·권한 실패가 있으면 `UNKNOWN`이에요. 명령은 `VehicleDrivingStateProvider.current()`로 다시 검사해요.
- Android의 주행 상태 관리자는 공개 SDK에 없어서 작은 어댑터 안에 선택적으로 격리했어요. 접근이 막히면 허용 상태를 추정하지 않아요.
- **현재 Pleos 이미지의 일반 설치 앱에는 `android.car.permission.CAR_DRIVING_STATE`가 부여되지 않아요.** `pm grant --user 10`도 변경 불가능한 권한으로 거부됐어요. 일반 앱의 P에서는 `UNKNOWN`이 맞아요. 실제 배포에서 이 권한을 받을 수 있는지는 플랫폼 담당자가 확인해야 해요.
- 실기 검증의 `VehicleProofInstrumentation`만 `UiAutomation.adoptShellPermissionIdentity()`로 이 권한을 빌려 실제 상태 전이를 확인해요. 이 코드는 `src/androidTest`에만 있어 출시 APK에 포함되지 않아요.
- 속도는 `CAR_SPEED`, 표준 충전 한도 속성은 `CAR_ENERGY` 읽기 권한이 필요해요. 앱 조립 단계에서 실제 요청 흐름을 연결해야 해요. 신호별 권한이 없으면 그 신호는 `UNSUPPORTED`예요. 충전 한도는 SDK capability를 확인한 뒤 표준 `EV_CHARGE_PERCENT_LIMIT`을 읽고, 제어 API를 호출하지 않아요.

## 시연

`DefaultVehicleConsole`은 기본적으로 debuggable 앱에서만 열려요. `setGear()`는 `SimulatedVehicleGateway`에서만 동작하고 실제 차량에서는 거부돼요. `replay()`는 주차 허용·세션·차량 데이터 동의를 재검사한 뒤 실제 `TripAccumulator`에 20분 가상 시간을 약 10초로 넣어요. 모든 결과는 `SYNTHETIC`이에요. 공개 최소 근거는 그대로 3회예요.

다섯 시나리오는 `fixtures/drive/*.json`과 `src/main/assets/drive/*.json`에 같은 내용으로 들어 있어요. 빌드 설정을 바꾸지 않기 위해 두 위치를 두었고 `drive` 명령이 동일 여부를 검사해요. 테스트는 care 3회 재생으로 합성 시그니처를 확인해요.

```sh
python3 scripts/dev.py drive gentle
python3 scripts/dev.py drive early-brake
python3 scripts/dev.py drive signal-manners
python3 scripts/dev.py drive care --duration-seconds 210
python3 scripts/dev.py drive unsupported
```

CLI는 실제 시간의 Virtual Console 신호를 보내요. `--duration-seconds`는 시나리오의 시간축을 바꿔요. 앱 안 재생처럼 플랫폼 시계를 빠르게 돌리지는 못해요. `unsupported`는 앱 재생에서는 신호를 생략하지만, CLI로 에뮬레이터의 지원 capability 자체를 바꿀 수는 없어요. 콘솔이 정수 입력만 받기 때문에 속도·조향 입력을 정수로 반올림해요.

## 검증 명령

JDK 17과 Android SDK 환경 변수를 설정한 뒤 실행해요.

```sh
./gradlew :core:signature:test :data:vehicle:testDebugUnitTest :data:vehicle:lintDebug verifyArchitecture
./gradlew :data:vehicle:assembleDebugAndroidTest
adb -s emulator-5556 install --user 10 -r -g data/vehicle/build/outputs/apk/androidTest/debug/vehicle-debug-androidTest.apk
adb -s emulator-5556 shell am instrument --user 10 -w -e durationMillis 235000 dev.chayeon.data.vehicle.test/dev.chayeon.data.vehicle.VehicleProofInstrumentation
```

instrumentation이 `status=ready`를 출력하면 다른 터미널에서 `python3 scripts/dev.py drive care --duration-seconds 210`을 실행해요. 검사 대상은 실제 플랫폼 신호, 세 가지 정책 상태, 유효 주행, 새로운 저장 행, recap, 포그라운드 서비스의 별도 저장 행이에요. 결과에 원시 값·이동량·좌표는 출력하지 않아요.

`src/debug/VehicleProbeService`는 별도 디버그 진입점이에요. 외부 호출에는 shell의 DUMP 권한이 필요하고, 출시 빌드에는 포함되지 않아요. 기존 에뮬레이터를 사용하며 시작·종료하지 않아요.

## 2026-11-03 검증 결과

- 지정된 Gradle 검사에 통과했어요. `core:signature` 44개, `data:vehicle` 13개 테스트에서 실패·오류·건너뛰기가 없었고 lint와 `verifyArchitecture`도 통과했어요.
- 235초 instrumentation과 210초 `care` 주행을 함께 실행했어요. `passed=true`, `durationValid=true`, `eligibility=ELIGIBLE`, `newRows=1`이었어요. 같은 실행의 포그라운드 서비스도 `foregroundEligibility=ELIGIBLE`, `foregroundNewRows=1`이었어요. 누적 주행 3개와 리캡 3개가 저장됐어요.
- 실제 플랫폼에서 `ALLOWED`, `RESTRICTED`, `UNKNOWN`을 모두 관측했고 연결 해제 후에는 `UNKNOWN`이었어요. 이 결과에는 위에서 설명한 **instrumentation 전용 주행 상태 권한**이 필요했어요.
- 수집한 표본 수는 Speed 1500, Brake 1125, Steering 1124, TurnIndicator 1124, SeatBelt 457, Clock 231, Cruise 230, BatteryLevel 47, ChargeLimit 47, AirCleaning 47이었어요. 원시 표본 값은 보고서에 남기지 않았어요.
- `bundleReleaseAar`에 성공했고 출시 AAR의 클래스 목록에 `VehicleProbeService`와 `VehicleProofInstrumentation`이 없음을 확인했어요.

다음은 이 에뮬레이터 이미지의 **입력 지원 여부**예요. AVAILABLE이 해당 특성을 획득했다는 뜻은 아니에요. 각 특성의 조건과 최소 주행 근거를 별도로 만족해야 해요.

| 특성 | 실제 플랫폼 결과 | 근거 |
| --- | --- | --- |
| GENTLE_RIDE | AVAILABLE | 속도 표본과 평활화한 가속도 |
| EARLY_BRAKE | AVAILABLE | 속도·브레이크 표본 |
| SIGNAL_MANNERS | AVAILABLE | 속도·조향 크기·방향지시등 표본 |
| STEADY_PACE | AVAILABLE | 속도·크루즈 표본 |
| BELT_FIRST | AVAILABLE | 운전석 벨트·기어 표본 |
| LONG_ROAD | AVAILABLE | 속도를 적분한 파생 거리 |
| NIGHT_DRIVE | AVAILABLE | 차량 시계 표본, 기본 비공개 |
| TIRE_CARE | UNSUPPORTED | TIRE_PRESSURE와 TIRE_WARNING capability가 미지원 |
| ENERGY_PLANNER | AVAILABLE | BATTERY_LEVEL 지원, FUEL_LEVEL 미지원 |
| BATTERY_KIND | AVAILABLE | CHARGE_LIMIT 지원, CAR_ENERGY 권한 포함 |
| FLUID_READY | UNSUPPORTED | OIL_WARNING·BRAKE_FLUID_WARNING·WASHER_FLUID_WARNING 미지원 |
| CABIN_CARE | AVAILABLE | AIR_CLEANING 표본 |

Virtual Console의 타이어 값을 설정해도 SDK capability는 미지원이었어요. 지원하지 않는 값을 0으로 대체하지 않았어요. 일반 설치 앱에 대한 `pm grant --user 10 dev.chayeon android.car.permission.CAR_DRIVING_STATE`는 `SecurityException`과 종료 코드 255로 거부됐어요.

## 확인 범위

실차와 마켓 권한 승인은 확인하지 못했어요. 위 instrumentation 권한 성공을 일반 설치 앱의 권한 성공으로 해석하면 안 돼요. `SignaturePolicy`의 제품 수치는 명세와 같고, 명세에 없는 평활화·턴 탐지·정속 탐지 세부 기준은 구현 선택으로 구분했어요. 에뮬레이터 신호로 실제 차량의 노이즈 정확도를 주장하지 않아요.
