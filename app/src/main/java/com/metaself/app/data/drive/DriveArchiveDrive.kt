package com.metaself.app.data.drive

import com.metaself.app.data.health.ArchiveDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [ArchiveDrive] over [DriveAccess] and [DriveHttp]. Never asks for consent: the daily copy does, so a
 * consent screen here is treated as Drive being out of reach. Untested for the reason [DriveAccess]
 * is; the decisions are in `HealthArchive`.
 */
class DriveArchiveDrive @Inject constructor(
    private val access: DriveAccess,
    private val http: DriveHttp,
) : ArchiveDrive {

    override suspend fun token(): String? = (access.authorise() as? DriveAuth.Token)?.accessToken

    override suspend fun list(token: String): List<DriveFile> =
        withContext(Dispatchers.IO) { http.list(DriveFiles.archiveListQuery(), token) }

    override suspend fun upload(fileName: String, bytes: ByteArray, token: String): Boolean =
        withContext(Dispatchers.IO) { http.upload(fileName, bytes, "application/gzip", token) }

    override suspend fun download(id: String, token: String): ByteArray? =
        withContext(Dispatchers.IO) { http.download(id, token) }

    override suspend fun delete(id: String, token: String): Boolean =
        withContext(Dispatchers.IO) { http.delete(id, token) }
}
