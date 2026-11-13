# 차연 디자인 시스템

`dev.chayeon.core.ui`의 명명된 컴포넌트를 사용합니다. 화면에서 Material 기본 표면을 섞거나 색·글자 토큰을 다시 만들지 않습니다. UI v2 토큰은 승인된 `design-v2/render` 목업을 따르며 기존 Red 시안보다 우선해요. 세부 기준은 `docs/design/README.md`에 있어요.

## 시작

- `ChayeonTheme(policy, animatorScaleOverride)`가 주행 정책과 장식 모션을 제공합니다. 정책 기본값은 `UNKNOWN`입니다. `animatorScaleOverride`는 갤러리·테스트용이며 제품에서는 시스템 배율을 그대로 사용합니다.
- `ScreenScaffold(topBar, bottomBar, primary, secondary)`는 1100dp 이상에서 두 열, 그 미만에서 스크롤 가능한 한 열을 만듭니다. 기본 화면은 1682×1210dp입니다.
- `ChayeonColors`, `ChayeonType`, `ChayeonSpacing`, `ChayeonRadii`, `ChayeonElevation`, `ChayeonMotion`을 사용합니다.
- `JellyButton`·토글·라디오·체크박스·하단 메뉴는 `ALLOWED`일 때만 동작합니다. 나가기에는 `JellyButton(allowWhenRestricted = true)`를 명시합니다. 기능의 ViewModel과 저장소도 별도로 정책을 검사해야 합니다.

## 컴포넌트

| 묶음 | API |
|---|---|
| 표면 | `ChayeonBackground`, `GlassCard`, `HeroPanel` |
| 입력 | `JellyButton`, `GelToggle`, `GelRadioRow`, `GelCheckbox` |
| 표시 | `ChayeonText`, `IconOrb`, `StatusChip`, `InterestTag`, `StateCard` |
| 탐색 | `BackButton`, `StepIndicator`, `GearStrip`, `TopBanner`, `BottomNav`, `ScreenScaffold` |
| 에셋 | `MochiAvatar`, `AvatarDish`, `ThreadStage`, `ChayeonAssets` |
| 시그니처 | `SignatureKnot(seed, interlockedWith)`, `ThreadView(stage)` |

`ExperienceState` 17종은 `StateCard`에 대응합니다. `onAction`이 없으면 행동 버튼을 생략합니다. 상대 차단 여부나 비공개 상태의 이유를 추측해 표시하지 않습니다. 의미 있는 에셋에는 설명이 있고, 중복 장식에는 `decorative = true`를 씁니다.

`SignatureKnot`은 시드를 섞어 고리의 위상·기울기·폭을 정하고, 3차원 곡선을 투영해 깊이 순서로 그립니다. 연속 튜브 면의 깊이와 법선을 보간해 진홍색 음영·가변 반사광·교차 그림자를 계산해요. 상대의 가닥은 진주빛 흰색이며 같은 깊이 버퍼에 참여해요. 시드와 크기에 따른 래스터·표시 경로를 캐시하고 reveal 프레임에서는 경로와 마스크를 재사용해요. 래스터의 긴 변은 960px 이하예요.

## 글꼴과 이미지

Asta Sans 원본은 [Google Fonts의 ofl/astasans](https://github.com/google/fonts/tree/main/ofl/astasans)에서 가져왔습니다. `src/main/res/font/asta_sans.ttf`와 배포 필수 원문 `src/main/res/raw/asta_sans_ofl.txt`를 함께 보관합니다. 원본 이미지 14개는 `drawable-nodpi`에 동일 내용으로 복사했고 파일명의 하이픈만 밑줄로 바꿨습니다.

## 검증

```sh
./gradlew :core:ui:recordRoborazziDebug
./gradlew :core:ui:verifyRoborazziDebug :core:ui:lintDebug
```

스크린샷 기준은 `src/test/screenshots`입니다. 갤러리는 모든 구성 요소와 18 상태, 넓은 창·좁은 창을 포함합니다. 개별 구성 요소는 같은 `w1682dp-h1210dp-land-mdpi` 환경에서 독립 캡처합니다. 시스템 애니메이션은 테스트에서 0으로 고정합니다. 갤러리의 여러 매듭과 아바타는 가상 데이터입니다.

## 시그니처 화면 키트

표시 문구는 `SignaturePresentation.kt`를 사용합니다. `TraitId.presentation`의 `name`, `publicPhrase`, `icon`, `category`와 `TraitLevel.label`을 조합합니다. 관심사·신고 이유·프로필 종류·데이터 출처·아바타는 각 enum의 `.label`을 사용합니다. 종료 사유는 `reason.presentation(peerNickname)`의 `title`, `body`, `label`, `tag`를 사용합니다. `BLOCKED`도 중립적인 종료 문구입니다. 저장소에서 받은 최종 종료 사유를 그대로 전달하며 상대의 차단 여부를 추측하지 않습니다.

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
| `GelTextField` | 값·변경 콜백·라벨, 오류, 선택적 `maxLength` |
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
                    body = "수락 전에는 아이디를 공유하지 않아요")
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

## 단어 줄바꿈과 스크롤 가장자리

`ChayeonText`는 API 34에서도 한글 어절을 유지해요. 보이지 않는 연결 문자는 렌더에만 들어가고 Compose 텍스트 탐색과 스크린 리더에는 원문을 전달해요. 칸보다 긴 어절은 비상 줄바꿈하며 `maxLines`를 넘으면 말줄임해요. `onTextLayout`은 표시 레이어의 결과이므로 원문을 저장하거나 전송하는 용도로 쓰지 않아요.

좁은 `ScreenScaffold`의 스크롤 가장자리는 자동으로 페이드돼요. 넓은 화면에서 feature가 소유하는 스크롤에는 같은 `Modifier.scrollEdgeFade`를 적용해요. 스크롤할 내용이 남은 가장자리만 흐려지며, 내용이 칸 안에 들어가면 페이드하지 않아요.

```kotlin
val scroll = rememberScrollState()
Column(Modifier.scrollEdgeFade(scroll).verticalScroll(scroll)) { /* Screen content */ }

val list = rememberLazyListState()
LazyColumn(state = list, modifier = Modifier.scrollEdgeFade(list)) { /* Screen items */ }
```

페이드는 `verticalScroll` 앞에 둬요. 콘텐츠와 배경을 분리한 뷰포트에 적용하면 기존 글래스 배경과 자연스럽게 이어져요.

## UI v2 공용 기반

새 화면은 승인된 1682×1210 목업을 기준으로 아래 부품을 사용해요. 기존 화면의 본문 조립은 다음 화면 작업에서 바꿔요. 앱 배경·하단 메뉴·상태 배지·아바타와 공개 점수 칩은 공용 구현을 통해 먼저 바뀌어요.

| API | 역할 |
|---|---|
| `SceneBackground(scene, modifier, dimmed, scrim, content)` | PARKING·ROAD·HOME·DAWN·PLAIN, crop 이미지와 정적 스크림 |
| `ScreenScaffold(..., scene, sceneScrim)` | 장면을 본문 뒤에 배치해요. 기본값은 앱의 `LocalScene`이며 제한·불명에서는 ROAD를 강제해요 |
| `Mascot(pose, modifier)` | WAVE·THREAD·CALL·SLEEP·BYE·KNOT, 한국어 설명과 부드러운 붉은 그림자 |
| `AvatarDish(avatar, modifier, decorative)` | 기존 API로 새 idle 에셋을 표시해요 |
| `CallAvatar(avatar, mirrored, modifier)` | 헤드셋·실 끝이 있는 call 에셋, 오른쪽 상대는 좌우 반전 |
| `CallHero(me, partner, modifier)` | `CallParticipant(avatar, name, title)` 두 명과 9/5/1.6dp 붉은 실, 닉네임·상대 칭호. 항상 정적이에요 |
| `HeroHeadline(title, supportingLine, modifier, style)` | 큰 제목과 한 문장. 기본 84sp, 좁은 창·통화에는 SceneTitle/CallTitle을 선택해요 |
| `StatTile(icon, label, value, modifier)` | 아이콘·작은 라벨·큰 값 |
| `ScoreChip(trait: PublicTrait, modifier, large)` | 공개 가능한 점수만 이름·굵은 숫자로 표시해요. 취향·60 미만은 숨겨요 |
| `PublishedScoreChips(signature, modifier, limit, large)` | 내림차순 최대 3개, 제한·불명에서는 최대 2개. 주행에서는 `large = true` |
| `PrimaryButton(text, onClick, modifier, icon, enabled, access, driving, compact)` | 붉은 그라데이션과 주차 중의 은은한 광택. 큰 주행 컨트롤은 `driving = true`, 인연 행의 76dp 액션은 `compact = true` |
| `OutlineButton(...)` | PrimaryButton과 같은 계약, 테두리 보조 버튼 |
| `InfoLine(text, modifier, icon)` | 작은 안내 문장과 선택적 아이콘 |
| `GlassPanel(modifier, contentPadding, content)` | 40dp 곡률의 반투명 레이어. 플랫폼 blur가 없어요 |
| `TopicLine(text, modifier)` | 체크 표시와 실제 공통 주제 문장 |
| `ListRow(avatar, name, subLine, modifier, status, trailingAction)` | 인연 행과 trailing 슬롯. 액션은 버튼 정책을 적용한 부품으로 넣어요 |
| `BondStatusBadge(status, modifier)` | INCOMING·CONNECTED·WAITING 상태의 한국어 배지 |
| `BrandMark`, `DrivingStatusPill(gear, policy)` | 하트 공·차연, 기어와 주행 상태 |
| `BottomNav(..., incomingAfterCount)` | 같은 너비의 3탭, 선택 pill, 주차 중에만 받은 애프터 수. 기존 발견 인자는 호환용이며 오브를 만들지 않아요 |
| `DiscoveryChip(checked, onCheckedChange, modifier, resting, enabled)` | 다음 홈 히어로의 발견 스위치 |

`ButtonAccess.PARKED_ONLY`가 기본이에요. `RESTRICTED_CALL`은 현재 통화의 음소거와 정책을 통과한 RING/AUTO 수신 받기·거절에만, `SAFE_EXIT`는 안전한 통화 종료에만 써요. UNKNOWN에서 SAFE_EXIT 외에는 잠겨요. 화면 ViewModel의 명령 검사도 유지해야 해요. 버튼 최소 크기는 주차 76dp, 제한·불명 96dp이고 `driving = true`는 목업의 180dp예요.

`CallHero`에는 시간·점수·관심사를 넣지 않아요. 주행 통화 화면은 목업처럼 상대 `AvatarDish`와 칭호, `PublishedScoreChips(..., limit = 2, large = true)`를 중심으로 구성해요. 점수의 출처는 반드시 서버의 공개 투영이어야 해요. `StatTile`·인연 목록·발견 스위치·설정은 주차 화면에서만 사용해요.

`ScreenChrome`은 `discoveryOn`, `discoveryResting`, `discoveryBusy`, `onDiscoveryToggle`을 노출해요. 다음 홈에서 `DiscoveryChip(chrome.discoveryOn, { chrome.onDiscoveryToggle() }, resting = chrome.discoveryResting, enabled = !chrome.discoveryBusy)`로 사용해요. 기존 홈의 ViewModel 스위치도 유지되어 있어요. 중앙 발견 오브는 제거했어요.

장면 선택은 `appScene`이 소유해요. 홈·만남·요청·주차 통화는 PARKING, 보호 중 홈·집 보호 설정은 HOME, 참여 준비·인연·애프터는 DAWN, 종료·주행은 ROAD, 프로필·근거·설정은 PLAIN이에요. 주행 정책이 제한·불명으로 바뀌면 스캐폴드가 ROAD와 어두운 스크림을 우선해요. `SceneScrim.RIGHT`는 오른쪽 본문, `CALL`은 주차 통화의 중앙 연출용이에요. 직접 테스트하는 feature에는 필요한 `scene`을 명시해요.

`ChayeonType.Headline/SceneTitle/CallTitle/CardTitle/TileValue/SceneButton/DrivingButton/SceneName/SceneBody`가 새 위계를 제공해요. 기존 타입 크기는 기존 화면의 줄바꿈을 유지하기 위해 그대로 두었어요. `ChayeonDimensions.Scene*`는 새 크기·간격 토큰이에요. 앱 루트 홈의 이전 `AppHeader` 브랜드는 `LocalBrandInChrome`으로 중복을 피하며, 상세 화면의 기존 `BackButton`은 새 테두리 표면을 써요.

WebP는 장면 4장(1536×1024), 마스코트 6장(768×768), idle 아바타 6장 교체와 call 아바타 6장 추가(각 512×512)예요. PNG를 앱 리소스에 넣지 않아요. 제작 기록은 [에셋 출처](../../docs/design/assets-v2.md)에 있어요.

검증 갤러리는 `v2_*.png`, 앱 크롬은 `v2-chrome-*.png`예요. 고정된 주행 정책, 1682×1210/840×1210dp, 저장 배율 0.5로 캡처해요.

기존 화면의 본문 가시성을 유지하려고 `ScreenScaffold.contentPadding` 기본값은 가로 32dp·세로 8dp예요. 커진 상태 pill이 차지하는 높이만큼 기존 바깥 세로 여백을 줄여 본문 가시성을 유지해요. v2 화면으로 조립할 때 `PaddingValues(start = ChayeonDimensions.SceneScreenHorizontal, end = ChayeonDimensions.SceneScreenHorizontal, top = ChayeonDimensions.SceneScreenTop, bottom = ChayeonDimensions.SceneScreenBottom)`를 전달하면 목업의 56/44/40dp 여백을 적용해요.

앱은 `hasDetailChrome`에 해당하는 상세 경로에서 상단 뒤로 버튼을 제공해요. `LocalChromeBackRegistry`가 있으면 기존 화면 본문의 `BackButton`이 자신의 콜백·활성 상태를 상단 크롬에 전달하고 중복 버튼은 만들지 않아요. 저장·게시 중 잠금과 화면별 돌아가기 동작을 그대로 유지해요. 앱 크롬만 `inChrome = true`를 전달해요. 직접 feature를 캡처하는 테스트에서는 기존 뒤로 버튼을 그대로 볼 수 있어요.
