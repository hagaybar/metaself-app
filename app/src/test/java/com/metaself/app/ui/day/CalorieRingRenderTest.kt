package com.metaself.app.ui.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The ring, on its own.
 *
 * **Why this file exists, written now rather than when the ring was built.** Until this commit
 * `CalorieRing` had no test of any kind. A grep across `app/src` returned exactly two files: the
 * composable itself and `DayScreen.kt`. Everything that had ever exercised it did so incidentally,
 * through the whole-day render asserting on the day's text — which worked only for as long as the
 * day screen went on drawing the ring.
 *
 * It is about to stop. D49 replaces this circle with a 2 dp rule, so the day screen drops the
 * `CalorieRing` call and the file sits unreferenced by any screen while the new page is confirmed on
 * the phone. The circle is **200 dp** — `RING_SIZE`, read from the file below rather than
 * remembered — which makes the rule a hundredth of its height, not a fortieth. The moment that happens, the three whole-day assertions that currently reach the ring's
 * text reach nothing, and **the over-target colour fixed one step ago — over target is ink, not red,
 * because it is a fact about the day and not a validation failure — becomes pinned by nothing at
 * all.** A file nothing renders and nothing tests rots quietly and is discovered to have rotted only
 * when someone tries to use it again.
 *
 * **What this pins, honestly.** `ComposeRender` reads the semantics tree: text, editable text and
 * content descriptions. It cannot read a colour, a size or a weight. So this test asserts that the
 * headline and the caption reach the screen, over target and under it, and **it does not assert the
 * colour at all — the ink-versus-red choice in `CalorieRing.kt` remains unpinned by any test in this
 * repository.** Saying that plainly is worth more than an assertion that looks like it covers the
 * colour and does not. What this does catch is the composable failing to draw, failing to compose,
 * or quietly losing one of its two lines of text — which is every way it can break that a test here
 * is able to see.
 *
 * Compose, so JUnit 4 — `org.junit.Test`, never `org.junit.jupiter.api.Test`. The two annotations
 * look identical at the call site and the wrong one produces a test that silently never runs.
 */
@RunWith(RobolectricTestRunner::class)
class CalorieRingRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    /**
     * The ordinary day: part of the target eaten, something left.
     *
     * The strings are literals rather than calls into `DayTotalsWording`, on purpose. The wording is
     * being split in two by D49 and `ofTarget` is losing its second "kcal"; a render test that asked
     * the wording object what to expect would assert that the ring draws whatever it was handed,
     * which is not a fact about anything.
     */
    @Test
    fun `an ordinary day draws the figure and what it is a fraction of`() {
        val texts = render.texts {
            CalorieRing(
                fractionEaten = 0.4f,
                headline = "1,240 kcal left",
                caption = "of 2,090",
                overTarget = false,
            )
        }

        assertThat(texts).contains("1,240 kcal left")
        assertThat(texts).contains("of 2,090")
    }

    /**
     * Over target, which is the case the previous step changed and nothing was watching.
     *
     * The headline takes a different colour branch here — ink rather than the inherited content
     * colour — and that branch is the one place this composable behaves differently from itself. The
     * colour is invisible to this helper, so what is pinned is that taking the branch still produces
     * both lines of text: a composable that throws, or one whose over-target arm dropped a line,
     * fails here. The colour itself is checked by eye on the phone and by nothing else.
     */
    @Test
    fun `a day past its target still draws both its lines`() {
        val texts = render.texts {
            CalorieRing(
                fractionEaten = 1f,
                headline = "210 kcal over",
                caption = "of 2,090",
                overTarget = true,
            )
        }

        assertThat(texts).contains("210 kcal over")
        assertThat(texts).contains("of 2,090")
    }

    /**
     * Nothing eaten yet: the filled arc is skipped entirely and only the track is drawn.
     *
     * A zero fraction is its own arm of the drawing code — `if (fractionEaten > 0f)` — and a day
     * before its first meal is the commonest state the screen is opened in.
     */
    @Test
    fun `a day with nothing on it yet still says what is left`() {
        val texts = render.texts {
            CalorieRing(
                fractionEaten = 0f,
                headline = "2,090 kcal left",
                caption = "of 2,090",
                overTarget = false,
            )
        }

        assertThat(texts).contains("2,090 kcal left")
    }
}
