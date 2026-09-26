# The health record — D65 to D72

> Written 2026-09-26 from a design conversation with the owner, who approved each section as it was
> presented. Nothing here is built.
>
> **Every figure in a worked example is invented** and chosen to keep the arithmetic round.

---

## 0. Why, and what it replaces

The owner's aim is a personalised trainer: recommendations that know how active he actually is,
help choosing useful activities, and his own words about his workouts kept beside the numbers. That
is three projects, built in order:

1. **The health record** — this document. Everything Health Connect holds that the owner approves is
   copied into the app's own database and kept.
2. **The trainer's notebook** — free-text feedback about workouts, stored with them. Its own spec.
3. **The trainer** — recommendations reasoning across food, weight and the health record together.
   Its own spec; that is where the question of sending health data to a model is decided (D16).

The record comes first because the other two stand on it.

**What this supersedes.** D59 (2026-09-25) said *workouts are stored; daily totals are not*. The
second half is reversed by D65: daily totals are stored, and so are the raw readings under them.
D59's first half stands, and the `workouts` table designed in
`2026-09-25-physical-activity-module-design.md` §3.2 is kept and extended here. D60 to D64 are
untouched. That spec's phases 2 and 3 (the store, the sync) are replaced by this document's phases
1 to 3; its phases 4 to 6 follow on top of this record. D12's credit arithmetic is unchanged and
keeps reading the live aggregate (§7).

---

## 1. Decisions

**D65 — The app keeps a health record.** Every kind of data in D66, from every app that writes it,
is copied into the local database and kept for ever: raw readings, and a summary per day computed
from them. Size is not a reason to refuse: a reading a minute for a year is about half a million
rows, tens of megabytes (an estimate from row count × a few dozen bytes; to be measured once real
data exists).

**D66 — The app asks for every kind that could serve the trainer.** Chosen by the owner 2026-09-26
over "only what the band is known to write", so that a new device never needs an app update. Kinds
with no writer stay empty. The read permissions, all present in the pinned `1.1.0-alpha07` client:

| Kind | Health Connect record | Stored in |
|---|---|---|
| Steps | `StepsRecord` | readings, summary |
| Distance | `DistanceRecord` | readings, summary |
| Active calories | `ActiveCaloriesBurnedRecord` | readings, summary |
| Total calories | `TotalCaloriesBurnedRecord` | readings, summary |
| Heart rate | `HeartRateRecord` (a series of samples) | readings, summary, workouts |
| Resting heart rate | `RestingHeartRateRecord` | readings, summary |
| Heart-rate variability | `HeartRateVariabilityRmssdRecord` | readings, summary |
| Blood oxygen | `OxygenSaturationRecord` | readings, summary |
| Breathing rate | `RespiratoryRateRecord` | readings, summary |
| Weight | `WeightRecord` | readings only (§7) |
| Body fat | `BodyFatRecord` | readings only |
| Workouts | `ExerciseSessionRecord` | workouts |
| Sleep | `SleepSessionRecord` (with stages) | sleep, summary |

Three are already granted on a connected phone (steps, active calories, exercise); the other ten are
new, and the phone shows them as off until the owner taps Connect again. Settings says so.

**D67 — Copying happens on open and on refresh, only what changed, newest first.**

- The trigger is the one the app already has: every open and every pull-to-refresh. No background
  work (unchanged; `READ_HEALTH_DATA_IN_BACKGROUND` is not requested).
- Per kind, the app keeps a Health Connect **changes token**. Each copy asks for the changes since
  it: an upsertion replaces that record's rows, a deletion removes them. Edits and deletions made in
  the writing app therefore reach this one.
- A token expires after about 30 days. An expired token, or none, means **re-read the window**:
  every record of that kind from the start of the window to now, reconciled against what is stored.
- **The first copy runs newest first**, so the recent weeks the trainer needs most arrive first,
  and it is spread over as many opens as it takes. It stops quietly when Health Connect refuses a
  read for quota, and resumes from its cursor next time. It never blocks the screen.
- **How far back:** the documentation says an app may read data from 30 days before any permission
  was first granted, onward. The first copy starts there. This reading of the documentation is to be
  **measured on the phone** in phase 2 (does a read of day −45 succeed?), not trusted.
- The sync **never throws upwards** (D8): a failure is a `ProblemLog` entry of kind `"health"`.

**D68 — Structured things get their own tables; simple readings share one.** Chosen by the owner
2026-09-26 from three layouts. Workouts, sleep and the daily summary have structure (stages, zones,
per-figure sources) and get tables. Everything that is a value at a time or over a span shares
`health_readings`, so a new simple kind costs a line of code and no migration.

- **A day is local midnight to midnight**, as meals are.
- **A night belongs to the day the owner wakes up.**

**D69 — The daily summary uses Health Connect's own de-duplicated totals, and every figure says
where it came from.** Two apps recording the same walk both keep their raw rows (that is what was
recorded), but the summary's steps, distance and calories come from the aggregation API, which
removes the overlap by the owner's priority list — D12c's rule, extended. Each figure carries its
source (D4): `TOTAL` (Health Connect's de-duplicated aggregate), `READ` (a single record, e.g. the
band's resting heart rate), `COMPUTED` (worked out by this app from readings, e.g. average heart
rate), or `CORRECTED` (the owner's figure, D12d). A day's summary is recomputed whenever that day's
readings change. A correction always wins.

**D70 — Heart-rate zones use an estimated maximum until a better one exists.** Maximum heart rate is
`220 − age`, age from the profile's birth year, and is always labelled *estimated*. Zones are the
common five bands at 50–60, 60–70, 70–80, 80–90 and 90–100 % of that maximum. Nothing asks the owner
for a figure. An *observed* maximum ("the highest seen in your workouts") offered in its place is a
trainer feature, deferred to project 3.

**D71 — The daily backup carries the structured record; the raw readings go to Drive by month.**

- Backup format **version 3** adds workouts, sleep with stages, daily summaries and corrections.
  Small: a handful of rows a day. Same file, same 14-file rotation, same destinations.
- Raw readings are written **only to Drive**, one gzip-compressed JSON file per calendar month,
  through the existing `drive.file` access. The current month's file is rewritten with each daily
  backup; a closed month is rewritten only when its readings change. **Month files are never
  deleted.** Unzipped, each is readable like the daily file.
- A restore reads the daily file as now, then offers the month files it finds on Drive: *"Also bring
  back 14 months of detailed readings from Drive?"* (invented count).
- With Drive off, raw readings are not backed up. The summaries are. Settings says this in one line
  when Drive is off.
- The changes tokens and catch-up cursors are **not** backed up: they belong to the phone's Health
  Connect, not the file. A restored phone re-reads its window.

**D72 — Older history waits for the toolchain upgrade.** `READ_HEALTH_DATA_HISTORY` lifts the
30-day limit and arrived in `1.1.0-alpha10`, which needs compileSdk 35; the stable client needs
compileSdk 36 and AGP 8.9.1 (measured 2026-09-25, the activity spec §1.1). The upgrade is its own
phase, last, with nothing else riding on it — D64 stands. Once it lands, the first copy is simply
re-run with an earlier start.

---

## 2. Data model — schema version 6

One migration, 5 → 6, creates every table below and touches nothing that exists. Conventions as
before: `epochDay` as `Long`, moments as `…Millis`, enumerations as strings, never ordinals.

### 2.1 `health_readings` — the shared table

```kotlin
@Entity(
    tableName = "health_readings",
    indices = [
        Index(value = ["kind", "startMillis"]),
        Index(value = ["origin", "recordId", "sampleIndex"], unique = true),
    ],
)
data class HealthReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** STEPS, DISTANCE, ACTIVE_KCAL, TOTAL_KCAL, HEART_RATE, RESTING_HEART_RATE, HRV_RMSSD,
     *  OXYGEN_SATURATION, RESPIRATORY_RATE, WEIGHT, BODY_FAT. */
    val kind: String,
    val startMillis: Long,
    /** Null for an instant (a heart-rate sample, a weight); the span's end otherwise (steps). */
    val endMillis: Long?,
    val value: Double,
    /** "count", "m", "kcal", "bpm", "ms", "%", "breaths/min", "kg". */
    val unit: String,
    /** The writing app's package. */
    val origin: String,
    /** Health Connect's record id. */
    val recordId: String,
    /** Position within a series record (heart rate); 0 for a single-value record. */
    val sampleIndex: Int = 0,
    /** Local day of [startMillis], stored so a day's rows are one indexed range. */
    val epochDay: Long,
)
```

A heart-rate record is one Health Connect record holding many samples; it becomes one row per
sample under the same `recordId`. An upsertion change deletes every row with that `(origin,
recordId)` and inserts the new ones, in one transaction; a deletion change deletes them.

### 2.2 `workouts` — as in the activity spec §3.2, plus

| Column | Type | Meaning |
|---|---|---|
| `avgHeartRate` | `Int?` | computed from the readings inside the session (`COMPUTED`) |
| `maxHeartRate` | `Int?` | likewise |
| `zoneSeconds` | `String?` | five integers, comma-separated, zone 1 to 5 (D70) |
| `zoneMaxSource` | `String?` | `ESTIMATED` until project 3 offers an observed maximum |

The existing `note` column is where project 2's feedback will live; this project writes nothing to it.

### 2.3 `sleep_sessions` and `sleep_stages`

```kotlin
@Entity(
    tableName = "sleep_sessions",
    indices = [Index("epochDay"), Index(value = ["origin", "recordId"], unique = true)],
)
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The day he woke up (D68). */
    val epochDay: Long,
    val startMillis: Long,
    val endMillis: Long,
    val origin: String,
    val recordId: String,
    val title: String?,
)

@Entity(
    tableName = "sleep_stages",
    foreignKeys = [ForeignKey(entity = SleepSessionEntity::class, parentColumns = ["id"],
        childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class SleepStageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** AWAKE, LIGHT, DEEP, REM, SLEEPING, OUT_OF_BED, AWAKE_IN_BED, UNKNOWN. */
    val stage: String,
    val startMillis: Long,
    val endMillis: Long,
)
```

### 2.4 `health_days` — the daily summary

One row per day. Every figure is nullable (no data is not zero) and has a source column beside it
holding `TOTAL`, `READ`, `COMPUTED` or `CORRECTED` (D69).

| Figure | Source rule |
|---|---|
| `steps`, `distanceM`, `activeKcal`, `totalKcal` | `TOTAL` — the aggregation API over the day |
| `restingHeartRate` | `READ` — the day's `RestingHeartRateRecord`; never computed here |
| `avgHeartRate`, `hrvMs`, `oxygenPct`, `respiratoryRate` | `COMPUTED` — mean of the day's readings |
| `sleepMinutes`, `deepMinutes`, `lightMinutes`, `remMinutes`, `awakeMinutes` | `COMPUTED` from the night's stages |
| `workoutCount`, `workoutMinutes` | `COMPUTED` from visible workouts |

Plus `computedAtMillis`. A correction (§2.5) replaces `steps` or `activeKcal` and sets its source to
`CORRECTED`.

### 2.5 `movement_corrections` — as in the activity spec §3.2, unchanged.

### 2.6 Bookkeeping — not backed up

- `health_sync(kind PRIMARY KEY, changesToken TEXT?, tokenAtMillis, catchUpCursorMillis?,
  catchUpDone INTEGER)` — per kind, where copying stands.
- `archive_months(month TEXT PRIMARY KEY, changedAtMillis, writtenAtMillis?)` — which Drive month
  files are out of date.

---

## 3. Components

```
data/health/
  HealthConnectReader     reads one kind: changes since a token, or records in a window  (thin)
  HealthRecordSync        per kind: token or window → HealthChanges → store; quota → stop
  HealthRecordStore       Room: readings, sleep, workouts, days; one transaction per batch
  HealthArchive           month files to and from Drive, through the existing DriveBackup client
domain/health/
  HealthChanges           pure: records + existing rows → rows to insert, rows to delete
  DaySummary              pure: a day's readings, sleep, workouts, correction → one health_days row
  HeartRateZones          pure: max (estimated from birth year) + samples in a session → five totals
  ReadingKind, Stage, FigureSource   enums, stored as names
```

The Health Connect reader stays thin enough that a fake would only test the fake, as
`HealthConnectSteps` is today; the logic lives in the three pure pieces and is tested there.

**Flow, per open:** for each granted kind, `HealthRecordSync` asks the reader for changes (or a
window, or the next catch-up slice, newest first), hands them to `HealthChanges`, writes the result,
marks the touched days, and moves the token or cursor. After all kinds, every touched day is
recomputed by `DaySummary` (the four totals by one aggregation call per touched day) and every
touched month is marked for the archive. The daily backup then runs as it does now, and writes the
out-of-date month files if Drive is on.

---

## 4. What the owner sees

- **After the update:** Android's permission screen, once, with the ten new kinds.
- **Settings → Movement:** one status line. *"Health record: 52 days, from 6 August · last copied 2
  minutes ago · still catching up"* (invented). When kinds are switched off: *"Heart rate and sleep
  are not allowed — tap Connect to allow them."* When Drive is off: *"Detailed readings are not
  backed up while Drive is off."*
- **Everywhere else: nothing new.** Showing the record is the Movement screen's job (activity spec
  phase 5, now standing on this record) and the trainer's.

---

## 5. Roadmap

| Phase | Builds | Done when |
|---|---|---|
| **1. The store** | schema 6 (every table in §2), `MIGRATION_5_6` + Python check, the DAOs, backup format 3 with the restore question naming workouts | migration and DAO tests pass in CI; a backup round-trips workouts, sleep and summaries |
| **2. The copying** | the ten permissions, `HealthConnectReader`, `HealthRecordSync`, `HealthChanges`, `DaySummary`, `HeartRateZones`, the Settings line; **measure the history limit** on the phone | after an open, the phone's recent days are in the tables; an edit made in the band's app shows after the next open; nothing is counted twice |
| **3. The Drive archive** | `HealthArchive`, month files, the restore offer, the "not backed up" line | a restored phone gets its readings back from Drive |
| **4. Older history** | the toolchain upgrade (AGP 8.9.1+, Gradle 8.11.1+, compileSdk 36, Health Connect 1.1.0), `READ_HEALTH_DATA_HISTORY`, the first copy re-run from further back | days before the first grant appear |

Then the activity spec's phases 4 to 6 (log by hand, the Movement screen, corrections), then
projects 2 and 3. Each phase is a minor version, release-signed via `~/bin/ms-release`, checked on
the phone before the next starts.

---

## 6. Testing

- **Pure, runs anywhere (JUnit 5 + Truth):** `HealthChangesTest` — an upsertion replaces a record's
  rows, a deletion removes them, a heart-rate series becomes one row per sample, a re-read of the
  same window changes nothing. `DaySummaryTest` — every figure's source; no data is null, not zero; a
  correction wins; a night lands on the waking day, including across midnight and a clock change.
  `HeartRateZonesTest` — the standard body's estimated maximum, samples on zone boundaries.
- **Database, CI only (Robolectric, `assumeSqliteRuntime()`):** the DAO tests, `MigrationTest` 5 → 6,
  `BackupRoundTripTest` for the new blocks. These join the list of classes that skip locally.
- **Migration, locally:** `tools/check-migration-5-6.py`, extracting the SQL and comparing it with
  the exported `6.json`.
- **Anonymisation:** fixtures are invented and say so; no real heart rate, sleep length, step count,
  resting rate or frequency anywhere, test names included. Health data is the most personal data the
  repository has yet had near it; the reading pass before every merge is not optional.

---

## 7. Not built, and why

- **Weight from Health Connect does not enter the weight log.** The log is the owner's own
  weighings and drives the trend; a scale's readings are stored as readings, and whether they should
  feed the trend is a decision for when a scale exists.
- **D12's credit keeps reading the live aggregate.** Moving it onto `health_days` would change a
  working calculation for no visible gain; it is a candidate once the record has proved itself.
- **No background copying.** On open is enough for a record that is read on open.
- **No routes, cadence, power, elevation, nutrition or hydration records.** Nothing would use them;
  food has its own log.
- **Nothing leaves the phone except to the owner's own Drive.** Sending any of this to a model is
  project 3's decision (D16).
- **No observed maximum heart rate, no feedback, no recommendations.** Projects 2 and 3.
