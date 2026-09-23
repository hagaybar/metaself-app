package com.metaself.app.data.profile

import com.metaself.app.domain.target.TargetRevision

/**
 * The last target revision's storage format, as a pure function both ways.
 *
 * The same shape as [ProfileCodec] and for the same reasons: strings throughout, because they are
 * locale-independent and legible in a backup; and an unreadable record decodes to null, so the app
 * behaves as though no revision had happened rather than crashing or inventing one.
 *
 * A missing revision is recoverable — the next weekly revision simply happens a week later than it
 * would have. That is a far better failure than a target the owner cannot account for.
 */
object TargetRevisionCodec {

    const val KEY_EPOCH_DAY = "revision_epoch_day"
    const val KEY_TREND_KG = "revision_trend_kg"
    const val KEY_KCAL = "revision_kcal"
    const val KEY_PREVIOUS_KCAL = "revision_previous_kcal"

    /** Whether the owner has seen the notice for the revision now stored. */
    const val KEY_SEEN = "revision_seen"

    /** What "there was nothing before this" is written as, since a key cannot hold null. */
    private const val NONE = "none"

    fun encode(revision: TargetRevision): Map<String, String> = mapOf(
        KEY_EPOCH_DAY to revision.epochDay.toString(),
        KEY_TREND_KG to revision.trendKg.toString(),
        KEY_KCAL to revision.kcal.toString(),
        KEY_PREVIOUS_KCAL to (revision.previousKcal?.toString() ?: NONE),
    )

    fun decode(values: Map<String, String>): TargetRevision? {
        val epochDay = values[KEY_EPOCH_DAY]?.toLongOrNull() ?: return null
        val trendKg = values[KEY_TREND_KG]?.toDoubleOrNull() ?: return null
        val kcal = values[KEY_KCAL]?.toIntOrNull() ?: return null
        val previousRaw = values[KEY_PREVIOUS_KCAL] ?: return null
        val previousKcal =
            if (previousRaw == NONE) null else previousRaw.toIntOrNull() ?: return null

        return TargetRevision(
            epochDay = epochDay,
            trendKg = trendKg,
            kcal = kcal,
            previousKcal = previousKcal,
        )
    }
}
