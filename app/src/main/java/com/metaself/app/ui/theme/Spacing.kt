package com.metaself.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The only four gaps this app uses, and what each one means.
 *
 * Before this, almost every screen spaced everything by 16 — so the gap between a heading and its
 * own content was the same as the gap between two unrelated sections, and nothing grouped. Naming
 * them is what stops a ninth screen inventing a fifth.
 */
object Spacing {

    /** Inside one thing: a label above its value, a caption under a card. */
    val Tight = 4.dp

    /** Between things that belong together: the rows of a list, buttons in a row. */
    val Related = 8.dp

    /** Between sections that do not: the target and the day's list. */
    val Section = 16.dp

    /** Around the edge of a screen. */
    val Screen = 24.dp
}
