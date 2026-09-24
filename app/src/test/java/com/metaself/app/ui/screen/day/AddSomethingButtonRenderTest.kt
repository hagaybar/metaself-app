package com.metaself.app.ui.screen.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The day's floating button, as a screen reader hears it (public issue #3).
 *
 * Compose, so JUnit 4 — `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class AddSomethingButtonRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    /**
     * The way in to adding anything has a name, and the name is on the thing that is pressed: a
     * press aimed at "Add something" reaches the button. Before, the words drawn on it reached no
     * node at all — the render read nothing — so the button announced nothing.
     */
    @Test
    fun `the floating button is named, and pressing that name presses it`() {
        var pressed = false
        val texts = render.texts { AddSomethingButton(onClick = { pressed = true }) }

        assertThat(texts).contains("Add something")
        render.clickDescribed("Add something")
        assertThat(pressed).isTrue()
    }
}
