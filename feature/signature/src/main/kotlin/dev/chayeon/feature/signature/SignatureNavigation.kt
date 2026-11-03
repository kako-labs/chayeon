package dev.chayeon.feature.signature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.ExperienceState
import dev.chayeon.core.ui.ChayeonSpacing
import dev.chayeon.core.ui.ChayeonText
import dev.chayeon.core.ui.ChayeonTheme
import dev.chayeon.core.ui.ChayeonType
import dev.chayeon.core.ui.GearStrip
import dev.chayeon.core.ui.GlassCard
import dev.chayeon.core.ui.HeroPanel
import dev.chayeon.core.ui.JellyButton
import dev.chayeon.core.ui.JellyVariant
import dev.chayeon.core.ui.ScreenScaffold
import dev.chayeon.core.ui.StateCard

object SignatureDestination {
    const val ROUTE = "signature/entry"
}

fun NavGraphBuilder.signatureGraph(
    deps: SignatureDeps,
    onOpenMeet: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    composable(SignatureDestination.ROUTE) {
        val model: SignatureViewModel = viewModel { SignatureViewModel(deps) }
        val state by model.uiState.collectAsStateWithLifecycle()
        SignaturePlaceholder(state, onOpenMeet, onOpenHistory)
    }
}

@Composable
internal fun SignaturePlaceholder(
    state: SignatureUiState,
    onOpenMeet: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    ChayeonTheme(policy = state.driving.policy) {
        ScreenScaffold(
            topBar = { GearStrip(state.driving.gear, state.driving.policy, syntheticLocation = true) },
            primary = {
                HeroPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        ChayeonText("차연", style = ChayeonType.Hero)
                        ChayeonText("내 프로필", style = ChayeonType.Title)
                        ChayeonText("길 위에서 시작되는 인연")
                        ChayeonText("화면을 준비하고 있어요.", style = ChayeonType.Secondary)
                    }
                }
            },
            secondary = {
                if (state.driving.policy == DrivingPolicy.ALLOWED) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    JellyButton("오늘의 인연", onOpenMeet, Modifier.fillMaxWidth(), variant = JellyVariant.SECONDARY)
                    JellyButton("스친 인연", onOpenHistory, Modifier.fillMaxWidth(), variant = JellyVariant.SECONDARY)
                        }
                    }
                } else {
                    StateCard(
                        if (state.driving.policy == DrivingPolicy.RESTRICTED) {
                            ExperienceState.DRIVING_RESTRICTED
                        } else {
                            ExperienceState.DRIVING_UNKNOWN
                        },
                        Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}
