package com.metaself.app.ui.screen.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * D113: the Trainer from Today — a word in the bar beside the menu, and an item in the menu after Weight. D130:
 * Movement is no longer in the menu; it is Your movement, behind the Trainer, and the step bar still opens it.
 * The menu opens in a popup window, which a render here cannot read, so its items are drawn on their own.
 */
@RunWith(RobolectricTestRunner::class)
class TodayActionsRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the bar says Trainer, beside the menu, and it opens the Trainer`() {
        var opened = 0
        val texts = render.texts {
            Row { TodayActions(onOpenTrainer = { opened++ }, onOpenWeight = {}, onOpenProfile = {}, onOpenManager = {}, onOpenSettings = {}) }
        }

        assertThat(texts).containsAtLeast("Trainer", "More").inOrder()
        assertThat(render.roleOf("Trainer")).isEqualTo(Role.Button)
        render.click("Trainer")
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun `the menu has no Movement, lists Trainer after Weight, and it opens the Trainer`() {
        val opened = mutableListOf<String>()
        val texts = render.texts {
            Column {
                TodayMenuItems(
                    onChosen = { opened += "closed" },
                    onOpenTrainer = { opened += "trainer" },
                    onOpenWeight = {},
                    onOpenProfile = {},
                    onOpenManager = {},
                    onOpenSettings = {},
                )
            }
        }

        assertThat(texts).containsAtLeast("Weight", "Trainer", "Your numbers", "Foods & meals", "Settings").inOrder()
        assertThat(texts).doesNotContain("Movement")
        render.click("Trainer")
        assertThat(opened).containsExactly("closed", "trainer").inOrder()
    }
}
