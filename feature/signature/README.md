# 시그니처 화면

`SignatureNavigation.kt`의 `signatureGraph(deps, chrome, navController, onExit)`가 다섯 화면을 등록해요. 화면은 불변 상태와 콜백만 받고, 저장소 명령은 화면별 ViewModel에서 실행해요.

| 화면 | Composable | ViewModel |
|---|---|---|
| 내 프로필 | `MyProfileScreen` | `MyProfileViewModel` |
| 내 시그니처 | `MySignatureScreen` | `SignatureViewModel` |
| 특성 상세 | `TraitDetailScreen` | `TraitDetailViewModel` |
| 공개 범위 | `SignatureVisibilityScreen` | `SignatureVisibilityViewModel` |
| 주행 후 요약 | `TripRecapScreen` | `TripRecapViewModel` |

## 통합

- 앱의 기존 `SignatureDeps`에 `ProfileRepository`, `SignatureRepository`, `DrivingStateProvider`, `VehicleConsole`의 실제 구현을 넣어요. `ChayeonTheme`은 실제 주행 정책을 받고 `ScreenChrome`은 실제 기어·발견·최상위 탐색을 담당해요.
- `SignatureExit.OpenDemoConsole`을 앱의 시연 제어 화면에 연결해요. 현재 앱의 이 분기는 무동작이에요. 다른 exit와 그래프 호출은 기존 계약을 유지해요.
- 앱이 `SignatureRepository.recaps`와 주행 정책을 관찰해, 미확인 요약이 있고 `ALLOWED`일 때 `SignatureDestination.recap(recap.id)`로 이동해요. 요약 읽음 처리는 `TripRecapViewModel.markSeen()`이 성공한 뒤 화면 내부에서 이동해요.
- `DefaultSignatureEngine`의 실제 계산 구현을 합쳐야 해요. 현재 공용 엔진은 중립 결과를 반환하는 골격이에요. 기능은 `publicProjection`, `title`, `knot`을 사용하며 별도 계산 규칙을 만들지 않아요. 숨긴 특성·기록 중·미지원 특성이 공개 투영에 들어가지 않도록 한 번 더 검사해요.
- 공개 설정·주행 제외는 기기 저장과 서버 게시가 별도 명령이에요. 같은 화면에서 실패를 알리고 동일 명령으로 재시도해요. 공개 설정 화면을 다시 열었을 때 미반영 공개 내용은 `공개 내용 반영`으로 복구할 수 있어요. 프로세스 종료를 넘는 전송 보장은 저장소 통합에서 확인해야 해요.

## 검증 범위

`src/test/screenshots`에 넓은 창·좁은 창, 불러오기·비어 있음·실패·주행 제한·상태 불명, 기록 중·미지원·제외·변화 없음 기준 이미지가 있어요. 주행 요약 연출은 600ms·1200ms도 캡처해요.

`SignatureViewModelTest`는 실제 상태 변화, 정책 재확인, 재구독, 중복, 멱등 재시도, 게시 중 공개 설정 변경, 제외와 화면 이동의 경합을 검증해요. `SignatureInteractionTest`는 Compose 조작과 fake 저장소를 연결해 주요 명령·실패 복구·좁은 창 스크롤·76dp 터치 영역을 확인해요.

실행 환경에 JDK 17과 Android SDK를 지정한 뒤 실행해요.

```sh
./gradlew :feature:signature:testDebugUnitTest :feature:signature:verifyRoborazziDebug :feature:signature:lintDebug verifyArchitecture
```

실차 신호, 실제 서버 동기화, 프로세스 종료 후 복구, TalkBack과 차량 화면의 야간 밝기는 이 모듈의 JVM 테스트로 검증하지 않았어요.
