package com.metaself.app.data.drive

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.settings.AutomaticBackupWording
import org.junit.jupiter.api.Test

class DriveFilesTest {

    @Test
    fun `it asks for the narrowest scope that works`() {
        assertThat(DriveFiles.SCOPE).isEqualTo("https://www.googleapis.com/auth/drive.file")
        // Not the scopes that would let it read everything the owner has.
        assertThat(DriveFiles.SCOPE).doesNotContain("drive.readonly")
        assertThat(DriveFiles.SCOPE).isNotEqualTo("https://www.googleapis.com/auth/drive")
    }

    @Test
    fun `it uploads to the real Drive, not to a sandbox`() {
        assertThat(DriveFiles.UPLOAD_URL).startsWith("https://www.googleapis.com/upload/drive/v3/")
    }

    @Test
    fun `a listing reads into files`() {
        val body = """
            {"files":[
              {"id":"abc","name":"metaself-2026-09-04.json"},
              {"id":"def","name":"metaself-2026-09-05.json"}
            ]}
        """.trimIndent()

        assertThat(DriveFiles.readListing(body))
            .containsExactly(
                DriveFile("abc", "metaself-2026-09-04.json"),
                DriveFile("def", "metaself-2026-09-05.json"),
            )
    }

    @Test
    fun `rubbish reads as nothing rather than throwing`() {
        assertThat(DriveFiles.readListing("not json")).isEmpty()
        assertThat(DriveFiles.readListing("")).isEmpty()
        assertThat(DriveFiles.readListing("""{"files":[{"name":"no id"}]}""")).isEmpty()
    }

    /** Running twice in a day should replace today's file, not leave two of it. */
    @Test
    fun `a file already holding today's name is found`() {
        val files = listOf(
            DriveFile("abc", "metaself-2026-09-04.json"),
            DriveFile("def", "metaself-2026-09-05.json"),
        )

        assertThat(DriveFiles.existing(files, "metaself-2026-09-05.json")).isEqualTo("def")
        assertThat(DriveFiles.existing(files, "metaself-2026-09-06.json")).isNull()
    }

    @Test
    fun `fourteen are kept and the fifteenth goes`() {
        val files = (1..15).map { DriveFile("id$it", "metaself-2026-09-%02d.json".format(it)) }

        assertThat(DriveFiles.toDelete(files).map { it.name })
            .containsExactly("metaself-2026-09-01.json")
    }

    /**
     * `drive.file` means nothing else is even visible, but the name check stays anyway: a backup
     * routine that deletes what it did not write is a data-loss bug waiting for a wrong assumption.
     */
    @Test
    fun `nothing this app did not name is ever deleted`() {
        val files = (1..15).map { DriveFile("id$it", "metaself-2026-09-%02d.json".format(it)) } +
            listOf(DriveFile("x", "tax-return.pdf"), DriveFile("y", "notes.json"))

        assertThat(DriveFiles.toDelete(files).map { it.name })
            .containsExactly("metaself-2026-09-01.json")
    }

    @Test
    fun `the metadata carries a name and nothing else about him`() {
        val metadata = DriveFiles.metadataFor("metaself-2026-09-05.json")

        assertThat(metadata).isEqualTo("""{"name":"metaself-2026-09-05.json"}""")
        listOf("weight", "kg", "email", "goal").forEach {
            assertThat(metadata.lowercase()).doesNotContain(it)
        }
    }

    /**
     * Drive is a second destination and never a replacement. If its failure could cost the folder
     * copy, a single bad morning at Google would take both.
     */
    @Test
    fun `every failure leaves something to say and nothing to lose`() {
        listOf(
            DriveOutcome.Failed("IOException"),
            DriveOutcome.Written("metaself-2026-09-05.json", deleted = 0),
        ).forEach { assertThat(AutomaticBackupWording.drive(it)).isNotEmpty() }

        assertThat(AutomaticBackupWording.drive(DriveOutcome.Failed("IOException")))
            .contains("other copies are unaffected")
    }

    @Test
    fun `a written file is named, and old ones counted`() {
        assertThat(AutomaticBackupWording.drive(DriveOutcome.Written("metaself-2026-09-05.json", 1)))
            .isEqualTo("Wrote metaself-2026-09-05.json to your Drive. Removed 1 older copy.")
    }

    /** What the switch promises, before it is turned on. */
    @Test
    fun `it says the key is not in the Drive copy either`() {
        assertThat(AutomaticBackupWording.DRIVE_OFF).contains("only this app can see")
        assertThat(AutomaticBackupWording.DRIVE_OFF).contains("API key is not in them")
    }
}
