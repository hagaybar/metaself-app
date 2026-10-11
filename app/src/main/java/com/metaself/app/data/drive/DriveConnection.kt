package com.metaself.app.data.drive

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/**
 * D139: whether a Drive failure was for want of a connection — the host could not be resolved, or no connection could
 * be made (no network, or none that reaches Google). A timeout is not counted: the network was there and answered
 * slowly, which is worth a line of its own. Any cause in the chain counts.
 */
object DriveConnection {
    fun lost(failure: Throwable): Boolean =
        generateSequence(failure) { it.cause?.takeIf { cause -> cause !== it } }
            .take(MAX_CAUSES)
            .any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException }

    /** A guard against a cause chain that loops; real chains are a few deep. */
    private const val MAX_CAUSES = 10
}
