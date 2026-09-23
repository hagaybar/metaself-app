package com.metaself.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The app's colours. Deliberately NOT dynamic (Android's wallpaper-derived palette): a food log is
 * read for its numbers, and a palette that changes with the wallpaper makes "over target" mean a
 * different colour on different phones.
 *
 * The palette itself lives in [MetaSelfLightColours] and [MetaSelfDarkColours], and the type scale
 * in [MetaSelfTypography] — handing the scale to `MaterialTheme` here is what reaches all 242 of
 * the app's `MaterialTheme.typography.*` calls without touching one of them.
 */
@Composable
fun MetaSelfTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        // Ink two has no Material slot, so it travels beside the scheme rather than inside it. It
        // is chosen from `useDarkTheme` — the parameter, not the system setting — because a
        // preview or a test that forces this theme dark must get the dark step, and anything
        // reading `isSystemInDarkTheme()` again further down would hand it the light one.
        LocalMetaSelfInkTwo provides if (useDarkTheme) MetaSelfDarkInkTwo else MetaSelfLightInkTwo,
    ) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) MetaSelfDarkColours else MetaSelfLightColours,
            typography = MetaSelfTypography,
            content = content,
        )
    }
}

/**
 * The steps of the ink ladder that Material's colour scheme has no room for.
 *
 * Three of the four steps are reachable as scheme roles already — `onSurface` is ink one,
 * `onSurfaceVariant` is ink three, `outlineVariant` is the rule — so this object holds only the
 * one that is not. Call sites read `MetaSelfInk.two`; none of them reaches for
 * [MetaSelfLightInkTwo] or [MetaSelfDarkInkTwo] directly, because a raw value at a call site is a
 * colour that cannot follow the scheme.
 */
internal object MetaSelfInk {
    /**
     * Prose that is read rather than scanned: the standing note under a field or a switch that
     * explains what the control does or what will happen. One step darker than a caption, so a
     * paragraph meant to be read outranks the labels around it.
     */
    val two: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalMetaSelfInkTwo.current
}
