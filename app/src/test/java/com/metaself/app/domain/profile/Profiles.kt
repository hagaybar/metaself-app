package com.metaself.app.domain.profile

/**
 * A profile with plausible defaults, for tests that care about one field at a time.
 *
 * The defaults are a 46-year-old man of 80 kg and 180 cm, moderately active, losing half a kilogram
 * a week — chosen because every intermediate number that body produces is a round one, which makes
 * a failing assertion readable.
 */
fun aProfile(
    heightCm: Int = 180,
    birthYear: Int = 1980,
    sex: Sex = Sex.MALE,
    weightKg: Double = 80.0,
    activity: ActivityLevel = ActivityLevel.MODERATE,
    goal: Goal = Goal.lose(0.5),
    allowBelowFloor: Boolean = false,
): Profile = Profile(
    heightCm = heightCm,
    birthYear = birthYear,
    sex = sex,
    weightKg = weightKg,
    activity = activity,
    goal = goal,
    allowBelowFloor = allowBelowFloor,
)

/** The year every test computes ages against, so no test changes answer on New Year's Day. */
const val TEST_YEAR = 2026
