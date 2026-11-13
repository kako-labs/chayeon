package dev.chayeon.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class SceneGalleryTest {
    @get:Rule val compose = createComposeRule()
    private val brake = PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.SIGNATURE, 94)

    private fun capture(name: String, scene: Scene = Scene.PARKING, policy: DrivingPolicy = DrivingPolicy.ALLOWED,
        content: @Composable ColumnScope.() -> Unit) {
        compose.setContent {
            ChayeonTheme(policy, 0f, showSourceLabels = false) {
                SceneBackground(scene, Modifier.fillMaxSize().testTag("scene")) {
                    Column(Modifier.fillMaxSize().padding(56.dp), verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
                }
            }
        }
        compose.onNodeWithTag("scene").captureRoboImage("src/test/screenshots/v2_$name.png")
    }

    @Test fun parking() = scene(Scene.PARKING)
    @Test fun road() = scene(Scene.ROAD)
    @Test fun home() = scene(Scene.HOME)
    @Test fun dawn() = scene(Scene.DAWN)
    @Test fun plain() = scene(Scene.PLAIN)
    private fun scene(scene: Scene) = capture("scene_${scene.name.lowercase()}", scene) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BrandMark(); DrivingStatusPill(Gear.PARK, DrivingPolicy.ALLOWED)
        }
        HeroHeadline("근처에 인연이 있어요", "발견을 켠 운전자끼리만 서로 보여요")
        Spacer(Modifier.weight(1f))
        GlassPanel(Modifier.fillMaxWidth()) { InfoLine("장면 위에서도 편안하게 읽을 수 있어요", icon = ChayeonIcon.LOCK) }
        BottomNav(NavigationItem.MEET, true, {}, {}, incomingAfterCount = 2)
    }

    @Test fun drivingScene() = capture("scene_driving", Scene.ROAD, DrivingPolicy.RESTRICTED) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { DrivingStatusPill(Gear.DRIVE, DrivingPolicy.RESTRICTED) }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AvatarDish(MochiAvatar.SLOW, Modifier.size(240.dp))
            ChayeonText("느린오후", style = ChayeonType.SceneTitle)
            ChayeonText("여유롭게 멈추는 밤 드라이버", color = ChayeonColors.MutedText)
            ScoreChip(brake, large = true)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            OutlineButton("음소거", {}, Modifier.weight(1f), access = ButtonAccess.RESTRICTED_CALL, driving = true)
            PrimaryButton("통화 끊기", {}, Modifier.weight(1f), access = ButtonAccess.SAFE_EXIT, driving = true)
        }
    }

    @Test fun mascots() = capture("mascots") {
        MascotPose.entries.chunked(3).forEach { poses ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                poses.forEach { pose -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(pose, Modifier.size(430.dp)); ChayeonText(pose.description, style = ChayeonType.Label)
                } }
            }
        }
    }

    @Test fun callAvatars() = capture("call_avatars") {
        MochiAvatar.entries.chunked(3).forEach { avatars ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                avatars.forEach { avatar -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row { CallAvatar(avatar, mirrored = false, modifier = Modifier.size(240.dp)); CallAvatar(avatar, true, Modifier.size(240.dp)) }
                    AvatarDish(avatar, Modifier.size(160.dp)); ChayeonText(avatar.description, style = ChayeonType.Label)
                } }
            }
        }
    }

    @Test fun callHero() = capture("call_hero") {
        HeroHeadline("느린오후님과 이야기하는 중", "근처에 있는 동안 목소리로 이어져요", style = ChayeonType.CallTitle)
        CallHero(CallParticipant(MochiAvatar.DAWN, "새벽드라이브"), CallParticipant(MochiAvatar.SLOW, "느린오후", "여유롭게 멈추는 밤 드라이버"))
        GlassPanel(Modifier.fillMaxWidth()) { TopicLine("둘 다 깜빡이를 잊지 않아요") }
    }

    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun callHeroNarrow() = capture("call_hero_narrow") {
        HeroHeadline("통화 중", "목소리로 이어져요", style = ChayeonType.CallTitle)
        CallHero(CallParticipant(MochiAvatar.DAWN, "새벽드라이브"), CallParticipant(MochiAvatar.STAR, "별보는밤", "밤길을 좋아하는 드라이버"))
        PrimaryButton("통화 끊기", {}, Modifier.fillMaxWidth(), access = ButtonAccess.SAFE_EXIT)
    }

    @Test fun scrimVariants() = capture("scrim_variants", Scene.PLAIN) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SceneBackground(Scene.PARKING, Modifier.weight(1f).fillMaxHeight(), scrim = SceneScrim.RIGHT) {
                Mascot(MascotPose.THREAD, Modifier.align(Alignment.BottomStart).size(320.dp))
                ChayeonText("오른쪽 본문", Modifier.align(Alignment.CenterEnd).padding(32.dp), ChayeonType.SceneName)
            }
            SceneBackground(Scene.PARKING, Modifier.weight(1f).fillMaxHeight(), scrim = SceneScrim.CALL) {
                CallHero(CallParticipant(MochiAvatar.DAWN, "새벽드라이브"), CallParticipant(MochiAvatar.SLOW, "느린오후"), Modifier.align(Alignment.Center))
            }
        }
    }

    @Test fun disabledControls() = capture("disabled_controls", Scene.PLAIN) {
        PrimaryButton("요청을 보내고 있어요", {}, Modifier.fillMaxWidth(), enabled = false)
        OutlineButton("다음 사람 보기", {}, Modifier.fillMaxWidth(), enabled = false)
        DiscoveryChip(true, {}, enabled = false)
        PrimaryButton("답하기", {}, compact = true)
        OutlineButton("애프터 신청", {}, compact = true)
    }

    @Test fun components() = capture("components") {
        HeroHeadline("근처에 인연이 있어요", "발견을 켠 운전자끼리만 서로 보여요")
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatTile(ChayeonIcon.PEOPLE, "지금 근처", "5명", Modifier.weight(1f))
            StatTile(ChayeonIcon.THREAD, "내 칭호", "여유로운 드라이버", Modifier.weight(2f))
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ChayeonText("별보는밤", style = ChayeonType.CardTitle)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { ScoreChip(brake); ScoreChip(brake, large = true) }
                TopicLine("둘 다 깜빡이를 챙겨요")
                PrimaryButton("통화 요청하기", {}, Modifier.fillMaxWidth(), icon = ChayeonIcon.PHONE)
                OutlineButton("다음 사람 보기", {}, Modifier.fillMaxWidth())
                InfoLine("수락하면 바로 통화가 연결돼요", icon = ChayeonIcon.LOCK)
            }
        }
        DiscoveryChip(true, {}, Modifier.width(360.dp))
    }

    @Test fun bonds() = capture("list_rows", Scene.DAWN) {
        HeroHeadline("인연", "길 위에서 통화한 사람들이 모여요", style = ChayeonType.SceneTitle)
        BondStatus.entries.forEachIndexed { index, status ->
            ListRow(MochiAvatar.entries[index], listOf("별보는밤", "느린오후", "재즈한잔")[index],
                "밤길을 좋아하는 드라이버 · 어제", status = status, trailingAction = {
                    if (status == BondStatus.INCOMING) PrimaryButton("답하기", {}, compact = true) else OutlineButton("보기", {}, compact = true)
                })
        }
        ListRow(MochiAvatar.FOREST, "숲길산책", "캠핑을 좋아하는 드라이버", trailingAction = { OutlineButton("애프터 신청", {}, compact = true) })
    }

    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun narrowComponents() = capture("components_narrow") {
        HeroHeadline("새로운 인연", "주차한 뒤 천천히 알아가요", style = ChayeonType.SceneTitle)
        StatTile(ChayeonIcon.PEOPLE, "지금 근처", "5명", Modifier.fillMaxWidth())
        GlassPanel(Modifier.fillMaxWidth()) {
            ScoreChip(brake); TopicLine("둘 다 깜빡이를 챙겨요")
            InfoLine("서로 수락하면 연결돼요")
        }
        DiscoveryChip(false, {})
        PrimaryButton("통화 요청하기", {}, Modifier.fillMaxWidth(), ChayeonIcon.PHONE)
        OutlineButton("다음 사람 보기", {}, Modifier.fillMaxWidth())
        BottomNav(NavigationItem.PROFILE, false, {}, {})
    }
}
