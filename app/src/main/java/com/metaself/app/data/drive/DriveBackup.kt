package com.metaself.app.data.drive

import com.metaself.app.data.backup.BackupCodec
import com.metaself.app.data.backup.BackupRepository
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.backup.BackupSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What writing to Drive did. */
sealed interface DriveOutcome {

    data class Written(val fileName: String, val deleted: Int) : DriveOutcome

    /**
     * He has not agreed yet, or the agreement was withdrawn.
     *
     * Carries the screen to show him. Without it this state is a dead end: Google would keep saying
     * "he has not agreed" and nothing would ever ask him, so the first attempt could never succeed
     * and every attempt after it would fail identically.
     */
    data class NeedsConsent(val request: android.content.IntentSender) : DriveOutcome

    data class Failed(val reason: String) : DriveOutcome
}

/**
 * The same export, written into the owner's own Drive.
 *
 * A SECOND destination beside the folder copy, never a replacement for it. If Drive is unreachable —
 * no signal, consent withdrawn, Google having a bad morning — the folder copy on the phone is
 * untouched and he still has yesterday. A backup that has one way to fail is not a backup.
 *
 * The upload goes through OkHttp, which this app already had, rather than Google's Java API client
 * libraries: one dependency instead of a tree of them, and a request whose contents can be read.
 */
@Singleton
class DriveBackup @Inject constructor(
    private val backups: BackupRepository,
    private val access: DriveAccess,
    private val problems: ProblemLog,
) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun write(today: LocalDate, nowMillis: Long): DriveOutcome =
        withContext(Dispatchers.IO) {
            val token = when (val auth = access.authorise()) {
                is DriveAuth.Token -> auth.accessToken
                is DriveAuth.NeedsConsent ->
                    return@withContext DriveOutcome.NeedsConsent(auth.request)
                is DriveAuth.Failed -> return@withContext DriveOutcome.Failed(auth.reason)
            }

            val fileName = BackupSchedule.fileNameFor(today.toString())
            val contents = BackupCodec.encode(backups.export(nowMillis))

            runCatching {
                val mine = list(token)
                // Replace today's rather than leaving two of it, if this runs twice in a day.
                DriveFiles.existing(mine, fileName)?.let { delete(it, token) }

                if (!upload(fileName, contents, token)) {
                    return@withContext DriveOutcome.Failed("Drive refused the upload")
                }

                val gone = DriveFiles.toDelete(list(token)).count { delete(it.id, token) }
                DriveOutcome.Written(fileName, gone)
            }.getOrElse { error ->
                problems.record(
                    kind = "drive",
                    detail = "upload failed: ${error::class.java.simpleName} ${error.message}",
                )
                DriveOutcome.Failed(error::class.java.simpleName)
            }
        }

    private fun list(token: String): List<DriveFile> {
        val url = "${DriveFiles.FILES_URL}?q=${java.net.URLEncoder.encode(DriveFiles.listQuery(), "UTF-8")}" +
            "&fields=files(id,name)&pageSize=100"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) emptyList()
            else DriveFiles.readListing(response.body?.string().orEmpty())
        }
    }

    private fun upload(fileName: String, contents: String, token: String): Boolean {
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(
                DriveFiles.metadataFor(fileName)
                    .toRequestBody("application/json; charset=UTF-8".toMediaType()),
            )
            .addPart(contents.toRequestBody("application/json".toMediaType()))
            .build()

        val request = Request.Builder()
            .url(DriveFiles.UPLOAD_URL)
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        return client.newCall(request).execute().use { it.isSuccessful }
    }

    private fun delete(id: String, token: String): Boolean {
        val request = Request.Builder()
            .url("${DriveFiles.FILES_URL}/$id")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()

        return client.newCall(request).execute().use { it.isSuccessful }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
    }
}
