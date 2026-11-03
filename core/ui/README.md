# 차연 디자인 시스템

`dev.chayeon.core.ui`의 명명된 컴포넌트를 사용합니다. 화면에서 Material 기본 표면을 섞거나 색·글자 토큰을 다시 만들지 않습니다. 토큰의 근거는 `docs/design/screens/RedStyle.dc.html`, `RedHome.dc.html`, `RedConnected.dc.html`입니다.

## 시작

- `ChayeonTheme(policy, animatorScaleOverride)`가 주행 정책과 장식 모션을 제공합니다. 정책 기본값은 `UNKNOWN`입니다. `animatorScaleOverride`는 갤러리·테스트용이며 제품에서는 시스템 배율을 그대로 사용합니다.
- `ScreenScaffold(topBar, bottomBar, primary, secondary)`는 1100dp 이상에서 두 열, 그 미만에서 스크롤 가능한 한 열을 만듭니다. 기본 화면은 1682×1210dp입니다.
- `ChayeonColors`, `ChayeonType`, `ChayeonSpacing`, `ChayeonRadii`, `ChayeonElevation`, `ChayeonMotion`을 사용합니다.
- `JellyButton`·토글·라디오·체크박스·하단 메뉴는 `ALLOWED`일 때만 동작합니다. 나가기에는 `JellyButton(allowWhenRestricted = true)`를 명시합니다. 기능의 ViewModel과 저장소도 별도로 정책을 검사해야 합니다.

## 컴포넌트

| 묶음 | API |
|---|---|
| 표면 | `ChayeonBackground`, `GlassCard`, `HeroPanel` |
| 입력 | `JellyButton`, `GelToggle`, `GelRadioRow`, `GelCheckbox`, `PresetButton` |
| 표시 | `ChayeonText`, `IconOrb`, `StatusChip`, `InterestTag`, `ChatBubble`, `StateCard` |
| 탐색 | `BackButton`, `StepIndicator`, `GearStrip`, `TopBanner`, `BottomNav`, `ScreenScaffold` |
| 에셋 | `MochiAvatar`, `AvatarDish`, `ThreadStage`, `ChayeonAssets` |
| 시그니처 | `SignatureKnot(seed, interlockedWith)`, `ThreadView(stage)` |

`ExperienceState` 18종은 `StateCard`에 대응합니다. `onAction`이 없으면 행동 버튼을 생략합니다. 상대 차단 여부나 비공개 상태의 이유를 추측해 표시하지 않습니다. 의미 있는 에셋에는 설명이 있고, 중복 장식에는 `decorative = true`를 씁니다.

`SignatureKnot`은 시드를 섞어 고리의 위상·기울기·폭을 정하고, 3차원 곡선을 투영해 깊이 순서로 그립니다. 어두운 외곽·붉은 몸체·가는 반사광을 따로 그려 교차점의 위아래를 보존합니다. 두 매듭도 같은 깊이 정렬에 참여합니다.

## 글꼴과 이미지

Asta Sans 원본은 [Google Fonts의 ofl/astasans](https://github.com/google/fonts/tree/main/ofl/astasans)에서 가져왔습니다. `src/main/res/font/asta_sans.ttf`와 배포 필수 원문 `src/main/res/raw/asta_sans_ofl.txt`를 함께 보관합니다. 원본 이미지 14개는 `drawable-nodpi`에 동일 내용으로 복사했고 파일명의 하이픈만 밑줄로 바꿨습니다.

## 검증

```sh
./gradlew :core:ui:recordRoborazziDebug
./gradlew :core:ui:verifyRoborazziDebug :core:ui:lintDebug
```

스크린샷 기준은 `src/test/screenshots`입니다. 갤러리는 모든 구성 요소와 18 상태, 넓은 창·좁은 창을 포함합니다. 개별 구성 요소는 같은 `w1682dp-h1210dp-land-mdpi` 환경에서 독립 캡처합니다. 시스템 애니메이션은 테스트에서 0으로 고정합니다. 갤러리의 여러 매듭과 아바타는 가상 데이터입니다.

## 시그니처 화면 키트

표시 문구는 `SignaturePresentation.kt`를 사용합니다. `TraitId.presentation`의 `name`, `publicPhrase`, `icon`, `category`와 `TraitLevel.label`을 조합합니다. 관심사·인사·신고 이유·프로필 종류·데이터 출처·아바타는 각 enum의 `.label`을 사용합니다. 종료 사유는 `reason.presentation(peerNickname)`의 `title`, `body`, `label`, `tag`를 사용합니다. `BLOCKED`도 중립적인 종료 문구입니다. 저장소에서 받은 최종 종료 사유를 그대로 전달하며 상대의 차단 여부를 추측하지 않습니다.

| 부품 | 사용 방법 |
|---|---|
| `TraitBadge` | `trait`, `level`; `TraitBadgeSize.CARD` 또는 `DETAIL`. 단계마다 글자와 고유 도형을 함께 표시해요 |
| `SignatureHero` | `PublicSignature` 또는 `seed, title, source`; 큰 매듭·칭호·출처가 함께 있어요 |
| `SignatureMini` | 공개 시그니처의 작은 매듭·칭호·출처예요 |
| `ChemistryCard` | `chemistry, first, second`에 두 `PublicSignature`를 전달해요. 시드를 직접 전달할 때는 `firstSource, secondSource`도 필수예요. 문장이 없으면 레이아웃 자체를 만들지 않아요 |
| `CandidateCard` | `candidate, onClick`; 대표 특성·잘 맞는 점·관심사·행동 순서예요. `enabled`로 재요청 대기 등을 반영해요 |
| `PeerHeader` | `PublicProfile`과 `PeerHeaderSize.SMALL/LARGE`; 가상 데이터 표시를 항상 포함해요 |
| `ResponseCountdown` | `remainingMillis: Long?`, 선택적으로 `totalMillis`; 자체 시계를 실행하지 않아요 |
| `ConfirmSheet` | `title, body, confirmLabel, onConfirm, onDismiss`; 직접 그린 모달이에요. `busy` 동안 중복 확인·닫기를 막아요 |
| `GelTextField` | 값·변경 콜백·라벨, 오류, 선택적 `maxLength`; 인사는 `singleLine = false`를 사용해요 |
| `InterestPicker` | 선택 집합과 서버 `ParticipationPolicy.maximumInterests`; 한도에서도 선택 해제는 가능해요 |
| `AvatarPicker` | 선택한 `AvatarId`와 `onSelect`; 라디오 역할과 선택 상태를 전달해요 |
| `AppHeader` | 로고·그라데이션 제목·설명, 선택적 프로필과 이동 콜백이에요 |
| 공통 조각 | `GradientText`, `SectionTitle`, `ProgressDots(current, total)`, `InfoRow`, `SettingsRow` |

`ProgressDots.current`는 0부터 시작합니다. `ResponseCountdown`의 0은 만료 확정이 아니어서 `응답 결과를 확인하고 있어요`로 표시합니다. 기준점이 없으면 `null`을 넣어 `응답 시간을 확인하고 있어요`를 표시합니다. 값은 `ServerTimeProvider`의 단조 시각과 `ServerTimeAnchor.remainingMillisUntil()`로 화면 밖에서 계산합니다. 기기 벽시계로 만료를 추정하지 않습니다.

`ConfirmSheet`는 화면의 확인 상태가 있을 때만 호출합니다. `allowExitWhenRestricted = true`는 현재 연결에서 나가는 확인에만 사용합니다. 삭제·차단·계정 정리에는 지정하지 않습니다. 시트 닫기는 상태를 바꾸는 명령이 아니므로 주행 중에도 가능합니다. 입력 초안과 오류·명령 진행 여부는 화면 ViewModel이 소유합니다. 닉네임 길이는 서버 정책으로 전달하며, 입력 한도는 유니코드 코드 포인트 수입니다.

## 화면 조립 레시피

앱이 실제 주행 상태로 `ChayeonTheme`를 감싸고, 실제 기어·발견·최상위 이동 콜백으로 `ScreenChrome`을 한 번 조립합니다. feature는 주입된 chrome의 슬롯을 배치합니다. `previewChrome()`은 화면 테스트 전용 무동작 chrome이며 앱 배선에 사용하지 않습니다. 이 함수가 테마 정책을 바꾸지는 않으므로 테스트에서도 `ChayeonTheme`에 같은 정책을 전달합니다.

```kotlin
@Composable
fun ExampleScreen(chrome: ScreenChrome, candidate: Candidate, onOpen: () -> Unit) {
    ScreenScaffold(
        topBar = chrome.topBar,
        bottomBar = { chrome.bottomBar(NavigationItem.MEET) },
        primary = { CandidateCard(candidate, onOpen, Modifier.fillMaxWidth()) },
        secondary = {
            GlassCard(Modifier.fillMaxWidth()) {
                InfoRow(ChayeonIcon.LOCK, "서로 수락해야 연결돼요",
                    body = "수락 전에는 대화가 열리지 않아요")
            }
        },
    )
}
```

1100dp 미만이면 `primary`, `secondary` 순서로 세로 스크롤합니다. 좁은 화면의 슬롯에는 `fillMaxHeight`나 고정 화면 높이를 넣지 않습니다. 넓은 화면에서 내용이 길어지는 상세 패널은 화면이 `verticalScroll`을 제공합니다. 버튼을 포함한 영역을 강제로 줄여 76dp 미만으로 만들지 않습니다.

정책은 `ChayeonTheme` → `LocalDrivingPolicy` → 버튼·입력·선택·탐색으로 전달됩니다. `UNKNOWN`과 `RESTRICTED`는 입력과 탐색을 막고, 입력 중 전환되면 포커스와 키보드를 해제합니다. 의미 있는 이미지에는 설명이 있고 장식 이미지는 제외합니다. `LocalDecorativeMotion`은 정책과 시스템 애니메이터 배율을 함께 확인하며 새 부품은 별도 반복 애니메이션을 만들지 않습니다.

화면 ViewModel은 반드시 현재 정책을 명령 진입점에서 다시 확인합니다. 주행 상태로부터 파생한 `StateFlow`의 초기값은 `UNKNOWN`을 포함하고, 다음 공유 정책을 사용합니다.

```kotlin
SharingStarted.WhileSubscribed(
    stopTimeoutMillis = 5_000,
    replayExpirationMillis = 0,
)
```

공개 시그니처가 사라지거나 동의가 철회되면 이전 공개 카드를 유지하지 않습니다. 이 키트는 데이터 수집·Repository·내비게이션·타이머를 소유하지 않습니다.

## 복사해서 쓰는 Roborazzi 레시피

feature에 Compose 테스트·Robolectric·Roborazzi 의존성과 Android 리소스 테스트 설정이 이미 있어야 합니다. 아래 클래스는 그대로 실행할 수 있으며 `SectionTitle` 자리만 실제 화면으로 바꿉니다. 같은 데이터로 넓은 창과 좁은 창을 모두 기록합니다.

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class FeatureScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, animatorScaleOverride = 0f) {
                val chrome = previewChrome(policy = DrivingPolicy.ALLOWED)
                ScreenScaffold(
                    modifier = Modifier.fillMaxSize().testTag("screen"),
                    topBar = chrome.topBar,
                    bottomBar = { chrome.bottomBar(NavigationItem.MEET) },
                    primary = {
                        GlassCard(Modifier.fillMaxWidth()) {
                            SectionTitle("오늘의 인연", subtitle = "서로의 주행 습관을 알아봐요")
                        }
                    },
                    secondary = {
                        GlassCard(Modifier.fillMaxWidth()) {
                            SectionTitle("잘 맞는 점", subtitle = "공개한 특성만 보여요")
                        }
                    },
                )
            }
        }
        compose.onNodeWithTag("screen")
            .captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun wide() = capture("screen_wide")

    @Test
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun narrow() = capture("screen_narrow")
}
```

새 키트의 갤러리와 개별 기준 이미지는 `kit_gallery_*.png`, `kit_*.png`입니다. `ScreenKitBehaviorTest`는 정책 전환·입력 포커스·관심사 한도·가상 표시·모달 중복 방지·카운트다운 경계를 검증합니다.
