package com.metaself.app.data.letter

import com.metaself.app.domain.letter.FoodWeek
import com.metaself.app.domain.letter.LetterFigures
import com.metaself.app.domain.letter.MovementFigures
import com.metaself.app.domain.letter.PlanWeekFigures
import com.metaself.app.domain.letter.WeekFigures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A letter's figures as stored (D104): this week and the four before, as counted on the phone. The shape
 * is this file's own, so a change to the domain classes cannot silently change what is stored. Reading
 * anything that is not that shape gives null, never a throw.
 */
object LetterCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(figures: LetterFigures): String = json.encodeToString(Figures.serializer(), figures.toDto())

    fun read(text: String): LetterFigures? = runCatching { json.decodeFromString(Figures.serializer(), text).toDomain() }.getOrNull()

    @Serializable
    private data class Figures(
        val week: Week,
        val earlier: List<Week>,
        @SerialName("target_kcal") val targetKcal: Int?,
    )

    @Serializable
    private data class Week(
        val monday: Long,
        val food: Food,
        @SerialName("weight_change_kg") val weightChangeKg: Double?,
        @SerialName("weighed_in") val weighedIn: Boolean,
        val movement: Movement,
        val plan: Plan?,
    )

    @Serializable
    private data class Food(
        @SerialName("days_logged") val daysLogged: Int,
        val kcal: Int?,
        @SerialName("protein_g") val proteinG: Int?,
        @SerialName("carbs_g") val carbsG: Int?,
        @SerialName("fat_g") val fatG: Int?,
    )

    @Serializable
    private data class Movement(
        val sessions: Int,
        val minutes: Int,
        @SerialName("distance_m") val distanceM: Int?,
        val easy: Int,
        val right: Int,
        val hard: Int,
        @SerialName("active_kcal_a_day") val activeKcalADay: Int?,
        @SerialName("steps_a_day") val stepsADay: Int?,
    )

    @Serializable
    private data class Plan(val title: String, val planned: Int, val done: Int, val ended: Boolean)

    private fun LetterFigures.toDto() = Figures(week.toDto(), earlier.map { it.toDto() }, targetKcal)

    private fun Figures.toDomain() = LetterFigures(week.toDomain(), earlier.map { it.toDomain() }, targetKcal)

    private fun WeekFigures.toDto() = Week(
        monday = monday,
        food = Food(food.daysLogged, food.kcal, food.proteinG, food.carbsG, food.fatG),
        weightChangeKg = weightChangeKg,
        weighedIn = weighedIn,
        movement = with(movement) { Movement(sessions, minutes, distanceM, easy, right, hard, activeKcalADay, stepsADay) },
        plan = plan?.let { Plan(it.title, it.planned, it.done, it.ended) },
    )

    private fun Week.toDomain() = WeekFigures(
        monday = monday,
        food = FoodWeek(food.daysLogged, food.kcal, food.proteinG, food.carbsG, food.fatG),
        weightChangeKg = weightChangeKg,
        weighedIn = weighedIn,
        movement = with(movement) { MovementFigures(sessions, minutes, distanceM, easy, right, hard, activeKcalADay, stepsADay) },
        plan = plan?.let { PlanWeekFigures(it.title, it.planned, it.done, it.ended) },
    )
}
