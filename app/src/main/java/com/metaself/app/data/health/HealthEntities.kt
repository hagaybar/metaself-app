package com.metaself.app.data.health

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One simple reading as Health Connect held it (D68): a value at a moment, or over a span.
 *
 * A series record — heart rate — is one Health Connect record of many samples, stored as one row per
 * sample under the same [recordId], told apart by [sampleIndex]. `(origin, recordId, sampleIndex)`
 * is unique, so the same sample can never be stored twice. Two apps recording the same walk both
 * keep their rows: that is what was recorded. De-duplication is the daily summary's job (D69).
 *
 * @property kind STEPS, DISTANCE, ACTIVE_KCAL, TOTAL_KCAL, HEART_RATE, RESTING_HEART_RATE,
 *   HRV_RMSSD, OXYGEN_SATURATION, RESPIRATORY_RATE, WEIGHT, BODY_FAT — a name, never an ordinal.
 * @property endMillis null for an instant (a heart-rate sample, a weight); the span's end otherwise.
 * @property unit "count", "m", "kcal", "bpm", "ms", "%", "breaths/min", "kg".
 * @property epochDay the local day of [startMillis], stored so that a day is one indexed range.
 */
@Entity(
    tableName = "health_readings",
    indices = [
        Index(value = ["kind", "epochDay", "startMillis"]),
        Index(value = ["origin", "recordId", "sampleIndex"], unique = true),
    ],
)
data class HealthReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val startMillis: Long,
    val endMillis: Long?,
    val value: Double,
    val unit: String,
    val origin: String,
    val recordId: String,
    val sampleIndex: Int = 0,
    val epochDay: Long,
)

/**
 * One workout, whether the band recorded it or the owner typed it (D59, extended by D65).
 *
 * [origin] and [originId] are Health Connect's data origin and record id; together unique, so a
 * re-read finds the row rather than adding a second. Both null for a typed workout — SQLite lets any
 * number of rows share a null in a unique index.
 *
 * [energyKcal] carries where it came from in [energySource] (D4). The heart-rate figures are worked
 * out by this app from the readings inside the session (phase 2); [zoneSeconds] is five
 * comma-separated totals, zone 1 to 5, and [zoneMaxSource] says what maximum they were measured
 * against — ESTIMATED until the trainer offers an observed one (D70). [note] is where the trainer's
 * notebook will keep the owner's words.
 */
@Entity(
    tableName = "workouts",
    indices = [
        Index("epochDay"),
        Index(value = ["origin", "originId"], unique = true),
    ],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startedAtMillis: Long,
    val durationMinutes: Int,
    /** RUN, WALK, CYCLE, SWIM, STRENGTH, OTHER. */
    val kind: String,
    val title: String?,
    val distanceM: Int?,
    val energyKcal: Int?,
    /** BAND, MET_ESTIMATE, TYPED, NONE. */
    val energySource: String,
    /** EASY, MODERATE, HARD. A typed workout always has one; a synced one never. */
    val effort: String?,
    /** SYNCED or TYPED. */
    val source: String,
    val origin: String?,
    val originId: String?,
    /** Set when the owner says a session is not real; kept, so the next sync cannot bring it back. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    val note: String?,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val zoneSeconds: String? = null,
    val zoneMaxSource: String? = null,
)

/** One night (D68): it belongs to [epochDay], the day he woke up. */
@Entity(
    tableName = "sleep_sessions",
    indices = [
        Index("epochDay"),
        Index(value = ["origin", "recordId"], unique = true),
    ],
)
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startMillis: Long,
    val endMillis: Long,
    val origin: String,
    val recordId: String,
    val title: String?,
)

/** A stretch of one night. Deleting the night deletes its stages. */
@Entity(
    tableName = "sleep_stages",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
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

/**
 * One day of the health record, summarised (D69).
 *
 * Every figure is nullable: no data is not zero. Each figure's source is TOTAL (Health Connect's
 * de-duplicated aggregate), READ (one record), COMPUTED (worked out here) or CORRECTED (the owner's
 * figure, D12d). The five sleep figures share [sleepSource] and the two workout figures share
 * [workoutSource], because each group is computed together.
 */
@Entity(tableName = "health_days")
data class HealthDayEntity(
    @PrimaryKey val epochDay: Long,
    val computedAtMillis: Long,
    val steps: Int? = null,
    val stepsSource: String? = null,
    val distanceM: Int? = null,
    val distanceSource: String? = null,
    val activeKcal: Int? = null,
    val activeKcalSource: String? = null,
    val totalKcal: Int? = null,
    val totalKcalSource: String? = null,
    val restingHeartRate: Int? = null,
    val restingHeartRateSource: String? = null,
    val avgHeartRate: Int? = null,
    val avgHeartRateSource: String? = null,
    val hrvMs: Double? = null,
    val hrvSource: String? = null,
    val oxygenPct: Double? = null,
    val oxygenSource: String? = null,
    val respiratoryRate: Double? = null,
    val respiratoryRateSource: String? = null,
    val sleepMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val remMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val sleepSource: String? = null,
    val workoutCount: Int? = null,
    val workoutMinutes: Int? = null,
    val workoutSource: String? = null,
)

/**
 * The owner's own figure for a day's movement, replacing what Health Connect read (D12d).
 * One row per day; a null column leaves that reading alone. Never written back to Health Connect.
 */
@Entity(tableName = "movement_corrections")
data class MovementCorrectionEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Int?,
    val activeKcal: Int?,
    val setAtMillis: Long,
    val note: String?,
)

/**
 * Where copying one kind stands (D67). Belongs to this phone's Health Connect, so it is never
 * backed up: a restored phone re-reads its window.
 */
@Entity(tableName = "health_sync")
data class HealthSyncEntity(
    @PrimaryKey val kind: String,
    val changesToken: String? = null,
    val tokenAtMillis: Long? = null,
    /** How far back the first copy has reached; it runs newest first. */
    val catchUpCursorMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val catchUpDone: Boolean = false,
)

/** Which Drive month files are out of date (D71). Never backed up. `month` is "2026-09". */
@Entity(tableName = "archive_months")
data class ArchiveMonthEntity(
    @PrimaryKey val month: String,
    val changedAtMillis: Long,
    val writtenAtMillis: Long? = null,
)

/**
 * One Monday-to-Sunday week of running. [week] is `(epochDay + 3) / 7` — epoch day 0 was a
 * Thursday, so this whole number changes on Mondays.
 */
data class WeekOfRunning(val week: Long, val metres: Int, val runs: Int)
