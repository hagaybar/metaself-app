package com.metaself.app.ui.screen.propose

/** Which way he came in to describe a meal (D58 §1). */
enum class DescribedFrom {
    /** Add something, or its search's *Describe "…" instead*: the describe screen sits on the day. */
    ADD_SOMETHING,

    /** My meals' *Describe a meal*: the describe screen sits on My meals, which sits on the day. */
    MY_MEALS,
}

/** What he chose at the end (D58 §5.2). */
enum class EndChoice { LOG, KEEP, BOTH }

/** Where he lands, and what the back stack does to get there (D58 §5.3). */
enum class Landing {
    /** Back one screen, onto the day. */
    BACK_TO_DAY,

    /** Back past My meals, onto the day. */
    BACK_PAST_MY_MEALS_TO_DAY,

    /** Back one screen, onto My meals, on its meals list. */
    BACK_TO_MY_MEALS,

    /** Back one screen, then on to My meals, opened on its meals list. */
    ON_TO_MY_MEALS,
}

/**
 * **He lands where what was stored is, whichever way he came in** (D58 §5.3): on the day after
 * logging, on My meals after keeping a meal without logging. One rule, so the navigation cannot
 * decide it differently in two places.
 */
object DescribeLanding {

    fun after(from: DescribedFrom, choice: EndChoice): Landing = when (choice) {
        EndChoice.LOG, EndChoice.BOTH -> when (from) {
            DescribedFrom.ADD_SOMETHING -> Landing.BACK_TO_DAY
            DescribedFrom.MY_MEALS -> Landing.BACK_PAST_MY_MEALS_TO_DAY
        }
        EndChoice.KEEP -> when (from) {
            DescribedFrom.ADD_SOMETHING -> Landing.ON_TO_MY_MEALS
            DescribedFrom.MY_MEALS -> Landing.BACK_TO_MY_MEALS
        }
    }

    /**
     * Whether logging goes on today whatever day the day screen was showing: from My meals it does,
     * because nothing there says which day (D58 §12.8); from Add something it goes on the day being
     * looked at, as it always has.
     */
    fun logsOnToday(from: DescribedFrom): Boolean = from == DescribedFrom.MY_MEALS

    /** The route argument's value for [DescribedFrom.MY_MEALS]. */
    const val FROM_MY_MEALS = "meals"

    fun fromArgument(value: String?): DescribedFrom =
        if (value == FROM_MY_MEALS) DescribedFrom.MY_MEALS else DescribedFrom.ADD_SOMETHING
}
