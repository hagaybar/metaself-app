package com.metaself.app.data.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import com.metaself.app.domain.health.HealthKind
import kotlin.reflect.KClass

/** Every kind's Health Connect record type and read permission (D66). */
object HealthPermissions {

    fun recordType(kind: HealthKind): KClass<out Record> = when (kind) {
        HealthKind.STEPS -> StepsRecord::class
        HealthKind.DISTANCE -> DistanceRecord::class
        HealthKind.ACTIVE_KCAL -> ActiveCaloriesBurnedRecord::class
        HealthKind.TOTAL_KCAL -> TotalCaloriesBurnedRecord::class
        HealthKind.HEART_RATE -> HeartRateRecord::class
        HealthKind.RESTING_HEART_RATE -> RestingHeartRateRecord::class
        HealthKind.HRV_RMSSD -> HeartRateVariabilityRmssdRecord::class
        HealthKind.OXYGEN_SATURATION -> OxygenSaturationRecord::class
        HealthKind.RESPIRATORY_RATE -> RespiratoryRateRecord::class
        HealthKind.WEIGHT -> WeightRecord::class
        HealthKind.BODY_FAT -> BodyFatRecord::class
        HealthKind.SLEEP -> SleepSessionRecord::class
        HealthKind.EXERCISE -> ExerciseSessionRecord::class
    }

    fun of(kind: HealthKind): String = HealthPermission.getReadPermission(recordType(kind))

    /** What the Connect button asks for: every kind, so a new device needs no app update (D66). */
    val ALL: Set<String> get() = HealthKind.entries.map(::of).toSet()
}
