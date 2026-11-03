# 차량 기록과 시그니처

`VehicleGraph`는 차량 신호·정책·기록 게이트·SQLite 저장소를 소유해요. 원시 신호는 메모리에서만 처리하고 파생 지표와 요약만 저장해요. 계산은 `DefaultSignatureEngine`에 맡겨요.

## 앱 연결

- `ChayeonApplication : VehicleGraphOwner`가 그래프를 한 번 생성하고 `start()`를 호출해요. `AppGraph`에는 실제 `drivingStateProvider`, `signatureRepository`, `vehicleConsole`을 넣어요.
- 세션의 계정과 현재 문서 버전의 VEHICLE_DATA 동의로 `gateInput.update(accountId, consent)`를 호출해요. UI의 주행 잠금 때문에 동의가 숨겨지지 않도록 `SocialGraph.recordingSettings`를 써요.
- 로그아웃·계정 삭제는 게이트를 먼저 닫고 `clearLocalData()` 결과를 확인해요. 정리 실패를 성공으로 돌려주지 않으며, 이전 기록 generation의 늦은 저장을 거절해요.
- Activity의 `onResume/onPause`가 `setForeground()`를 호출해요. 게이트가 열리고 Activity가 전경일 때 `VehicleRecordingService`를 시작해요. Android 14 백그라운드 시작 제한을 우회하지 않아요.
- 속도·에너지 권한 요청 후 `permissionsChanged()`로 속도 구독을 다시 시도해요. 속도 콜백과 연료·배터리·충전 한도 조회 때도 현재 Android 권한을 다시 검사해요. 거부하거나 지원하지 않는 신호는 UNSUPPORTED예요.
- 짧은 기어 전환은 TOO_SHORT로 버려 주차 복귀 화면을 방해하지 않아요. 충분한 주행을 마치면 주차 때 요약을 저장해요. 앱이 현재 ALLOWED일 때만 미확인 요약을 열어요.
- 공개 범위와 주행 제외 후의 공개 투영은 앱의 `SignaturePublisher`가 게시하거나 철회해요. 개인 지표와 근거는 소셜 API로 보내지 않아요.

## 일반 설치의 주행 정책

`AutomotiveBackend`는 공개된 `CarUxRestrictionsManager`를 사용해요. `Car.createCar()`의 서비스 연결 콜백, `Car.CAR_UX_RESTRICTION_SERVICE`, 변경 리스너와 현재 제한 조회를 함께 사용해요. 특별한 주행 상태 권한이 필요하지 않아요. [Android 공개 API](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager)를 기준으로 구현했어요.

| 입력 | 정책 |
| --- | --- |
| 신선한 SDK 기어 P, Requires DO=false, 서비스 연결됨 | ALLOWED |
| 신선한 D/N/R 또는 Requires DO=true | RESTRICTED |
| 신호 없음·오류·2.5초 초과·서비스 연결 끊김 | UNKNOWN |

속도 0을 주차로 해석하지 않아요. `VehicleDrivingStateProvider.current()`는 명령 때 원본과 단조 시계를 다시 검사해요. 관찰 StateFlow는 `WhileSubscribed(5_000, replayExpirationMillis=0)`으로 초기 UNKNOWN을 복원해요. 앱이 기능과 소셜 명령에 주입하는 Flow도 수집을 시작할 때 `current()`를 다시 읽어요.

일반 설치에서 부여되지 않는 `CAR_DRIVING_STATE`는 manifest에서 제거했어요. instrumentation도 shell 권한을 대여하지 않아요. 디버그의 `VehicleConnectionProofReceiver`는 앱 자신의 차량 클라이언트 연결만 끊고 복원해요. 공유 차량 서비스나 에뮬레이터를 종료하지 않아요. 이 수신기와 기존 probe/instrumentation은 release에 포함되지 않아요.

## 합성 시연

`VehicleGraph(demoEnabled=...)`가 `DefaultVehicleConsole`에 빌드 설정을 전달해요. 시연 여부를 debuggable로 추정하지 않아요. 기어 조작은 `SimulatedVehicleGateway`에서만 가능해요. 실제 차량 서비스에서는 시연 UI가 이를 설명해요.

`fixtures/drive/*.json`이 유일한 시나리오 원본이에요. Gradle의 `assets.srcDir`와 Python CLI가 같은 파일을 읽어요. 재생은 실제 `TripAccumulator`에 가상 시간 표본을 공급하며 결과는 항상 SYNTHETIC이에요. 세 번을 채우는 앱 동작은 약 30초이며 공개 최소 근거도 3회 그대로예요.

```sh
python3 scripts/dev.py drive care --duration-seconds 210
python3 scripts/dev.py e2e --with-real-drive
```

CLI는 Virtual Console 세션을 약 1초마다 유지해요. 에뮬레이터에 넣은 신호는 플랫폼 VEHICLE 경로로 수집하지만, 실차 검증이라는 뜻은 아니에요. CLI로 차량 capability를 바꿀 수 없으므로 `unsupported`는 앱 안 합성 재생으로 확인해요.

## 한계

실차의 노이즈·재연결 동작과 App Market 승인은 확인하지 못했어요. 서비스가 사라지면 허용을 추정하지 않아요. 프로세스가 종료된 미완료 주행은 복원하지 않으며, `START_NOT_STICKY` 서비스는 사용자가 앱을 다시 열면 게이트를 확인해 재시작해요.
