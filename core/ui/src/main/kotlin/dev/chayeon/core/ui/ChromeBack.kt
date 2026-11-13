package dev.chayeon.core.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class ChromeBackAction(
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val allowWhenRestricted: Boolean = false,
)

@Stable
class ChromeBackRegistry {
    var action: ChromeBackAction? by mutableStateOf(null)
        private set

    internal fun register(action: ChromeBackAction): () -> Unit {
        this.action = action
        return { if (this.action === action) this.action = null }
    }
}

val LocalChromeBackRegistry = staticCompositionLocalOf<ChromeBackRegistry?> { null }
