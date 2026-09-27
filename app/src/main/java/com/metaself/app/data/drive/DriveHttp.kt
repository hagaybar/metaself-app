package com.metaself.app.data.drive

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drive's REST calls, as the daily copy and the month archive both need them. Blocking; callers are
 * already on `Dispatchers.IO`. Untested for the reason `DriveAccess` is: no network and no account on
 * the build machine. Everything that decides anything is in [DriveFiles].
 */
@Singleton
class DriveHttp @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * The files [query] finds, or null when Drive refused or the reply was not a listing — never an
     * empty list for a failure. One page only: `pageSize=1000` is this app's choice, sized to hold the
     * daily files kept plus one file per month for many years; a next page is not asked for.
     */
    fun list(query: String, token: String): List<DriveFile>? {
        val url = "${DriveFiles.FILES_URL}?q=${URLEncoder.encode(query, "UTF-8")}" +
            "&fields=files(id,name)&pageSize=1000"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else DriveFiles.readListing(response.body?.string().orEmpty())
        }
    }

    fun upload(fileName: String, contents: ByteArray, mimeType: String, token: String): Boolean {
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(DriveFiles.metadataFor(fileName).toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(contents.toRequestBody(mimeType.toMediaType()))
            .build()
        val request = Request.Builder()
            .url(DriveFiles.UPLOAD_URL)
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return client.newCall(request).execute().use { it.isSuccessful }
    }

    fun download(id: String, token: String): ByteArray? {
        val request = Request.Builder()
            .url("${DriveFiles.FILES_URL}/$id?alt=media")
            .header("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }

    fun delete(id: String, token: String): Boolean {
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
