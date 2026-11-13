package dev.chayeon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.Gear
import dev.chayeon.core.ui.ChromeBackAction
import dev.chayeon.core.ui.BackButton
import dev.chayeon.core.ui.BrandMark
import dev.chayeon.core.ui.GearStrip

@Composable
internal fun AppTopChrome(gear: Gear, policy: DrivingPolicy, rootTab: Boolean, syntheticLocation: Boolean,
    modifier: Modifier = Modifier, resting: Boolean = false, onDemo: (() -> Unit)? = null, backAction: ChromeBackAction? = null,
    center: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (backAction != null) BackButton(backAction.onClick, enabled = backAction.enabled,
            allowWhenRestricted = backAction.allowWhenRestricted, inChrome = true)
        else if (rootTab) BrandMark()
        if (center == null) {
            GearStrip(gear, policy, syntheticLocation, Modifier.weight(1f), onDemo, resting)
        } else {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center() }
            GearStrip(gear, policy, syntheticLocation, Modifier.width(IntrinsicSize.Max), onDemo, resting)
        }
    }
}
