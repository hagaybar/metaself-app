package com.metaself.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale — D48. Seven roles, mapped onto all fifteen of Material's slots.
 *
 * A screen names a role and never a size, a weight or a face. Until this file existed the app had
 * 242 calls to `MaterialTheme.typography.*` and no `Typography` of its own, so every one of them
 * resolved to Material's stock default: the biggest number in the app was set in the same face and
 * the same weight as the small print beneath it, and nothing ranked.
 *
 * **All fifteen slots are filled, including the three the app does not call yet.** An empty slot is
 * not empty — it falls back to a default that looks plausible and belongs to no decision, so a
 * screen written next month could reach one by naming a role and nobody would see it happen. The
 * unused ones are filled from the nearest role so that reaching one is a choice rather than an
 * accident.
 *
 * **`fontFeatureSettings = "tnum, lnum"` on every slot that can carry a figure.** `tnum` gives each
 * digit the same advance width, which is why a column of calories lines up and why a number that
 * ticks over does not shuffle the words beside it; `lnum` keeps the digits at cap height rather
 * than letting Fraunces' old-style figures hang below the baseline in a table. This is not a
 * preference — a proportional digit in a column of figures is a defect. The slots that set it are
 * the three display sizes, the two large headlines, `bodySmall` and `labelMedium`; `headlineSmall`
 * deliberately does not, because it sits on the title row and carries a screen's own words.
 *
 * `titleSmall` and `labelSmall` are letterspaced because they are used as small-caps kickers. The
 * upper-casing is done by whoever supplies the words, not here: Compose has no text-transform, and
 * an `uppercase()` buried in shared wording code would mangle Hebrew the day the app gets it (#9).
 */
internal val MetaSelfTypography = Typography(

    // The one number a screen exists to answer. Nothing else is within two steps of it.
    displayLarge = TextStyle(
        fontFamily = Faces.Display,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 72.sp,
        lineHeight = 64.sp,
        fontFeatureSettings = "tnum, lnum",
    ),

    // A secondary answer: a trend, a total.
    displayMedium = TextStyle(
        fontFamily = Faces.Display,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 44.sp,
        lineHeight = 44.sp,
        fontFeatureSettings = "tnum, lnum",
    ),

    // Not in D48's table and not yet called: the step between the two answers above, kept in the
    // display face so that reaching it cannot silently drop a screen back into the text sans.
    displaySmall = TextStyle(
        fontFamily = Faces.Display,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        fontFeatureSettings = "tnum, lnum",
    ),

    // A figure inside a block: a meal's total, a macro column's number.
    headlineLarge = TextStyle(
        fontFamily = Faces.Display,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontFeatureSettings = "tnum, lnum",
    ),

    headlineMedium = TextStyle(
        fontFamily = Faces.Display,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontFeatureSettings = "tnum, lnum",
    ),

    // A screen's own title. Words, not figures — see the note above about `tnum` here.
    headlineSmall = TextStyle(
        fontFamily = Faces.Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),

    titleLarge = TextStyle(
        fontFamily = Faces.Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),

    // A section heading.
    titleMedium = TextStyle(
        fontFamily = Faces.Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),

    // A small-caps kicker over a group. Letterspaced because it is set in capitals by its caller.
    titleSmall = TextStyle(
        fontFamily = Faces.Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.14.em,
    ),

    // A thing on a list: a food's name.
    bodyLarge = TextStyle(
        fontFamily = Faces.Text,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),

    // Ordinary prose.
    bodyMedium = TextStyle(
        fontFamily = Faces.Text,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),

    // A row's figures, a caption. The smallest thing that regularly carries a number.
    bodySmall = TextStyle(
        fontFamily = Faces.Text,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum, lnum",
    ),

    // A button.
    labelLarge = TextStyle(
        fontFamily = Faces.Text,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),

    // A chip, a small figure.
    labelMedium = TextStyle(
        fontFamily = Faces.Text,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum, lnum",
    ),

    // The colophon, an axis label. The floor of the scale: nothing in this app is under 11 sp.
    labelSmall = TextStyle(
        fontFamily = Faces.Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.16.em,
    ),
)
