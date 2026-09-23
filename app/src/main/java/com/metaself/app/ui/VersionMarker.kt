package com.metaself.app.ui

/**
 * The version line drawn on the first screen.
 *
 * It exists so the owner can say which build is on his phone without opening anything — two rcs of
 * one version look identical otherwise, which is exactly when it matters. Pure, so it can be pinned
 * by a test; a string left inline in a composable is reachable by none.
 */
object VersionMarker {

    fun line(versionName: String, versionCode: Int): String = "v$versionName ($versionCode)"
}
