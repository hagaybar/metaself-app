package com.metaself.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.metaself.app.R

/**
 * The two faces the app is set in, and nothing else: a display serif for the figures that answer a
 * question, and a text sans for every word. D48.
 *
 * **Static instances rather than the variable fonts**, though both families ship one and `minSdk 26`
 * would carry it. A variable font reaches a weight only through `FontVariation.Settings` supplied
 * where the text is drawn, which makes the weight a per-use decision at every call site — exactly
 * the thing D48 exists to remove. Five files are smaller than that argument, and they are bundled
 * rather than downloaded because this app is offline-first: a font provider would need Play
 * Services, a first-run fetch, and a fallback for the fetch failing, which is three new ways for the
 * app to look different from itself on a morning with no signal.
 *
 * **Each file is registered at the nearest [FontWeight] to what it actually is, and every weight
 * named below has a file behind it.** A [FontFamily] that claims a weight it has no file for does
 * not fail — Android synthesises it by smearing the nearest one, which is how a deliberately chosen
 * face ends up looking like a bolded default. So the names below follow the files' real axis
 * positions rather than the names of the roles they serve, and where the two cannot line up exactly
 * the file is registered at the nearest position that is still its own:
 *
 * - `fraunces_light.ttf` has `usWeightClass` **250**, and Compose has no [FontWeight] for 250. It
 *   is registered at [FontWeight.ExtraLight] (200), the nearest free position, because 300 is
 *   already taken by `fraunces_regular.ttf`, which really is 300. Asking for ExtraLight therefore
 *   gets a real 250 instance — half a step heavier than the name — rather than a 300 file smeared
 *   thinner, and the two bundled instances stay distinct and in the right order. Half a step of
 *   honest weight beats any amount of synthesis.
 * - Work Sans' three files are 400, 500 and 600 and land exactly on Normal, Medium and SemiBold.
 *
 * **Only weights the scale actually asks for are registered.** A Fraunces Medium (500) file was
 * bundled and registered here at first, but no slot in `Type.kt` pairs [Display] with
 * [FontWeight.Medium], so it was 71.6 KB that shipped in every APK and could never be drawn. It was
 * removed. If a display slot ever wants Medium, the file comes back with it.
 *
 * These live on an object rather than as two top-level values because the text face is called
 * `Text`, and a top-level `Text` in this package would collide with Compose's `Text` composable for
 * anything later written beside it.
 */
internal object Faces {

    /** Fraunces. Numbers that answer a question, and nothing else. */
    val Display = FontFamily(
        Font(R.font.fraunces_light, FontWeight.ExtraLight),
        Font(R.font.fraunces_regular, FontWeight.Light),
    )

    /** Work Sans. Every word in the app. */
    val Text = FontFamily(
        Font(R.font.work_sans_regular, FontWeight.Normal),
        Font(R.font.work_sans_medium, FontWeight.Medium),
        Font(R.font.work_sans_semibold, FontWeight.SemiBold),
    )
}
