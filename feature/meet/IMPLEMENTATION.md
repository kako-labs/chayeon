# 만남 화면 교정 결과

## WHAT EXISTS

이번 교정은 `feature/meet` 안에서만 진행했어요. 커밋하지 않았어요.

| 항목 | 결과 | 구현 |
| --- | --- | --- |
| 1. 상대 중심 후보 화면 | 완료 | `MeetCandidateContent.kt`: 320dp 아바타 접시, 큰 닉네임과 필수 라벨, 작은 시그니처 매듭, 주행 스타일·차량 관리 두 그룹, 작은 궁합, 관심사 한 줄, 고정 행동 영역이에요. 1682×1210에서 특성 12개와 공개 문구, 궁합 문구 3개, 관심사 5개가 모두 보여요. |
| 2. 받은 요청 | 완료 | `MeetPendingRequest`: 보낸 요청과 같은 전체 너비 무대, 두 마스코트와 실, 아래 칭호 띠, 차단·신고 / 거절 / 수락의 단일 행이에요. |
| 3. 주행 중 새 연결 | 완료 | `MeetViewModel.effectsFor`와 내비게이션 소비자가 ALLOWED를 확인해요. `ConnectedViewModel`은 주차 중 공개하지 않은 연결의 신원을 숨겨요. 일반 안내와 나가기만 보이고, 주차하면 연결 화면으로 이동해요. |
| 4. 대화 말풍선 | 완료 | `MeetGreeting`: 텍스트 너비에 맞추고 부모 너비의 88%와 528dp 중 작은 값을 최대 너비로 사용해요. |
| 5. 기존 연결 주행 화면 | 완료 | `MeetConnectedDriving`: 큰 실과 아바타를 화면 가운데 배치하고 잠금 안내와 나가기를 유지해요. |
| 6. 미발행 시그니처 초대 | 완료 | `MeetSignatureInvitation`: 후보 목록 위에 설명과 만들기 버튼을 보여요. 발행하면 사라지고 `MeetExit.OpenMySignature`로 이어져요. |
| 7. 배너 도착 시 밀림 | 미완료: 소유 범위 밖 | `app`의 `ChayeonApp`이 `ScreenChrome.topBar`의 Column에 `TopBanner`를 조건부로 삽입해요. 앱 담당자가 상단 공간을 고정하거나 겹쳐 표시해야 해요. |
| 8. 종료 후 기록 | 완료 | `EndedScreen`: 기록 안내와 더 넓고 큰 기록 보기 버튼을 배치하고 `MeetExit.OpenEncounter`를 호출해요. |

넓은 화면의 `MeetPane`에도 공통 `scrollEdgeFade`를 적용했어요. 기존 공유 매듭 재질과 문구를 사용하고 화면·상태별 기준 이미지 96개를 유지해요.

## COMMANDS

최종 검사는 다음 명령 그대로 실행했고, 40초 만에 성공했어요. 테스트 145개 모두 통과했고 실패·오류·건너뜀은 없어요. Roborazzi 비교, lint, 아키텍처 검사도 통과했어요.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :feature:meet:testDebugUnitTest :feature:meet:verifyRoborazziDebug :feature:meet:lintDebug verifyArchitecture
```

기준 이미지 기록 명령도 성공했어요. 생성된 PNG를 직접 열고 전체 96개를 검토했으며, 주요 화면은 시안과 나란히 비교했어요.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :feature:meet:recordRoborazziDebug
```

반복 중 사용한 선택 검사 명령과 실제 결과예요.

```sh
./gradlew :feature:meet:testDebugUnitTest --tests 'dev.chayeon.feature.meet.MeetViewModelTest'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :feature:meet:testDebugUnitTest --tests 'dev.chayeon.feature.meet.MeetViewModelTest'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :feature:meet:testDebugUnitTest --tests 'dev.chayeon.feature.meet.MeetViewModelTest'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :feature:meet:testDebugUnitTest --tests 'dev.chayeon.feature.meet.MeetViewModelTest' --tests 'dev.chayeon.feature.meet.MeetNavigationTest' --tests 'dev.chayeon.feature.meet.MeetInteractionTest'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :feature:meet:testDebugUnitTest --tests 'dev.chayeon.feature.meet.MeetInteractionTest.everyPublicTraitAndPhraseIsVisibleWithoutScrolling'
git diff --check
```

- 첫 두 명령은 Java 미설정, SDK 경로 미설정으로 각각 실패했어요. 셸 환경만 지정해서 해결했어요.
- ViewModel 선택 검사는 수정 전 주행 중 연결 이동 회귀를 재현했어요: 23개 중 1개 실패했어요.
- 세 클래스 선택 검사는 잘못된 테스트 import를 수정한 뒤 47개 모두 통과했어요.
- 최대 콘텐츠 검사 확장 후 기록 실행에서 145개 중 1개가 실패했어요. Compose 조작 테스트에도 스크린샷과 같은 Robolectric NATIVE 그래픽 모드를 적용했고, 마지막 단일 선택 검사와 전체 기록·최종 검사를 모두 통과했어요.
- `git diff --check`를 통과했어요. 의존성이나 빌드 설정은 바꾸지 않았어요.

최종 테스트 구성은 `MeetViewModelTest` 25개, `MeetInteractionTest` 17개, `MeetNavigationTest` 5개, `MeetMotionTest` 4개, `MeetScreenshotTest` 94개예요. 화면 94개와 모션 프레임 2개가 기준 이미지 96개를 구성해요.

## SELF-REVIEW

- P2: 앱 상단 배너가 나타나면 여전히 콘텐츠 높이가 바뀔 수 있어요. 원인이 소유 범위 밖이라 7번은 미완료로 남겼어요.
- 자체 검토에서 정책 변화로 건너뛴 연결 이동이 다시 발행되지 않는 경로를 찾아 `distinctUntilChanged` 순서를 고치고 회귀 테스트를 추가했어요. 기존 연결 화면에 다른 연결이 도착하는 경우도 신원을 숨기는지 검증했어요.
- 긴 닉네임의 마지막 한 글자만 줄바꿈되는 문제와 종료된 연결의 주행 중 안내 문구를 수정했어요. 최대 프로필 내용은 스크롤 없이 표시되는지 조작 테스트와 PNG로 확인했어요.

## CONTRACT CHANGES

none. `core/model`을 포함한 소유 범위 밖 파일은 변경하지 않았어요.

## INTEGRATION NOTES

- `meetGraph(deps, chrome, navController, onExit)`와 `MeetDeps`의 기존 연결을 유지해요. 새로운 저장소나 의존성은 필요하지 않아요.
- `MeetExit.OpenMySignature`는 내 시그니처 화면, `MeetExit.OpenEncounter(encounterId)`는 해당 기록 상세 화면에 연결해요. 기존 `MeetExit.OpenSafety(peerProfileId)` 연결도 유지해요.
- 앱 담당자가 `ChayeonApp`의 `ScreenChrome.topBar` 안에서 조건부 `TopBanner`가 공간을 추가하지 않도록 고정 공간 또는 겹침 배치를 적용해야 해요. 이 수정 이후 앱 상단 바를 포함해 후보 화면 높이를 다시 확인해야 해요.

## UNVERIFIED

수정 후 Pleos 실기기 배포와 통합 앱 실행, 실제 서버 연결은 수행하지 않았어요. 제공된 기존 실기기 캡처를 읽고 새 화면은 Robolectric NATIVE PNG로 비교했어요. 실기기의 주야간 가독성·프레임 성능, 실제 서버 이벤트 순서, 앱 배너를 포함한 최종 통합 배치는 검증하지 못했어요.
