package com.metaself.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The whole palette, derived from the green this app has had since step 1.
 *
 * Before this, only `primary` and `onPrimary` were set and every other role kept Material's
 * defaults, which are derived from a purple. Green buttons sat beside grey-purple surfaces and a
 * secondary nobody had chosen: nothing was wrong and nothing agreed.
 *
 * **The error red is left red.** It carries one meaning — something the owner has to put right:
 * a refusal, or a field that is wrong (D48) — and harmonising it into the green would take that
 * meaning away. Over target is not one of them and is drawn in ink. Everything else is a green or a
 * near-neutral warmed towards it.
 */

// The green, and the family around it.
private val Green40 = Color(0xFF2E7D5B)
private val Green30 = Color(0xFF1B5E44)
private val Green90 = Color(0xFFB6EDD2)
private val Green80 = Color(0xFF7FD1A8)

/*
 * The ink ladder — D48.
 *
 * **The ladder exists because one grey was doing every job, so nothing ranked. It does not exist
 * because anything failed contrast.** Brand names, aliases, row figures and footnotes were all the
 * same secondary, so finding a food meant reading the screen instead of scanning it. The small
 * print was always perfectly legible: the caption colour this ladder inherits, `#414942`, measures
 * 9.09:1 on the light page, and its dark twin `#C0C9C1` measures 10.92:1 — both far above the 4.5:1
 * WCAG AA floor for text.
 *
 * That is worth stating plainly because the first draft of this ladder asserted the opposite: that
 * the old grey measured 3.9:1 and failed AA. It was a fabricated number — no colour in this palette
 * was ever near it. Built on it, that draft lightened every step until dark captions on a card
 * reached 3.47:1, which is a real AA failure the app did not have before. Ranking is bought by
 * darkening the steps ABOVE the caption, never by lightening the caption itself.
 *
 * Four steps, and no fifth. Every text step is measured against both backgrounds it can land on —
 * the page, and `surfaceVariant`, which is a real card background here — in both schemes, by
 * `InkLadderTest`. The test computes the ratios rather than trusting this file, because a contrast
 * figure asserted rather than measured is precisely what went wrong the first time. This is D4
 * turned on the app's own source: a number that was never measured must not be written as one.
 */

/** Anything that is an answer: names, figures, headings. 17.17:1 on the page, 13.64:1 on a card. */
internal val MetaSelfLightInk = Color(0xFF161A17)

/**
 * Prose that is read but not scanned. 12.84:1 on the light page, 10.20:1 on a card.
 *
 * **Wired to no Material slot, deliberately.** It is the step D49's margin notes and running prose
 * take, and it is defined here so that the ladder is whole and step two does not invent a fifth
 * grey when it needs one. It is not an oversight and it is not dead — do not tidy it away.
 */
internal val MetaSelfLightInkTwo = Color(0xFF2B322D)

/**
 * Captions, origins, the colophon. 9.09:1 on the light page, 7.22:1 on a card.
 *
 * **This is the value the app already had** for `onSurfaceVariant`, kept on purpose. The caption
 * step was never the problem and had nothing to gain by moving; what it lacked was two darker steps
 * ranked above it, which is what the rest of this ladder supplies.
 */
internal val MetaSelfLightInkThree = Color(0xFF414942)

/** Hairlines and dividers. Never text, so no contrast floor applies to it. */
internal val MetaSelfLightRule = Color(0xFFDAE0D8)

/** Anything that is an answer: names, figures, headings. 15.44:1 on the page, 7.75:1 on a card. */
internal val MetaSelfDarkInk = Color(0xFFE9EBE6)

/** Prose that is read but not scanned; wired to no slot, as its light twin is. 13.17:1 / 6.61:1. */
internal val MetaSelfDarkInkTwo = Color(0xFFD5DBD4)

/**
 * Captions, origins, the colophon. 10.92:1 on the dark page, 5.48:1 on a card.
 *
 * Unchanged from what the app already drew captions in, for the same reason as its light twin. A
 * lighter draft of this step put it at 3.47:1 on a card — below the AA floor, on the one background
 * the earlier test never checked. That is why `InkLadderTest` now measures cards as well as pages.
 */
internal val MetaSelfDarkInkThree = Color(0xFFC0C9C1)

/** Hairlines and dividers. Never text. */
internal val MetaSelfDarkRule = Color(0xFF333A34)

internal val MetaSelfLightColours = lightColorScheme(
    primary = Green40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Green90,
    onPrimaryContainer = Color(0xFF00210F),

    // A muted green-grey: present enough to group things, quiet enough not to compete with the
    // numbers, which are what this app is read for.
    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8D7),
    onSecondaryContainer = Color(0xFF0C1F15),

    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFEAF8),
    onTertiaryContainer = Color(0xFF001F27),

    background = Color(0xFFFBFDF8),
    onBackground = MetaSelfLightInk,
    surface = Color(0xFFFBFDF8),
    // The page and the cards on it must not drift into two different blacks.
    onSurface = MetaSelfLightInk,
    surfaceVariant = Color(0xFFDCE5DC),
    // Every caption and origin line in the app reads this one. It is the step that had to move.
    onSurfaceVariant = MetaSelfLightInkThree,
    // The two below are one letter apart and the app draws both, and NEITHER is ever text.
    // `outline` is a BORDER — the edge of a field or of something interactive, held to the 3:1
    // floor for non-text rather than the 4.5:1 for words. `outlineVariant` is a HAIRLINE — a
    // divider. A word drawn in either one is a bug; the ink ladder above is where text comes from.
    outline = Color(0xFF717972),
    outlineVariant = MetaSelfLightRule,

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

internal val MetaSelfDarkColours = darkColorScheme(
    primary = Green80,
    onPrimary = Color(0xFF003822),
    primaryContainer = Green30,
    onPrimaryContainer = Green90,

    secondary = Color(0xFFB5CCBB),
    onSecondary = Color(0xFF213529),
    secondaryContainer = Color(0xFF374B3F),
    onSecondaryContainer = Color(0xFFD1E8D7),

    tertiary = Color(0xFFA3CDDC),
    onTertiary = Color(0xFF033541),
    tertiaryContainer = Color(0xFF224C58),
    onTertiaryContainer = Color(0xFFBFEAF8),

    background = Color(0xFF111412),
    onBackground = MetaSelfDarkInk,
    surface = Color(0xFF111412),
    onSurface = MetaSelfDarkInk,
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = MetaSelfDarkInkThree,
    // As in the light scheme: `outline` is a border, `outlineVariant` is a hairline, neither is text.
    outline = Color(0xFF8A938B),
    outlineVariant = MetaSelfDarkRule,

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * The ink ladder's second step, which no Material slot carries — see [MetaSelfLightInkTwo].
 *
 * It is a composition local rather than a colour-scheme role because Material has no slot for
 * "prose", and inventing one by borrowing an unrelated role is how `secondary` — a brand colour —
 * came to draw every caption in this app in the first place.
 *
 * **Read it through [MetaSelfInk], never here.** The default below is the light value, so a
 * composable drawn outside [MetaSelfTheme] gets the light step; everything in this app is inside
 * the theme, and [MetaSelfTheme] is what supplies the right one for the scheme in force.
 */
internal val LocalMetaSelfInkTwo = staticCompositionLocalOf { MetaSelfLightInkTwo }
