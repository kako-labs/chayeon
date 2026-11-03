# 만남 화면 구현 계획

목표는 발견부터 요청, 연결, 종료까지 서버 상태로 이어지는 만남 경험이에요. 공통 계약과 그래프 함수는 유지하고 `feature/meet`만 수정해요. 커밋은 통합 담당자가 진행해요.

- [x] 화면 상태와 화면별 ViewModel: 현재 주행 정책 재확인, 명령별 진행 상태, 같은 본문의 멱등 재시도, 서버 기준 카운트다운을 구현했어요.
- [x] 순수 화면: Red 시안과 core:ui 토큰을 사용해 넓은 창과 좁은 창, 오류·잠금 상태를 구현했어요. 상대 카드에서는 매듭·칭호·공개 특성·궁합·관심사 순서를 지켜요.
- [x] 그래프: 요청 수락과 연결 종료를 일회성 효과로 이동하고 주행 잠금에서 복귀할 때 화면 맥락을 보존해요.
- [x] 검증: 가짜 저장소로 재시도·중복·명령 중 상태 변경을 검증하고 Compose 조작 테스트와 화면별 Roborazzi 기준 이미지를 만들었어요.
- [x] 최종 검토: 화면 PNG와 시안을 비교하고 자체 반박 검토 후 모듈 단위 테스트, Roborazzi, lint, 아키텍처 검사를 통과했어요.

화면 구현과 화면 테스트는 화면 담당자가, 상태·ViewModel·내비게이션과 동작·모션 테스트는 주 작업자가 맡았어요. 읽기 전용 검토 담당자가 요청 선택, 재시도, 연결 변경 경합을 검토했어요.

## 검증 결과

다음 최종 명령은 성공했어요. 테스트 131개에 실패·오류·건너뜀은 없어요. 화면 기준 이미지는 90개예요.

```sh
ASDF_JAVA_VERSION=openjdk-17.0.2 ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :feature:meet:testDebugUnitTest :feature:meet:verifyRoborazziDebug :feature:meet:lintDebug verifyArchitecture --console=plain
```

- `MeetViewModelTest`: 21개. 정책 변경, 재구독 초기화, 중복 호출, 같은 본문의 재시도, 요청 선택, 서버 시각 미확인·0초, 연결 변경 시 인사 격리를 검증했어요.
- `MeetInteractionTest`: 15개. 발견 전환, 요청·인사 실패와 재시도, 주행 잠금과 나가기, 좁은 창 스크롤을 실제 Compose 조작으로 확인했어요.
- `MeetNavigationTest`: 3개. 주행 후 홈 복귀, 서버 상태로 연결·종료 이동, 목록 동기화 전 새 요청 선택을 검증했어요.
- `MeetMotionTest`: 4개. 연결·종료 프레임 차이와 주행 제한·애니메이터 배율 0에서 정지를 확인했어요.
- `MeetScreenshotTest`: 88개. 1682×1210과 840×1210 크기에서 정상·로딩·없음·실패·주행 제한·미확인 상태를 비교했어요. 모션 프레임 2개를 더해 기준 이미지는 90개예요.

초기 실행에서는 Java/SDK 환경 누락, 테스트 종료 처리, 비동기 UI 대기, Robolectric 캡처 방식, lint 오류가 발견됐어요. 모두 수정한 뒤 위 최종 명령을 통과했어요. 의존성이나 빌드 설정은 바꾸지 않았어요.

## 통합 지점

- `meetGraph(deps, chrome, navController, onExit)`를 기존 NavHost에 등록해요. 진입점은 `MeetDestination.HOME`이에요.
- `MeetDeps`에 `DiscoveryRepository`, `ConnectionRepository`, `ProfileRepository`, `DrivingStateProvider`, `ServerTimeProvider` 구현을 공급해요.
- 앱에서 실시간 주행 정책을 전달하는 `ChayeonTheme`로 감싸고, `ScreenChrome`의 상단·하단 바를 공급해요. 앱 공통 내비게이션도 주행 정책을 확인해야 해요.
- `MeetExit.OpenMySignature`는 내 시그니처로, `MeetExit.OpenSafety(peerProfileId)`는 안전 화면으로, `MeetExit.OpenEncounter(encounterId)`는 해당 스친 인연 기록으로 연결해요.
- 그래프·Deps·공유 모델 계약을 바꾸지 않았어요. 커밋은 하지 않았어요.

## 확인하지 못한 범위

Pleos 실기기와 통합 앱, 실제 서버 연결은 실행하지 않았어요. 실기기의 프레임 성능·주야간 가독성과 실제 서버의 동시 수락·만료 이벤트 순서는 통합 환경에서 확인해야 해요.
