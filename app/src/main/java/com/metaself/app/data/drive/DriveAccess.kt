package com.metaself.app.data.drive

import android.content.Context
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.metaself.app.data.diagnostics.ProblemLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What asking Google for permission produced. */
sealed interface DriveAuth {

    /** A token good for about an hour. Asked for again each time rather than stored. */
    data class Token(val accessToken: String) : DriveAuth

    /** Google wants the owner to agree first; this is the screen to show him. */
    data class NeedsConsent(val request: IntentSender) : DriveAuth

    data class Failed(val reason: String) : DriveAuth
}

/**
 * Getting permission to write to the owner's own Drive.
 *
 * **No refresh token is ever held by this app.** Google's authorisation client is asked for an
 * access token each time one is wanted; once consent has been given it returns one without
 * bothering him, and an hour later it returns another. That is why nothing here has to be stored,
 * and why there is no credential in the backup or on disk to leak.
 *
 * Nothing tests this class. It needs Google Play services and a real account, neither of which
 * exists on the build machine — so it is kept to as few lines as it can be, and everything that
 * could be decided elsewhere is (see [DriveFiles]).
 */
@Singleton
class DriveAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val problems: ProblemLog,
) {

    suspend fun authorise(): DriveAuth = suspendCancellableCoroutine { waiting ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveFiles.SCOPE)))
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                val consent = result.pendingIntent
                waiting.resume(
                    when {
                        // First time only: he has not yet agreed to this app touching his Drive.
                        result.hasResolution() && consent != null ->
                            DriveAuth.NeedsConsent(consent.intentSender)

                        result.accessToken != null -> DriveAuth.Token(result.accessToken!!)

                        else -> {
                            // Neither a token nor a screen to show: nothing further can happen,
                            // and without this line nothing would say so.
                            problems.record(
                                kind = "drive",
                                detail = "authorisation returned neither a token nor a consent " +
                                    "screen — check the OAuth client's type, package and " +
                                    "fingerprint, and that the Drive API is enabled",
                            )
                            DriveAuth.Failed("Google returned no token")
                        }
                    },
                )
            }
            .addOnFailureListener { error ->
                problems.record(
                    kind = "drive",
                    detail = "authorisation failed: ${error::class.java.simpleName} " +
                        "${error.message}",
                )
                waiting.resume(DriveAuth.Failed(error::class.java.simpleName))
            }
    }
}
