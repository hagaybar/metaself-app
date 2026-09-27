package com.metaself.app.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings opened from "Add a key in settings" opens where the key goes (public issue #11).
 *
 * Since D79 the way there is a route: "opened at the key" is the AI estimates page, whose first
 * section is the key (the route itself is pinned in `MetaSelfNavHostRenderTest`). Asked of Compose's
 * own finders, whose `assertIsDisplayed` clips a node by the scrolling column it sits in;
 * [ComposeSession] alone reads every node whether scrolled into view or not. The guard proves the
 * key is not on the index Settings opens at from the menu, so opening "at the key" is a different
 * place and not the same page by accident. Existence is asserted before visibility, for the reason
 * `FoodsAskFirstSessionTest` gives. Pinned to a phone's size so the scrolling does not depend on
 * Robolectric's default.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class SettingsAtKeySessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `opened for the key, the whole key section is on screen`() {
        session.start { KeyPage() }

        compose.onNodeWithText(KEY_TITLE).assertExists().assertIsDisplayed()
        compose.onNodeWithText(SAVE_KEY).assertExists().assertIsDisplayed()
    }

    /** From the menu Settings opens at its index, where there is no key section at all (D79). */
    @Test
    fun `opened from the menu, it opens at the index and the key is on a page of its own`() {
        session.start { SettingsScreen(state = SettingsUiState(), onOpen = {}, onBack = {}) }

        compose.onNodeWithText(KEY_TITLE).assertDoesNotExist()
        compose.onNodeWithText(AI_ESTIMATES).assertExists().assertIsDisplayed()
    }

    /** Where "opened at the key" lands now: the AI estimates page, the key its first section (D79). */
    @Composable
    private fun KeyPage() {
        AiSettingsPage(
            state = SettingsUiState(),
            onSaveKey = {},
            onClearKey = {},
            onSetModel = {},
            onSetCeiling = {},
            onTest = {},
            onDismissFailure = {},
            onBack = {},
        )
    }

    private companion object {
        /** `R.string.settings_key_title`, as the phone draws it. */
        const val KEY_TITLE = "Your OpenAI API key"

        /** `R.string.settings_page_ai`, the index row the key is under. */
        const val AI_ESTIMATES = "AI estimates"

        /** `R.string.settings_key_save`, as the phone draws it. */
        const val SAVE_KEY = "Save the key"
    }
}
