package dev.chayeon.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.chayeon.core.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class ScreenKitBehaviorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unknownAndDrivingBlockInputSelectionAndNavigation() {
        val policy = mutableStateOf(DrivingPolicy.ALLOWED)
        var changes = 0
        compose.setContent {
            ChayeonTheme(policy.value, 0f) {
                Column {
                    GelTextField("느린오후", { changes++ }, "닉네임")
                    AvatarPicker(AvatarId.SLOW, { changes++ })
                    InterestPicker(emptySet(), 5, { changes++ })
                    AppHeader(profile = galleryPeer, onProfileClick = { changes++ })
                }
            }
        }
        compose.onNodeWithContentDescription("닉네임").assertHeightIsAtLeast(76.dp).performClick()
        for (restricted in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            compose.runOnIdle { policy.value = restricted }
            compose.onNodeWithContentDescription("닉네임").assertIsNotEnabled()
            compose.onAllNodes(isFocused()).assertCountEquals(0)
            compose.onNodeWithContentDescription(AvatarId.DAWN.label).assertIsNotEnabled().performClick()
            compose.onNodeWithText(InterestId.JAZZ.label).assertIsNotEnabled().performClick()
            compose.onNodeWithContentDescription("내 프로필 보기").assertIsNotEnabled().performClick()
        }
        assertEquals(0, changes)
    }

    @Test fun interestLimitStillAllowsDeselection() {
        val selected = mutableStateOf(setOf(InterestId.JAZZ))
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { InterestPicker(selected.value, 1, { selected.value = it }) } }
        compose.onNodeWithText("커피").assertIsNotEnabled().performClick()
        compose.onNodeWithText("재즈").assertIsOn().performClick()
        compose.onNodeWithText("커피").assertIsEnabled().performClick()
        assertEquals(setOf(InterestId.COFFEE), selected.value)
    }

    @Test fun textInputHonorsLimitAndPreservesEditableDraft() {
        val value = mutableStateOf("")
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { GelTextField(value.value, { value.value = it }, "닉네임", maxLength = 4) } }
        compose.onNodeWithContentDescription("닉네임").performTextInput("느린오후")
        assertEquals("느린오후", value.value)
        compose.onNodeWithContentDescription("닉네임").performTextInput("긴이름")
        assertEquals("느린오후", value.value)
    }

    @Test fun avatarsExposeSelectionAndDiscoveryOrbExposesSwitchState() {
        val avatar = mutableStateOf(AvatarId.SLOW)
        val discovery = mutableStateOf(false)
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                Column {
                    AvatarPicker(avatar.value, { avatar.value = it })
                    BottomNav(NavigationItem.PROFILE, discovery.value, {}, { discovery.value = !discovery.value })
                }
            }
        }
        compose.onNodeWithContentDescription(AvatarId.SLOW.label).assertIsSelected()
        compose.onNodeWithContentDescription(AvatarId.DAWN.label).performClick().assertIsSelected()
        compose.onNodeWithContentDescription("발견 켜기").assertIsOff().performClick()
        compose.onNodeWithContentDescription("발견 끄기").assertIsOn()
        compose.onNodeWithText("내 프로필").assertExists()
    }

    @Test fun emptyChemistryHasNoSurfaceAndPublicCardsAlwaysLabelTheirSource() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                Column {
                    ChemistryCard(Chemistry(), KnotSeed(1), KnotSeed(2), DataSource.SYNTHETIC, DataSource.SYNTHETIC, Modifier.testTag("empty"))
                    CandidateCard(galleryCandidate, {})
                }
            }
        }
        compose.onNodeWithTag("empty").assertDoesNotExist()
        compose.onNodeWithText("가상 프로필").assertExists()
        compose.onNodeWithText("합성 주행").assertExists()
        compose.onNodeWithText("잘 맞는 점 2가지").assertExists()
    }

    @Test fun confirmationDoesNotPermitDeletionWhileDrivingAndBusyDoesNotRepeat() {
        val policy = mutableStateOf(DrivingPolicy.UNKNOWN)
        val busy = mutableStateOf(false)
        var confirms = 0
        var dismisses = 0
        compose.setContent {
            ChayeonTheme(policy.value, 0f) {
                ConfirmSheet("기록을 지울까요?", "내 화면에서 기록이 사라져요.", "기록 삭제", { confirms++ }, { dismisses++ }, busy = busy.value)
            }
        }
        compose.onNodeWithText("기록 삭제").assertIsNotEnabled().performClick()
        compose.onNodeWithText("돌아가기").performClick()
        compose.runOnIdle { policy.value = DrivingPolicy.ALLOWED; busy.value = true }
        compose.onNodeWithText("처리하고 있어요").assertIsNotEnabled().performClick()
        compose.onNodeWithText("돌아가기").assertIsNotEnabled()
        assertEquals(0, confirms)
        assertEquals(1, dismisses)
    }

    @Test fun countdownNeverInfersExpirationOrOverflows() {
        assertEquals("응답 시간을 확인하고 있어요", countdownLabel(null))
        assertEquals("응답 결과를 확인하고 있어요", countdownLabel(0))
        assertEquals("응답 결과를 확인하고 있어요", countdownLabel(-1))
        assertEquals("0:01 남았어요", countdownLabel(1))
        assertEquals("1:48 남았어요", countdownLabel(108_000))
        assertEquals("23시간 0분 남았어요", countdownLabel(82_800_000))
        assertFalse(countdownLabel(Long.MAX_VALUE).contains("-"))
    }

    @Test fun presentationCoversEveryContractValueAndKeepsEndedCopyNeutral() {
        TraitId.entries.forEach { assertTrue(it.presentation.name.isNotBlank()); assertTrue(it.presentation.publicPhrase.endsWith("요")) }
        assertEquals(7, TraitId.entries.count { it.presentation.category == TraitCategory.STYLE })
        assertEquals(5, TraitId.entries.count { it.presentation.category == TraitCategory.CARE })
        assertEquals(setOf("기록 중", "보여요", "꾸준해요", "시그니처"), TraitLevel.entries.map { it.label }.toSet())
        InterestId.entries.forEach { assertTrue(it.label.isNotBlank()) }
        ReportReason.entries.forEach { assertTrue(it.label.isNotBlank()) }
        ProfileKind.entries.forEach { assertTrue(it.label.isNotBlank()) }
        DataSource.entries.forEach { assertTrue(it.label.isNotBlank()) }
        AvatarId.entries.forEach { assertEquals(it.label, MochiAvatar.from(it).description) }
        ConnectionEndReason.entries.forEach { assertFalse(it.presentation("가상 상대").title.contains("차단")) }
        assertEquals("느린오후 님이 통화를 마쳤어요", ConnectionEndReason.PEER_LEFT.presentation("느린오후").title)
        assertEquals("위치나 통신이 오래 끊겼어요. 멀어졌다고 판단한 것은 아니에요.", ConnectionEndReason.STATUS_UNKNOWN.presentation("상대").body)
    }

    @Test fun navigationHasThreeTabsAndIncomingAfterBadge() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            BottomNav(NavigationItem.RELATIONSHIPS, false, {}, {}, incomingAfter = true)
        } }
        compose.onAllNodes(hasAnyAncestor(isRoot()) and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Tab)).assertCountEquals(3)
        compose.onNodeWithText("홈").assertExists()
        compose.onNodeWithText("인연").assertExists()
        compose.onNodeWithText("내 프로필").assertExists()
        compose.onNodeWithContentDescription("새 애프터 요청", useUnmergedTree = true).assertExists()
    }
}
