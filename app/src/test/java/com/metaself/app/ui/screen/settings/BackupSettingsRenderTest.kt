package com.metaself.app.ui.screen.settings

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.HealthRecordState
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.health.HealthRecordWording
import com.metaself.app.ui.settings.AutomaticBackupWording
import java.time.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Settings' Backups page (D79): three groups — Keeping a copy, Google Drive, By hand — and Drive's
 * way back to the detailed readings' months beside the other Drive controls, where it moved from
 * Movement. Asked by order in the drawn tree, which is what a render test here can say about
 * position; nothing about size (`CLAUDE.md`). Figures invented.
 *
 * JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class BackupSettingsRenderTest {

    private val render = ComposeRender()

    private var offered = 0

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the page holds its three groups in order`() {
        val texts = draw(SettingsUiState())

        assertThat(texts).contains("Backups")
        assertThat(texts).containsAtLeast(
            "Keeping a copy", "Pick a folder", "Google Drive", "By hand",
            "Save to a file", "Restore from a file",
        ).inOrder()
    }

    @Test
    fun `the Drive switch and its line are in the Drive group`() {
        val texts = draw(SettingsUiState())
        val drive = texts.indexOf("Google Drive")
        val byHand = texts.indexOf("By hand")

        assertThat(texts.indexOf(AutomaticBackupWording.DRIVE_OFF))
            .isIn(Range.open(drive, byHand))
    }

    @Test
    fun `with Drive on, the Drive group copies now and brings the detailed readings back`() {
        val texts = draw(SettingsUiState(driveOn = true))
        val inDrive = Range.open(
            texts.indexOf("Google Drive"), texts.indexOf("By hand"),
        )

        assertThat(texts.indexOf("Copy to Drive now")).isIn(inDrive)
        assertThat(texts.indexOf(BRING_BACK)).isIn(inDrive)
    }

    @Test
    fun `with Drive off, neither is offered`() {
        val texts = draw(SettingsUiState(driveOn = false))

        assertThat(texts).doesNotContain("Copy to Drive now")
        assertThat(texts).doesNotContain(BRING_BACK)
    }

    @Test
    fun `pressing bring back asks for the months`() {
        draw(SettingsUiState(driveOn = true))

        render.click(BRING_BACK)

        assertThat(offered).isEqualTo(1)
    }

    @Test
    fun `with a health record, the detailed readings line is in the Drive group`() {
        val texts = draw(SettingsUiState(driveOn = true, healthRecord = HealthRecordState(days = 30)))
        val line = HealthRecordWording.detailedBackup(driveOn = true)

        assertThat(texts.count { it == line }).isEqualTo(1)
        assertThat(texts.indexOf(line)).isIn(
            Range.open(texts.indexOf("Google Drive"), texts.indexOf("By hand")),
        )
    }

    @Test
    fun `asked from the Drive controls, the question is drawn there, once`() {
        val texts = draw(SettingsUiState(driveOn = true, pendingArchive = QUESTION, archiveFromDrive = true))

        assertThat(texts.count { it == QUESTION }).isEqualTo(1)
        assertThat(texts.indexOf(QUESTION)).isGreaterThan(texts.indexOf("Google Drive"))
        assertThat(texts.indexOf(QUESTION)).isLessThan(texts.indexOf("By hand"))
        assertThat(texts).contains("Bring them back")
    }

    @Test
    fun `offered after a restore, the question is drawn under By hand, once`() {
        val texts = draw(SettingsUiState(driveOn = true, pendingArchive = QUESTION, archiveFromDrive = false))

        assertThat(texts.count { it == QUESTION }).isEqualTo(1)
        assertThat(texts.indexOf(QUESTION)).isGreaterThan(texts.indexOf("Restore from a file"))
    }

    @Test
    fun `what asking from Drive came to is said in the Drive group`() {
        val texts = draw(SettingsUiState(driveOn = true, archiveMessage = HealthRecordWording.NONE_IN_DRIVE))

        assertThat(texts.indexOf(HealthRecordWording.NONE_IN_DRIVE)).isIn(
            Range.open(texts.indexOf("Google Drive"), texts.indexOf("By hand")),
        )
        assertThat(texts).contains("Got it")
    }

    @Test
    fun `a folder that is set can be changed, copied now, or turned off`() {
        val texts = draw(SettingsUiState(hasBackupFolder = true, lastBackup = LocalDate.of(2026, 9, 3)))

        assertThat(texts).containsAtLeast("Change the folder", "Copy now", "Turn it off", "Google Drive")
            .inOrder()
    }

    private fun draw(state: SettingsUiState): List<String> = render.texts(heightPx = TALL) {
        BackupSettingsPage(
            state = state,
            onPickBackupFolder = {},
            onForgetBackupFolder = {},
            onBackUpNow = {},
            onSetDrive = {},
            onDriveNow = {},
            onOfferArchive = { offered++ },
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

    private companion object {
        /** Tall enough that the whole page is laid out and none of it left unplaced. */
        const val TALL = 20_000

        const val BRING_BACK = "Bring back detailed readings from Drive"

        /** `HealthRecordWording.offerMonths`, for an invented count. */
        val QUESTION = HealthRecordWording.offerMonths(3)
    }
}
