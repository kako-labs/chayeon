package dev.chayeon

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class NavigationSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appStartsAtFirstRunRoute() {
        compose.setContent { ChayeonApp(AppGraph.foundation()) }
        compose.onNodeWithText("스친 인연").assertIsDisplayed()
    }
}
