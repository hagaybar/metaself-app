package com.metaself.app.data.diagnostics.measure

import android.content.Context
import android.net.Uri
import android.os.Build
import com.metaself.app.BuildConfig
import com.metaself.app.data.day.MetaSelfDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D152: Download diagnostics — the one moment the database is read for measuring. The header
 * ([DiagnosticsHeader]: app version, phone model, Android version, hours measured, each table's row
 * count, the database's size on disk) and then every measuring line, oldest first, onto the file the
 * owner chose. Nothing is sent anywhere.
 */
@Singleton
class Diagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MetaSelfDatabase,
    private val recorder: MeasureRecorder,
    private val switch: MeasureSwitch,
) {

    /** @return whether the file was written. */
    suspend fun writeTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val times = switch.times.first()
            val now = recorder.clock.wallMillis()
            val header = DiagnosticsHeader.line(
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                phoneModel = Build.MODEL ?: "",
                androidVersion = Build.VERSION.RELEASE ?: "",
                androidSdk = Build.VERSION.SDK_INT,
                hoursMeasured = MeasureWindow.hoursMeasured(times.startedAt, times.until, now),
                measuredFromMillis = times.startedAt,
                measuredUntilMillis = times.until?.let { minOf(it, now) },
                tableRows = rowCounts(),
                databaseBytes = databaseBytes(),
                writtenAtMillis = now,
            )
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: return@runCatching false
            out.use {
                it.write((header + "\n").toByteArray())
                recorder.copyTo(it)
            }
            true
        }.getOrDefault(false)
    }

    /** Every table of the schema, counted; Android's and Room's own bookkeeping tables left out. */
    private fun rowCounts(): Map<String, Long> {
        val db = database.openHelper.readableDatabase
        val tables = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table')",
        ).use { cursor -> while (cursor.moveToNext()) tables += cursor.getString(0) }
        return tables.associateWith { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
        }
    }

    private fun databaseBytes(): Long {
        val main = context.getDatabasePath(database.openHelper.databaseName ?: return 0L)
        return listOf(main, File(main.path + "-wal"), File(main.path + "-shm")).sumOf { it.length() }
    }
}
