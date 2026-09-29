package com.metaself.app.data.letter

import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.letter.FoodWeek
import com.metaself.app.domain.letter.LetterFigures
import com.metaself.app.domain.letter.LetterTexts
import com.metaself.app.domain.letter.MovementFigures
import com.metaself.app.domain.letter.PlanWeekFigures
import com.metaself.app.domain.letter.WeekFigures
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.domain.movement.MovementWeek

/** The Monday of the week holding [TEST_EPOCH_DAY]. */
val LETTER_MONDAY: Long = MovementWeek.mondayOf(TEST_EPOCH_DAY)

/** A week with every figure present. Every figure is invented and round. */
fun aFullWeek(monday: Long) = WeekFigures(
    monday, FoodWeek(5, 2_000, 100, 200, 70), -0.2, true,
    MovementFigures(3, 120, 9_000, 1, 1, 0, 300, 8_000), PlanWeekFigures("Invented plan", 3, 2, false),
)

/** A week with every figure that can be missing, missing. */
fun anEmptyWeek(monday: Long) = WeekFigures(
    monday, FoodWeek(0, null, null, null, null), null, false,
    MovementFigures(0, 0, null, 0, 0, 0, null, null), null,
)

fun someFigures(monday: Long = LETTER_MONDAY, week: (Long) -> WeekFigures = ::aFullWeek, targetKcal: Int? = 2_100) =
    LetterFigures(week(monday), List(LetterFigures.WEEKS_BEFORE) { week(monday - 7L * (it + 1)) }, targetKcal)

/** An invented letter for [weekMonday]. */
fun aWeeklyLetter(weekMonday: Long = LETTER_MONDAY) = WeeklyLetter(
    weekMonday = weekMonday,
    createdAtMillis = 1_000,
    figures = someFigures(weekMonday),
    texts = LetterTexts("Invented headline.", "Invented.", "Invented.", "Invented.", "Invented.", "Invented."),
    model = "a-model",
)
