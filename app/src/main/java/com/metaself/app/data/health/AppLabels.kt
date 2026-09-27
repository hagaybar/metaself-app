package com.metaself.app.data.health

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The phone's name for an app, by its package name. */
fun interface AppLabels {
    fun labelOf(packageName: String): String
}

/**
 * [PackageManager.getApplicationLabel], or the package name itself when the phone does not know the
 * app, will not say, or gives a blank label. From Android 11 another app is only visible when the
 * manifest's `<queries>` covers it; the manifest declares the intents an app asking for Health Connect
 * permissions has to declare, so the apps that write to it should be visible. Never throws (D8).
 */
class PackageManagerAppLabels @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppLabels {

    override fun labelOf(packageName: String): String = try {
        val packages = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packages.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packages.getApplicationInfo(packageName, 0)
        }
        packages.getApplicationLabel(info).toString().takeIf { it.isNotBlank() } ?: packageName
    } catch (failure: Exception) {
        packageName
    }
}
