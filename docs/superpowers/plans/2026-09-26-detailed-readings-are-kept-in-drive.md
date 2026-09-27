# Detailed readings are kept in Drive — Implementation Plan (health record, phase 3)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans, task by task. Steps use checkbox syntax for tracking.

**Goal:** D71's second half. The raw readings (`health_readings`) are written to the owner's Drive as
one gzip-compressed JSON file per calendar month — the months whose rows changed, with each daily
backup, never deleted — and a restore offers to bring them back. Settings says whether the detailed
readings are backed up.

**Architecture:**

```
data/health/ReadingArchive.kt   pure: rows ⇄ gzip JSON bytes, file names          JUnit 5
data/drive/DriveHttp.kt         the Drive REST calls, extracted from DriveBackup  untested (network)
data/drive/DriveFiles.kt        + archive listing query                           JUnit 5
data/health/HealthArchive.kt    write out-of-date months; list; restore           JUnit 5 with fakes
data/health/RoomHealthStore     + ArchiveRecord (month rows, marking written)     Robolectric, CI
data/backup/AutomaticBackup     writes the archive after the Drive daily copy
ui/screen/settings/…            the Drive line; the offer after a restore
```

`HealthArchive` talks to two small interfaces — `ArchiveDrive` (list / upload / download) and
`ArchiveRecord` (which months are out of date, a month's rows, mark written) — plus the existing
`HealthStore` for restoring, so every decision is tested on any machine.

**Decision:** the owner's, 2026-09-26 — D71 in `docs/superpowers/specs/2026-09-26-health-record-design.md`
(§1 D71, §5 phase 3).

**Tech stack:** Kotlin, kotlinx.serialization, `java.util.zip`, OkHttp (already used by `DriveBackup`),
Room, Compose. No new dependency.

**Red lines (stop and report if crossed):**

- **A month file is never deleted, and the daily-backup pruning can never match one.** Month files are
  named `metaself-readings-YYYY-MM.json.gz`; `BackupSchedule.isBackupFile` requires the name to end in
  `.json`, so it is false for them — and a test pins exactly that, plus `DriveFiles.toDelete` never
  returning one. Any change to either name rule that breaks this test is a data-loss bug.
- **Drive stays a second destination.** A failed archive write never affects the daily copy, the local
  folder, or the owner's use of the app (D8). Failures go to the problem log as kind `"drive"`.
- **`drive.file` scope only.** No new scope, no new consent.
- **A restore never deletes readings.** Bringing months back replaces records by id (the store's
  existing rule); nothing the phone has that the file lacks is removed.
- **No schema change.** `app/schemas` untouched.
- **The daily file is unchanged.** Raw readings never enter it (D71).
- **Never `git add -A`; never bare `./gradlew`.** Anonymisation: invented, round figures that say so;
  origin `com.example.band`.

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` and `~/bin/ms-release` share one lock; a
finished build's daemon lingers up to ten minutes. **Before every Gradle command run `free -m`;** under
about 4000 MB available (6000 MB before `ms-release`), wait and check again. Never `gradlew --stop`.
One build at a time.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-health3.log 2>&1; echo "exit $?"
```

SQLite classes skip locally (ten, listed in `CLAUDE.md`); report them as skipped.

---

### Task 1: The month file

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/ReadingArchive.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/ReadingArchiveTest.kt`

- [ ] **Step 0: Branch.** `git checkout main && git pull && git checkout -b detailed-readings-are-kept-in-drive`;
  commit this plan (`docs: the health record's phase 3 plan`, co-author line).

- [ ] **Step 1: Failing test.**

```kotlin
package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.drive.DriveFile
import com.metaself.app.data.drive.DriveFiles
import com.metaself.app.domain.backup.BackupSchedule
import org.junit.jupiter.api.Test
import java.util.zip.GZIPInputStream

/** Every figure is invented and round. */
class ReadingArchiveTest {

    @Test
    fun `a month is named for itself`() {
        assertThat(ReadingArchive.fileName("2026-09")).isEqualTo("metaself-readings-2026-09.json.gz")
        assertThat(ReadingArchive.monthOf("metaself-readings-2026-09.json.gz")).isEqualTo("2026-09")
        assertThat(ReadingArchive.monthOf("metaself-2026-09-03.json")).isNull()
    }

    /** Red line: the daily pruning must never be able to delete a month file. */
    @Test
    fun `the daily backup's pruning can never match a month file`() {
        val month = ReadingArchive.fileName("2026-09")
        assertThat(BackupSchedule.isBackupFile(month)).isFalse()

        val manyDays = (1..30).map { DriveFile("d$it", BackupSchedule.fileNameFor("2026-09-%02d".format(it))) }
        val months = listOf(DriveFile("m1", month), DriveFile("m2", ReadingArchive.fileName("2026-08")))

        assertThat(DriveFiles.toDelete(manyDays + months).map { it.id }).containsNoneOf("m1", "m2")
    }

    @Test
    fun `rows go out and come back exactly`() {
        val rows = listOf(beat("hr-1", 0, 60.0), beat("hr-1", 1, 62.0), steps("st-1"))

        val back = ReadingArchive.decode(ReadingArchive.encode("2026-09", rows))!!

        assertThat(back.month).isEqualTo("2026-09")
        assertThat(back.readings).containsExactlyElementsIn(rows.map { it.copy(id = 0) })
    }

    @Test
    fun `the file is gzip and, unzipped, readable words`() {
        val bytes = ReadingArchive.encode("2026-09", listOf(beat("hr-1", 0, 60.0)))

        val text = GZIPInputStream(bytes.inputStream()).bufferedReader().readText()

        assertThat(text).contains("\"month\": \"2026-09\"")
        assertThat(text).contains("\"kind\": \"HEART_RATE\"")
        assertThat(text).contains("\"record_id\": \"hr-1\"")
    }

    @Test
    fun `rubbish, a truncated file, or a later version is refused`() {
        assertThat(ReadingArchive.decode(byteArrayOf(1, 2, 3))).isNull()
        val whole = ReadingArchive.encode("2026-09", listOf(beat("hr-1", 0, 60.0)))
        assertThat(ReadingArchive.decode(whole.copyOf(whole.size / 2))).isNull()
        assertThat(ReadingArchive.decode(ReadingArchive.encodeRaw("""{"version": 99, "month": "2026-09", "readings": []}""")))
            .isNull()
    }

    private fun beat(record: String, index: Int, bpm: Double) = HealthReadingEntity(
        kind = "HEART_RATE", startMillis = 1_000L + index * 60_000, endMillis = null, value = bpm,
        unit = "bpm", origin = "com.example.band", recordId = record, sampleIndex = index, epochDay = 20_699,
    )

    private fun steps(record: String) = HealthReadingEntity(
        kind = "STEPS", startMillis = 1_000, endMillis = 61_000, value = 100.0, unit = "count",
        origin = "com.example.band", recordId = record, sampleIndex = 0, epochDay = 20_699,
    )
}
```

- [ ] **Step 2: Run, see it fail.**

- [ ] **Step 3: `ReadingArchive.kt`:**

```kotlin
package com.metaself.app.data.health

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * One month of raw readings as a file in the owner's Drive (D71). Pure: rows in, bytes out.
 *
 * gzip-compressed JSON, indented like the daily file, so that unzipped it is something a person can
 * open and check. **Named so that the daily backup's pruning can never match it** — the daily rule
 * requires a name ending in `.json`, and this ends in `.json.gz` (`ReadingArchiveTest` pins it).
 */
object ReadingArchive {

    const val VERSION = 1
    private const val PREFIX = "metaself-readings-"
    private const val SUFFIX = ".json.gz"

    @Serializable
    data class File(
        val version: Int = VERSION,
        val month: String,
        val readings: List<Row> = emptyList(),
    )

    /** One row, written whole. */
    @Serializable
    data class Row(
        val kind: String,
        @SerialName("start") val startMillis: Long,
        @SerialName("end") val endMillis: Long? = null,
        val value: Double,
        val unit: String,
        val origin: String,
        @SerialName("record_id") val recordId: String,
        @SerialName("sample_index") val sampleIndex: Int = 0,
        @SerialName("epoch_day") val epochDay: Long,
    )

    /** What [decode] gives back: the month and its rows, ids zeroed. */
    data class Month(val month: String, val readings: List<HealthReadingEntity>)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun fileName(month: String): String = "$PREFIX$month$SUFFIX"

    fun monthOf(fileName: String): String? =
        fileName.takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
            ?.removePrefix(PREFIX)?.removeSuffix(SUFFIX)
            ?.takeIf { Regex("""\d{4}-\d{2}""").matches(it) }

    fun encode(month: String, rows: List<HealthReadingEntity>): ByteArray = encodeRaw(
        json.encodeToString(
            File.serializer(),
            File(month = month, readings = rows.map { it.toRow() }),
        ),
    )

    /** For tests and for [encode]: gzip of UTF-8 text. */
    fun encodeRaw(text: String): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }.toByteArray()

    /** Null for anything that cannot be read whole, or a later version. No partial month. */
    fun decode(bytes: ByteArray): Month? = runCatching {
        val text = GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).readText()
        val file = json.decodeFromString(File.serializer(), text)
        if (file.version !in 1..VERSION) return null
        Month(file.month, file.readings.map { it.toEntity() })
    }.getOrNull()

    private fun HealthReadingEntity.toRow() =
        Row(kind, startMillis, endMillis, value, unit, origin, recordId, sampleIndex, epochDay)

    private fun Row.toEntity() = HealthReadingEntity(
        kind = kind, startMillis = startMillis, endMillis = endMillis, value = value, unit = unit,
        origin = origin, recordId = recordId, sampleIndex = sampleIndex, epochDay = epochDay,
    )
}
```

(`return null` inside `runCatching` returns from `decode` — correct in Kotlin because the lambda is
inlined.)

- [ ] **Step 4: Run, see it pass. Step 5: Commit** both files:
  `feat: a month of detailed readings is one gzip file a person can open (D71)`.

---

### Task 2: Drive's calls, shared

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/drive/DriveHttp.kt`
- Modify: `app/src/main/java/com/metaself/app/data/drive/DriveBackup.kt`
- Modify: `app/src/main/java/com/metaself/app/data/drive/DriveFiles.kt`
- Test: `app/src/test/java/com/metaself/app/data/drive/DriveFilesTest.kt`

Behaviour of the daily Drive copy must not change: this task only moves its HTTP into a class both it
and the archive use.

- [ ] **Step 1: Failing test** in `DriveFilesTest`:

```kotlin
    @Test
    fun `the archive listing asks only for month files`() {
        assertThat(DriveFiles.archiveListQuery())
            .isEqualTo("name contains 'metaself-readings-' and trashed = false")
    }
```

- [ ] **Step 2: `DriveFiles`** — add:

```kotlin
    /** Search for this app's own month files (D71). */
    fun archiveListQuery(): String = "name contains 'metaself-readings-' and trashed = false"
```

- [ ] **Step 3: `DriveHttp.kt`** — move `DriveBackup`'s `client`, `list`, `upload` and `delete`
  here unchanged in behaviour, generalised:

```kotlin
package com.metaself.app.data.drive

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drive's REST calls, as the daily copy and the month archive both need them. Blocking; callers are
 * already on `Dispatchers.IO`. Untested for the reason `DriveAccess` is: no network and no account on
 * the build machine. Everything that decides anything is in [DriveFiles].
 */
@Singleton
class DriveHttp @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun list(query: String, token: String): List<DriveFile> {
        val url = "${DriveFiles.FILES_URL}?q=${URLEncoder.encode(query, "UTF-8")}" +
            "&fields=files(id,name)&pageSize=1000"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) emptyList() else DriveFiles.readListing(response.body?.string().orEmpty())
        }
    }

    fun upload(fileName: String, contents: ByteArray, mimeType: String, token: String): Boolean {
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(DriveFiles.metadataFor(fileName).toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(contents.toRequestBody(mimeType.toMediaType()))
            .build()
        val request = Request.Builder()
            .url(DriveFiles.UPLOAD_URL)
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return client.newCall(request).execute().use { it.isSuccessful }
    }

    fun download(id: String, token: String): ByteArray? {
        val request = Request.Builder()
            .url("${DriveFiles.FILES_URL}/$id?alt=media")
            .header("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }

    fun delete(id: String, token: String): Boolean {
        val request = Request.Builder()
            .url("${DriveFiles.FILES_URL}/$id")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        return client.newCall(request).execute().use { it.isSuccessful }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
    }
}
```

**`pageSize` is 1000 for both listings** (the daily listing used 100; a list of this app's own files is
small either way, and a month archive grows by twelve a year). Keep the daily copy's behaviour otherwise
identical.

- [ ] **Step 4: `DriveBackup`** takes `private val http: DriveHttp` in its constructor instead of
  building its own client; `list(token)` becomes `http.list(DriveFiles.listQuery(), token)`,
  `upload(name, contents, token)` becomes `http.upload(name, contents.toByteArray(Charsets.UTF_8), "application/json", token)`,
  `delete` → `http.delete`. Remove its now-unused OkHttp code and imports.

- [ ] **Step 5: Run** `--tests "com.metaself.app.data.drive.*" --tests "com.metaself.app.ui.screen.settings.*"`
  → pass (SettingsViewModelTest constructs `DriveBackup`? If it does, pass `DriveHttp()`; say so).

- [ ] **Step 6: Commit** the four files: `refactor: Drive's calls are shared by the daily copy and the month archive`.

---

### Task 3: The record's side of the archive

**Files:**
- Modify: `app/src/main/java/com/metaself/app/data/health/HealthPorts.kt`
- Modify: `app/src/main/java/com/metaself/app/data/health/HealthReadingDao.kt`
- Modify: `app/src/main/java/com/metaself/app/data/health/RoomHealthStore.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/HealthRecordStoreTest.kt` (CI)

- [ ] **Step 1: Port**, appended to `HealthPorts.kt`:

```kotlin
/** What the Drive archive needs from the record (D71). */
interface ArchiveRecord {
    /** Months never written, or changed since, oldest first, as "YYYY-MM". */
    suspend fun monthsOutOfDate(): List<String>
    /** Every raw reading filed on a day of [month], in time order. */
    suspend fun readingsIn(month: String): List<HealthReadingEntity>
    /** [month] is in Drive as it stood at [atMillis]. */
    suspend fun markWritten(month: String, atMillis: Long)
}
```

- [ ] **Step 2: DAO**, `HealthReadingDao`:

```kotlin
    /** One kind's rows over a day range: the (kind, epochDay, startMillis) index applies. */
    @Query(
        "SELECT * FROM health_readings WHERE kind = :kind AND epochDay BETWEEN :fromDay AND :toDay " +
            "ORDER BY startMillis, sampleIndex",
    )
    suspend fun ofKindInDays(kind: String, fromDay: Long, toDay: Long): List<HealthReadingEntity>
```

- [ ] **Step 3: `RoomHealthStore` implements `ArchiveRecord`:**

```kotlin
    override suspend fun monthsOutOfDate(): List<String> = bookkeepingDao.monthsOutOfDate().map { it.month }

    /** Kind by kind, so the index serves every query; a month is a few tens of thousands of rows. */
    override suspend fun readingsIn(month: String): List<HealthReadingEntity> {
        val first = java.time.YearMonth.parse(month).atDay(1)
        val last = java.time.YearMonth.parse(month).atEndOfMonth()
        return HealthKind.entries.filter { it.isReading }.flatMap { kind ->
            readingDao.ofKindInDays(kind.name, first.toEpochDay(), last.toEpochDay())
        }
    }

    /**
     * Written as it stood at [atMillis] — the moment its rows were read, taken BEFORE reading, so a
     * change made while the file was being written leaves `changedAt` later and the month out of date.
     */
    override suspend fun markWritten(month: String, atMillis: Long) {
        val known = bookkeepingDao.month(month) ?: return
        bookkeepingDao.putMonth(known.copy(writtenAtMillis = atMillis))
    }
```

(Use the store's real DAO property names — they were renamed with a `Dao` suffix in phase 2.) Bind
`ArchiveRecord` → `RoomHealthStore` in `DataModule` (`@Singleton`, the same instance as `HealthStore`:
provide both from one `RoomHealthStore` — make `RoomHealthStore` itself `@Singleton`).

- [ ] **Step 4: CI tests** in `HealthRecordStoreTest` (invented figures):
  `a month's readings are every kind's rows on its days, and no other month's` (rows on 2026-08-31,
  2026-09-01 and 2026-09-30 of two kinds; `readingsIn("2026-09")` returns the September ones only);
  `a month written after its last change is no longer out of date, one changed since is`
  (`apply` a row → month out of date; `markWritten(month, now + 1)` → not; `markWritten(month, changedAt - 1)` → still).

- [ ] **Step 5: Run** `--tests "com.metaself.app.data.health.*"` → pure pass, SQLite skip. **Commit**:
  `feat: the record says which months Drive lacks and what is in them (D71)`.

---

### Task 4: The archive

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/HealthArchive.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/HealthArchiveTest.kt`

- [ ] **Step 1: Ports and outcome**, at the top of `HealthArchive.kt`:

```kotlin
/** The Drive calls the archive makes, with authorisation already done. A fake in tests. */
interface ArchiveDrive {
    /** A token, or null when Drive cannot be reached or consent is wanted (never asked for here). */
    suspend fun token(): String?
    suspend fun list(token: String): List<DriveFile>
    suspend fun upload(fileName: String, bytes: ByteArray, token: String): Boolean
    suspend fun download(id: String, token: String): ByteArray?
    suspend fun delete(id: String, token: String): Boolean
}

/** What bringing months back did. */
data class ArchiveRestore(val months: Int, val readings: Int, val unreadable: Int)

/** What Settings asks of the archive. */
interface ReadingsArchive {
    /** How many month files Drive holds, or null when Drive cannot be reached. */
    suspend fun monthsInDrive(): Int?
    suspend fun restoreAll(): ArchiveRestore?

    companion object {
        val NONE = object : ReadingsArchive {
            override suspend fun monthsInDrive(): Int? = null
            override suspend fun restoreAll(): ArchiveRestore? = null
        }
    }
}
```

- [ ] **Step 2: Failing test** `HealthArchiveTest` (JUnit 5), with `FakeDrive` (a map name → bytes,
  recording calls; `token` configurable to null), `FakeArchiveRecord` (months out of date, rows per month,
  written marks), and a `FakeStore : HealthStore` recording `apply` and `summarise`:

```kotlin
    @Test
    fun `each out-of-date month is written, replacing the one already there, and marked`() = runTest {
        record.outOfDate = listOf("2026-08", "2026-09")
        record.rows["2026-08"] = listOf(beat("hr-1", day = 20_690))
        record.rows["2026-09"] = listOf(beat("hr-2", day = 20_699))
        drive.files["metaself-readings-2026-09.json.gz"] = byteArrayOf(0)

        val written = archive.writeOutOfDate()

        assertThat(written).isEqualTo(2)
        assertThat(drive.files.keys).containsExactly("metaself-readings-2026-08.json.gz", "metaself-readings-2026-09.json.gz")
        assertThat(ReadingArchive.decode(drive.files.getValue("metaself-readings-2026-09.json.gz"))!!.readings.single().recordId)
            .isEqualTo("hr-2")
        assertThat(record.written.keys).containsExactly("2026-08", "2026-09")
        // Marked as of a moment taken BEFORE the rows were read.
        assertThat(record.written.getValue("2026-08")).isEqualTo(NOW)
    }

    @Test
    fun `a month whose upload failed is not marked, so it is tried again`() = runTest {
        record.outOfDate = listOf("2026-09")
        record.rows["2026-09"] = listOf(beat("hr-1", day = 20_699))
        drive.refuseUploads = true

        archive.writeOutOfDate()

        assertThat(record.written).isEmpty()
        assertThat(problems.logged.single().kind).isEqualTo("drive")
    }

    @Test
    fun `no Drive, nothing written and nothing marked`() = runTest {
        record.outOfDate = listOf("2026-09")
        drive.tokenAvailable = false

        assertThat(archive.writeOutOfDate()).isEqualTo(0)
        assertThat(drive.calls).containsExactly("token")
    }

    /** Red line: an archive write never deletes a month file. */
    @Test
    fun `writing never deletes anything but the file it replaces`() = runTest {
        drive.files["metaself-readings-2026-07.json.gz"] = byteArrayOf(0)
        record.outOfDate = listOf("2026-09")
        record.rows["2026-09"] = listOf(beat("hr-1", day = 20_699))

        archive.writeOutOfDate()

        assertThat(drive.files).containsKey("metaself-readings-2026-07.json.gz")
        assertThat(drive.calls.filter { it.startsWith("delete") }).isEmpty()
    }

    @Test
    fun `Drive's months are counted, daily files and strangers ignored`() = runTest {
        drive.files["metaself-readings-2026-08.json.gz"] = ReadingArchive.encode("2026-08", emptyList())
        drive.files["metaself-readings-2026-09.json.gz"] = ReadingArchive.encode("2026-09", emptyList())
        drive.files["metaself-2026-09-03.json"] = byteArrayOf(0)

        assertThat(archive.monthsInDrive()).isEqualTo(2)
    }

    @Test
    fun `bringing months back applies every record in them and re-summarises their days`() = runTest {
        drive.files["metaself-readings-2026-09.json.gz"] = ReadingArchive.encode(
            "2026-09", listOf(beat("hr-1", day = 20_699, index = 0), beat("hr-1", day = 20_699, index = 1), beat("hr-2", day = 20_700)),
        )

        val result = archive.restoreAll()!!

        assertThat(result).isEqualTo(ArchiveRestore(months = 1, readings = 3, unreadable = 0))
        val applied = store.applied.flatten().filterIsInstance<ReadRecord.Reading>()
        assertThat(applied.map { it.recordId }).containsExactly("hr-1", "hr-2")
        assertThat(applied.first { it.recordId == "hr-1" }.samples).hasSize(2)
        assertThat(store.summarised.single().first).containsExactly(20_699L, 20_700L)
        // Totals are not asked for: every metric is marked failed, so the restored daily figures stand.
        assertThat(store.summarised.single().second.failed).containsExactlyElementsIn(TotalMetric.entries)
        assertThat(record.written.keys).containsExactly("2026-09")
    }

    @Test
    fun `an unreadable month is counted and skipped, never half-applied`() = runTest {
        drive.files["metaself-readings-2026-08.json.gz"] = byteArrayOf(1, 2, 3)
        drive.files["metaself-readings-2026-09.json.gz"] = ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_699)))

        val result = archive.restoreAll()!!

        assertThat(result.months).isEqualTo(1)
        assertThat(result.unreadable).isEqualTo(1)
    }

    @Test
    fun `a reading of a kind this version does not know is left out`() = runTest {
        drive.files["metaself-readings-2026-09.json.gz"] = ReadingArchive.encode(
            "2026-09", listOf(beat("hr-1", day = 20_699), beat("x-1", day = 20_699).copy(kind = "STRESS")),
        )

        assertThat(archive.restoreAll()!!.readings).isEqualTo(1)
    }
```

(`FakeDrive` must model Drive faithfully: a list of (id, name, bytes); `upload` appends a NEW id even when the name exists; `delete(id)` removes that id only; `files` in the tests is a view by name of the latest upload. Otherwise the replace test would delete the file it just wrote.) (Helpers: `beat(id, day, index = 0)` builds a HEART_RATE `HealthReadingEntity` on `day` with
`startMillis = day * 86_400_000L + index * 60_000`, origin `com.example.band`; `NOW = 5_000L`; the
archive is built `HealthArchive(drive, record, store, problems, now = { NOW })`.)

- [ ] **Step 3: Run, see it fail.**

- [ ] **Step 4: `HealthArchive`:**

```kotlin
/**
 * The raw readings in the owner's Drive, one file per month (D71).
 *
 * Written with each daily backup: the months whose rows changed since they were last written. Never
 * deletes a month; replaces only the file of the month it is writing. A restore brings every month
 * back through the record's own door, so a record already here is replaced, never doubled.
 * Never throws upwards (D8); failures go to the problem log as `"drive"`.
 */
@Singleton
class HealthArchive @Inject constructor(
    private val drive: ArchiveDrive,
    private val record: ArchiveRecord,
    private val store: HealthStore,
    private val problems: ProblemLog,
    private val now: Now,
) : ReadingsArchive {

    /** @return how many months were written. */
    suspend fun writeOutOfDate(): Int = guarded(0) {
        val months = record.monthsOutOfDate()
        if (months.isEmpty()) return@guarded 0
        val token = drive.token() ?: return@guarded 0
        val existing = drive.list(token)
        var written = 0
        for (month in months) {
            val asOf = now()
            val bytes = ReadingArchive.encode(month, record.readingsIn(month))
            val name = ReadingArchive.fileName(month)
            if (!drive.upload(name, bytes, token)) {
                problems.record("drive", "month $month could not be uploaded")
                continue
            }
            // Only after the new one is safely up: the previous copy of THIS month, and nothing else.
            existing.filter { it.name == name }.forEach { drive.delete(it.id, token) }
            record.markWritten(month, asOf)
            written++
        }
        written
    }

    override suspend fun monthsInDrive(): Int? = guarded(null) {
        val token = drive.token() ?: return@guarded null
        drive.list(token).count { ReadingArchive.monthOf(it.name) != null }
    }

    override suspend fun restoreAll(): ArchiveRestore? = guarded(null) {
        val token = drive.token() ?: return@guarded null
        val files = drive.list(token).filter { ReadingArchive.monthOf(it.name) != null }.sortedBy { it.name }
        var months = 0
        var readings = 0
        var unreadable = 0
        val touched = mutableSetOf<Long>()
        for (file in files) {
            val month = drive.download(file.id, token)?.let(ReadingArchive::decode)
            if (month == null) {
                unreadable++
                continue
            }
            val records = month.readings
                .mapNotNull { row -> HealthKind.parse(row.kind)?.takeIf { it.isReading }?.let { it to row } }
                .groupBy { (kind, row) -> Triple(kind, row.origin, row.recordId) }
                .map { (key, rows) ->
                    ReadRecord.Reading(
                        kind = key.first, origin = key.second, recordId = key.third,
                        samples = rows.map { it.second }.sortedBy { it.sampleIndex }
                            .map { Sample(it.startMillis, it.endMillis, it.value) },
                    )
                }
            touched += store.apply(records, emptyList())
            record.markWritten(month.month, now())
            readings += records.sumOf { it.samples.size }
            months++
        }
        if (touched.isNotEmpty()) {
            store.summarise(touched, TotalsResult(emptyMap(), TotalMetric.entries.toSet()), now())
        }
        ArchiveRestore(months, readings, unreadable)
    }

    private suspend fun <T> guarded(otherwise: T, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        withContext(Dispatchers.IO) {
            problems.record("drive", "archive: ${failure::class.java.simpleName} ${failure.message}")
        }
        otherwise
    }
}
```

Adapt `TotalsResult`'s constructor to the real one from phase 2 (read `HealthPorts.kt`). The restored
rows' `epochDay` is recomputed by `HealthRows` from the phone's current zone when re-applied — accepted
and consistent with the copying.

- [ ] **Step 5: `ArchiveDrive` over Drive.** In `data/drive/DriveArchiveDrive.kt`:

```kotlin
/** [ArchiveDrive] over [DriveAccess] and [DriveHttp]. Never asks for consent: the daily copy does. */
class DriveArchiveDrive @Inject constructor(
    private val access: DriveAccess,
    private val http: DriveHttp,
) : ArchiveDrive {
    override suspend fun token(): String? = (access.authorise() as? DriveAuth.Token)?.accessToken
    override suspend fun list(token: String) = withContext(Dispatchers.IO) { http.list(DriveFiles.archiveListQuery(), token) }
    override suspend fun upload(fileName: String, bytes: ByteArray, token: String) =
        withContext(Dispatchers.IO) { http.upload(fileName, bytes, "application/gzip", token) }
    override suspend fun download(id: String, token: String) = withContext(Dispatchers.IO) { http.download(id, token) }
    override suspend fun delete(id: String, token: String) = withContext(Dispatchers.IO) { http.delete(id, token) }
}
```

Bind in `DataModule`: `ArchiveDrive` → `DriveArchiveDrive`, `ReadingsArchive` → `HealthArchive`
(`@Singleton`).

- [ ] **Step 6: Run, see it pass. Step 7: Commit**:
  `feat: the detailed readings are written to Drive by month and can be brought back (D71)`.

---

### Task 5: Written with each daily backup

**Files:**
- Modify: `app/src/main/java/com/metaself/app/data/backup/AutomaticBackup.kt`

- [ ] **Step 1:** `AutomaticBackup` takes `private val archive: HealthArchive`; inside the existing
  `if (profiles.driveBackupOn.first())` block, after `drive.write(...)`:

```kotlin
            // The detailed readings, month by month (D71). A second call, so a failed month never
            // costs the daily file, and the daily file's failure never stops the months.
            runCatching { archive.writeOutOfDate() }
```

Also in `SettingsViewModel.driveNow` (the "write to Drive now" path taken when Drive is switched on),
after a successful `drive.write`, launch the same `archive.writeOutOfDate()` quietly — so switching Drive
on sends the months at once rather than tomorrow. If `SettingsViewModel` cannot take `HealthArchive`
without breaking its tests, take `ReadingsArchive` extended with `suspend fun writeOutOfDate(): Int`
(add it to the interface and `NONE`) and a defaulted parameter, in both constructors.

- [ ] **Step 2: Compile; run** `--tests "com.metaself.app.ui.screen.settings.*" --tests "com.metaself.app.data.backup.*"`
  (SettingsViewModelTest constructs `AutomaticBackup`: give it a `HealthArchive` built on fakes, or make the
  parameter the `ReadingsArchive` interface with a default — choose the smaller change and say which).

- [ ] **Step 3: Commit**: `feat: each daily backup to Drive also sends the months that changed (D71)`.

---

### Task 6: What Settings says, and the offer after a restore

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/health/HealthRecordWording.kt` (+ test)
- Modify: `SettingsViewModel.kt`, `SettingsUiState.kt`, `SettingsScreen.kt`, `MetaSelfNavHost.kt`,
  `app/src/main/res/values/strings.xml`
- Test: `HealthRecordWordingTest`, `SettingsViewModelTest`

- [ ] **Step 1: Failing wording tests:**

```kotlin
    @Test
    fun `the detailed readings line follows Drive`() {
        assertThat(HealthRecordWording.detailedBackup(driveOn = true))
            .isEqualTo("Detailed readings are copied to your Drive, one file a month.")
        assertThat(HealthRecordWording.detailedBackup(driveOn = false))
            .isEqualTo("Detailed readings are kept on this phone only while Drive backup is off; the daily backup has the summaries.")
    }

    @Test
    fun `the offer after a restore names the months`() {
        assertThat(HealthRecordWording.offerMonths(14))
            .isEqualTo("Also bring back 14 months of detailed readings from Drive?")
        assertThat(HealthRecordWording.offerMonths(1))
            .isEqualTo("Also bring back 1 month of detailed readings from Drive?")
    }

    @Test
    fun `what came back is said in numbers`() {
        assertThat(HealthRecordWording.broughtBack(ArchiveRestore(months = 2, readings = 40_000, unreadable = 0)))
            .isEqualTo("Brought back 2 months: 40,000 readings.")
        assertThat(HealthRecordWording.broughtBack(ArchiveRestore(months = 1, readings = 10, unreadable = 1)))
            .isEqualTo("Brought back 1 month: 10 readings. 1 file could not be read.")
    }
```

Replace `NOT_BACKED_UP` with `detailedBackup(driveOn)` everywhere (and its test).

- [ ] **Step 2: Implement** the three functions in `HealthRecordWording` (thousands separator as
  `MovementWording.number` does, `Locale.US`).

- [ ] **Step 3: The offer.** In `SettingsViewModel`: after `confirmRestore`'s successful restore, if
  `profiles.driveBackupOn.first()`, ask `archive.monthsInDrive()`; when it is a positive number, set a
  `pendingArchive` state to `HealthRecordWording.offerMonths(n)`. Add `fun confirmArchive()` (busy while it
  runs; calls `archive.restoreAll()`; sets `backupMessage` to `broughtBack(result)`, or to
  `"Drive could not be reached; the detailed readings were not brought back."` when null) and
  `fun cancelArchive()`. `SettingsUiState` gains `pendingArchive: String? = null`. `SettingsScreen` shows it
  in the same card style as `pendingRestore`, with buttons `settings_archive_bring` = "Bring them back"
  and `settings_archive_skip` = "Not now"; wire `onConfirmArchive` / `onCancelArchive` through
  `MetaSelfNavHost`. The `detailedBackup` line replaces the phase-2 line under the same condition
  (`days > 0`), using `state.driveOn`.

- [ ] **Step 4: VM tests** in `SettingsViewModelTest` with a fake `ReadingsArchive`: after a confirmed
  restore with Drive on and 3 months in Drive, `pendingArchive` holds the 3-month question; with Drive off,
  no question and `monthsInDrive` not called; `confirmArchive` sets the "Brought back …" message; a null
  result sets the unreachable message. Follow the file's existing restore tests for setup.

- [ ] **Step 5: Run** `--tests "com.metaself.app.ui.*"` → pass. **Commit**:
  `feat: Settings says where the detailed readings are, and offers them back after a restore (D71)`.

---

### Task 7: Version, suite, CI, release

- [ ] `app/build.gradle.kts`: `109` → `110`, `"0.55.0"` → `"0.56.0"`.
- [ ] Whole suite (`free -m` first): 0 failures; skipped exactly the ten SQLite classes. Lint exit 0.
  `app/schemas` clean. Anonymisation read of every added line.
- [ ] Commit, push, PR `0.56.0: detailed readings are kept in Drive (D71, phase 3)`; body says what is
  written, when, the never-deleted naming guarantee and its test, what CI must show, the phone checks. No
  session link.
- [ ] CI green with 0 skipped → squash-merge. `free -m` ≥ ~6000 MB and lock free → `~/bin/ms-release`;
  send the APK. **Phone checks:** with Drive backup on, after the next daily backup (or switching Drive
  off and on), the owner's Drive holds `metaself-readings-2026-09.json.gz` (and August if the record
  reaches it); Settings → Movement says the readings are copied to Drive.

## Amended during build (2026-09-26)

- **A month in Drive is never replaced by a thinner one** (controller's decision). When a month has a
  file in Drive and this phone has never written it (`ArchiveRecord.everWritten` false: its
  `archive_months` row has no `writtenAtMillis`), `HealthArchive.writeOutOfDate` downloads Drive's copy
  first and uploads the union — every record the phone has, as it has it, plus every record, keyed by
  (origin, record id), only Drive has. If Drive's copy cannot be downloaded or read, that month is not
  uploaded this time and stays out of date. A month this phone has written before is replaced as
  before. `ArchiveRestore` gains `unreachable` (download failed), counted apart from `unreadable`
  (decode failed), and the restore message says which.
- **The union write also feeds Drive's extra records back to the phone** (controller's decision).
  Uploading the union alone left the next write of that month — now `everWritten` — reading only the
  record and sending the phone's rows alone, so Drive's contribution was lost on the write after this
  one. `HealthArchive.writeOutOfDate` now applies the Drive-only rows to `store` through the same
  grouping `restoreAll` uses (pulled out as `groupIntoRecords`, shared by both), then summarises the
  days they touch with every total marked failed, before uploading. Because that `apply` marks the
  month changed, `asOf` is now taken after the rows are built (previously before), so `markWritten`
  is not immediately stale.
- **Bringing readings back never deletes** (controller's decision, supersedes the two entries above
  where they differ). `ArchiveRecord.insertMissing` inserts Drive's rows as they are (sample index and
  day kept) with `OnConflictStrategy.IGNORE`, so on an (origin, record id, sample index) clash the
  phone's row wins; it never deletes, returns the days actually inserted, and marks their months
  changed. `restoreAll` downloads every month file first, then `insertMissing`s each readable one,
  never calls `store.apply`, never marks a month written, and re-summarises the touched days in a
  `finally`. The union write `insertMissing`s Drive's rows, then takes `asOf`, then uploads
  `readingsIn(month)` — what the phone now holds; the phone path takes `asOf` before reading. The
  grouping into `ReadRecord`s is gone.
- **A failed listing is not "no files"** (controller's decision). `DriveHttp.list` and
  `DriveFiles.readListing` return null for a refused reply or a body that is not an object with a
  `files` array; `DriveBackup` keeps its old behaviour with `.orEmpty()`; `writeOutOfDate` writes
  nothing, and `monthsInDrive` / `restoreAll` return null.
- **One archive run at a time** (controller's decision). A `Mutex` in `HealthArchive`: a write finding
  it held is skipped (returns 0), a restore waits for it.
- **Per-file and per-month isolation** (controller's decision). A download that throws during a
  restore counts that file unreachable; a month that throws during a write is logged and the next is
  tried. Cancellation is rethrown in both.
- **Streaming** (controller's decision). `ReadingArchive.encode` / `decode` stream through gzip with
  kotlinx's `encodeToStream` / `decodeFromStream`, still indented; `encodeTo(OutputStream, …)` added.
- **Months counted, not files** (controller's decision). `monthsInDrive` and the restore's month count
  count distinct months, so two copies of one month are one; the restore's reading count is distinct
  (origin, record id, sample index) keys.
- **A way back besides the offer** (controller's decision). With Drive backup on, Settings → Movement
  has "Bring back detailed readings from Drive"; it asks the same question there
  (`SettingsViewModel.offerArchive`), or says no months were found, or that Drive could not be
  reached, and what came back is said there too. "Not now" is relabelled "No".
- **Minor** (controller's decision). `pageSize=1000` is labelled as this app's choice; long lines split.
