package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sentence for an action that threw is drawn under the part of the page it came from, once.
 *
 * A line anywhere but under its part could be off screen, or on another page (D79), so where it
 * lands is the point; asserted by order in the drawn tree, which is what a render test here can
 * say about position. JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRefusalRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    /**
     * BACKUP's sentence is drawn at the end of the Backups page, after the last of its controls, as
     * it was drawn at the end of the backup part of the single page. It used to be bounded below by
     * the reminder, which is now on the Eating page (D79): that bound is kept as "not drawn there".
     */
    @Test
    fun `a restore that threw is said on the Backups page, after its controls, and not on Eating`() {
        val failed = SettingsRefusal(SettingsPart.BACKUP, ActionRefused.MAYBE_PARTIAL)
        val texts = backups(failed)
        val sentence = texts.indexOf(PARTIAL)

        assertThat(texts.count { it == PARTIAL }).isEqualTo(1)
        assertThat(sentence).isGreaterThan(texts.indexOf("Keeping a copy"))
        assertThat(sentence).isGreaterThan(texts.indexOf("Restore from a file"))
        assertThat(texts).contains("All right")

        assertThat(eating(failed)).doesNotContain(PARTIAL)
    }

    @Test
    fun `a key that threw is said under the key, before the model`() {
        val texts = ai(SettingsRefusal(SettingsPart.KEY, ActionRefused.NOTHING_CHANGED))
        val sentence = texts.indexOf(NOTHING)

        assertThat(sentence).isGreaterThan(texts.indexOf("Your OpenAI API key"))
        assertThat(sentence).isLessThan(texts.indexOf("Model"))
    }

    @Test
    fun `with nothing refused, nothing is said on any page`() {
        // Every page that draws a refusal (D79); the index and the others draw none at all.
        for (texts in listOf(eating(null), backups(null), ai(null), food(null))) {
            assertThat(texts).doesNotContain(NOTHING)
            assertThat(texts).doesNotContain(PARTIAL)
            assertThat(texts).doesNotContain("All right")
        }
    }

    private fun backups(failed: SettingsRefusal?): List<String> = render.texts(heightPx = TALL) {
        BackupSettingsPage(
            state = SettingsUiState(failed = failed),
            onPickBackupFolder = {},
            onForgetBackupFolder = {},
            onBackUpNow = {},
            onSetDrive = {},
            onDriveNow = {},
            onOfferArchive = {},
            onConfirmArchive = {},
            onCancelArchive = {},
            onDismissArchiveMessage = {},
            onExport = {},
            onRestore = {},
            onConfirmRestore = {},
            onCancelRestore = {},
            onDismissBackupMessage = {},
            onDismissFailure = {},
            onBack = {},
        )
    }

    private fun ai(failed: SettingsRefusal?): List<String> = render.texts(heightPx = TALL) {
        AiSettingsPage(
            state = SettingsUiState(failed = failed),
            onSaveKey = {},
            onClearKey = {},
            onSetModel = {},
            onSetCeiling = {},
            onTest = {},
            onDismissFailure = {},
            onBack = {},
        )
    }

    private fun eating(failed: SettingsRefusal?): List<String> = render.texts(heightPx = TALL) {
        EatingSettingsPage(
            state = SettingsUiState(failed = failed),
            onSetWindow = { _, _ -> },
            onSetRatio = {},
            onClearWindow = {},
            onSetReminder = {},
            onSendReminderNow = {},
            onDismissFailure = {},
            onBack = {},
        )
    }

    private fun food(failed: SettingsRefusal?): List<String> = render.texts {
        FoodDatabaseSettingsPage(
            state = SettingsUiState(failed = failed),
            onSaveOffAccount = { _, _ -> },
            onClearOffAccount = {},
            onDismissFailure = {},
            onBack = {},
        )
    }

    private companion object {
        /** Tall enough that a whole page is laid out and none of it left unplaced. */
        const val TALL = 20_000

        const val NOTHING = "That didn't work, and nothing was changed. " +
            "What went wrong is under Settings → Recent problems."
        const val PARTIAL = "That didn't finish, and may have only partly happened. " +
            "What went wrong is under Settings → Recent problems."
    }
}
