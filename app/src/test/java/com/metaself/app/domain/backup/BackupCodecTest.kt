package com.metaself.app.domain.backup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.backup.BackupCodec
import org.junit.jupiter.api.Test

class BackupCodecTest {

    private val full = Backup(
        version = Backup.CURRENT_VERSION,
        exportedAtMillis = 1_772_000_000_000,
        profile = BackupProfile(
            heightCm = 180,
            birthYear = 1980,
            sex = "MALE",
            weightKg = 80.0,
            activity = "MODERATE",
            goalDirection = "LOSE",
            goalKgPerWeek = 0.5,
            goalTargetKg = 75.0,
            allowBelowFloor = false,
        ),
        meals = listOf(
            BackupMeal(
                epochDay = 20_699,
                loggedAtMillis = 1_000,
                note = null,
                items = listOf(
                    BackupItem(
                        name = "יוגורט בסגנון יווני",
                        portion = "130 g",
                        portionAmount = 130.0,
                        portionUnit = "g",
                        kcal = 130,
                        proteinG = 10,
                        carbsG = 5,
                        fatG = 7,
                        source = "AI_ESTIMATE",
                        confidence = "HIGH",
                    ),
                ),
            ),
        ),
        weights = listOf(BackupWeight(20_699, 80.0), BackupWeight(20_700, 79.5)),
        revision = BackupRevision(20_699, 80.0, 2090, 2100),
        revisionSeen = true,
        arrival = BackupArrival(75.0, 20_800),
        milestones = mapOf("FIRST_KG" to 20_710L),
        reminder = BackupReminder(enabled = true, hour = 20, minute = 0),
        ai = BackupAi(model = "gpt-4o-mini", dailyCeiling = 30),
    )

    @Test
    fun `everything survives a round trip`() {
        assertThat(BackupCodec.decode(BackupCodec.encode(full))).isEqualTo(full)
    }

    /**
     * The one file the owner is expected to open and check. A backup nobody can read is a backup
     * nobody has checked, and the first time it matters is the worst time to find it was empty.
     */
    @Test
    fun `the file is readable, and a saved food is in it as words`() {
        val text = BackupCodec.encode(full)

        assertThat(text).contains("יוגורט בסגנון יווני")
        assertThat(text).contains("\"kcal\": 130")
        assertThat(text.lines().size).isGreaterThan(10)
    }

    /**
     * There is nowhere in the format to put a key, and this is the test that keeps it that way. An
     * export ends up in a cloud drive and an email; a credential that spends money does not.
     */
    @Test
    fun `nothing that looks like an api key can appear in an export`() {
        val text = BackupCodec.encode(full).lowercase()

        assertThat(text).doesNotContain("api_key")
        assertThat(text).doesNotContain("apikey")
        assertThat(text).doesNotContain("sk-")
    }

    /** No partial restore: a half-restored record is worse than a failed one. */
    @Test
    fun `a file from a later version is refused whole`() {
        // Written against the CURRENT version rather than a literal 1: the last time this was a
        // literal, raising the version made the substitution a no-op and the test passed while
        // testing nothing.
        val fromTheFuture = BackupCodec.encode(full)
            .replace("\"version\": ${Backup.CURRENT_VERSION}", "\"version\": 99")

        assertThat(BackupCodec.decode(fromTheFuture)).isNull()
    }

    @Test
    fun `rubbish is refused without throwing`() {
        assertThat(BackupCodec.decode("this is not a backup")).isNull()
        assertThat(BackupCodec.decode("")).isNull()
        assertThat(BackupCodec.decode("{\"version\":")).isNull()
    }

    @Test
    fun `an empty phone exports an empty file rather than nothing at all`() {
        val empty = Backup(exportedAtMillis = 1)

        val decoded = BackupCodec.decode(BackupCodec.encode(empty))!!

        assertThat(decoded.meals).isEmpty()
        assertThat(decoded.profile).isNull()
    }
}
