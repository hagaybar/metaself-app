package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.window.WindowWording
import java.time.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The "When you eat" section, now that there are two kinds of window.
 *
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * **Both kinds are on screen at once, and that is a requirement rather than a layout preference.**
 * `ComposeRender.click` presses nodes from the LAST render, and calling `texts { }` again builds a
 * fresh activity and a fresh composition — so a two-click sequence (pick a ratio, then save it)
 * cannot be split across two renders without throwing away the very selection it exists to make.
 * Nothing here may go behind a tab or a mode toggle.
 *
 * Two labelling rules follow from the same helper, because `click` matches on `startsWith` and
 * takes the FIRST matching node in the tree:
 *
 * - the ratio's save button must not extend the hours button's label, so it is "Save this ratio"
 *   and not "Keep tab on this ratio";
 * - no line above the chips may BEGIN with the figure, or `click("16/8")` would find a sentence and
 *   fail with "has nothing to click". The existing hours summary already has the right shape —
 *   "Your window is 06:00 to 20:00." — and the ratio summary follows it.
 */
@RunWith(RobolectricTestRunner::class)
class WindowSettingsRenderTest {

    private val render = ComposeRender()

    private val sixteenEight =
        WindowRule.Measured(MeasuredWindow(fastingHours = 16), fromEpochDay = TEST_EPOCH_DAY)

    private val savedRatios = mutableListOf<Int>()
    private val savedHours = mutableListOf<Pair<Int, Int>>()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the two kinds of window are both offered`() {
        val texts = draw()

        assertThat(texts).contains("When you eat")
        assertThat(texts).contains("Set the hours")
        assertThat(texts).contains("Set a ratio")
    }

    /**
     * The decision this test exists for: the slash never stands alone. Both are their own pieces of
     * text, because that is how a reader of the screen — and this helper — sees them.
     */
    @Test
    fun `the ratio is stated in words, not just as a slash`() {
        val texts = draw(rule = sixteenEight)

        assertThat(texts).contains("16/8")
        assertThat(texts).contains("16 hours fasting, 8 hours eating")
        assertThat(texts).doesNotContain("18 hours fasting, 6 hours eating")
    }

    /**
     * Once there is a tally, the summary line is replaced by it — so the figure and the words have
     * to be somewhere that survives the substitution, not inside the summary sentence.
     *
     * The tally a RATIO gets is counted in stretches, because that is the unit the measured window
     * judges in now (design §3.1), and since the ratio was set (D32). "Kept 10 of 14 stretches since
     * 3 Sep", not "on 14 days".
     */
    @Test
    fun `the words stay when the tally replaces the summary`() {
        val texts = draw(rule = sixteenEight, kept = 10, judged = 14)

        assertThat(texts).contains(
            WindowWording.keptStretches(kept = 10, judged = 14, since = LocalDate.ofEpochDay(sixteenEight.fromEpochDay))!!,
        )
        assertThat(texts).contains("16/8")
        assertThat(texts).contains("16 hours fasting, 8 hours eating")
    }

    /** The settings tally for a ratio is stretches, said in as many words. */
    @Test
    fun `the tally under a ratio counts stretches`() {
        val texts = draw(rule = sixteenEight, kept = 10, judged = 14)

        assertThat(texts).contains("Kept 10 of 14 stretches since 3 Sep")
        assertThat(texts).doesNotContain("Window kept on 10 of 14 days")
    }

    /**
     * The fixed hours keep the tally they have always had.
     *
     * "Eat between 06:00 and 20:00" is a statement about a DAY and keeps its day verdict, its day
     * mark and its day tally (design §4). Two kinds of window, two shapes of answer.
     */
    @Test
    fun `the tally under fixed hours still counts days`() {
        val hours = WindowRule.Fixed(EatingWindow(6, 20, 20_699))
        val texts = draw(rule = hours, kept = 10, judged = 14)

        assertThat(texts).contains(WindowWording.kept(kept = 10, judged = 14)!!)
        assertThat(texts).contains("Window kept on 10 of 14 days")
        assertThat(texts).doesNotContain("Kept 10 of your last 14 stretches")
    }

    /**
     * One render, two presses. The save button's lambda must read the chosen ratio when it RUNS:
     * it was composed before the chip was pressed, so a value captured outside the lambda would
     * save the default for ever — a bug that is invisible on screen.
     *
     * The chip pressed is therefore NOT the default one. Pressing 16/8 on a screen drawn with no
     * rule — where the selection already starts at 16 — changes no state and recomposes nothing,
     * so the lifted-value bug this test exists for would hold 16 as well and the assertion would
     * pass against it. 18/6 is a chip the selection has to actually move to.
     */
    @Test
    fun `choosing a ratio saves the fasting hours`() {
        draw()

        render.click("18/6")
        render.click("Save this ratio")

        assertThat(savedRatios).containsExactly(18)
        assertThat(savedHours).isEmpty()
    }

    /** D27's one firm rule, said where the window is set — for the ratio as much as the hours. */
    @Test
    fun `it still says the window applies only from today`() {
        assertThat(draw(rule = sixteenEight)).contains(WindowWording.FROM_TODAY)
    }

    @Test
    fun `and says it for the hours as well`() {
        assertThat(draw(rule = WindowRule.Fixed(EatingWindow(6, 20, 20_699))))
            .contains(WindowWording.FROM_TODAY)
    }

    /** The fixed kind is not weakened by the arrival of the second one. */
    @Test
    fun `the hours picker is untouched when the hours are chosen`() {
        val texts = draw()

        assertThat(texts).contains("From")
        assertThat(texts).contains("Until")
        assertThat(texts).contains("Keep tab on this")

        render.click("Keep tab on this")

        assertThat(savedHours).containsExactly(8 to 20)
        assertThat(savedRatios).isEmpty()
    }

    private fun draw(
        rule: WindowRule? = null,
        kept: Int = 0,
        judged: Int = 0,
    ): List<String> = render.texts {
        SettingsScreen(
            state = SettingsUiState(
                windowRule = rule,
                windowKept = kept,
                windowJudged = judged,
            ),
            onSaveKey = {},
            onClearKey = {},
            onSetModel = {},
            onSetCeiling = {},
            onTest = {},
            onSetReminder = {},
            onSendReminderNow = {},
            onSaveOffAccount = { _, _ -> },
            onClearOffAccount = {},
            onSetWindow = { start, end -> savedHours += start to end },
            onSetRatio = { fastingHours -> savedRatios += fastingHours },
            onClearWindow = {},
            onConnectSteps = {},
            onPickBackupFolder = {},
            onForgetBackupFolder = {},
            onBackUpNow = {},
            onSetDrive = {},
            onDriveNow = {},
            onExport = {},
            onRestore = {},
            onConfirmRestore = {},
            onCancelRestore = {},
            onDismissBackupMessage = {},
            onCopyProblems = {},
            onClearProblems = {},
            onBack = {},
        )
    }
}
