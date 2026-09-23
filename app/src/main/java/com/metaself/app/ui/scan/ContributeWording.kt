package com.metaself.app.ui.scan

import com.metaself.app.data.product.Contribution

/**
 * What the app says about sending a product up.
 *
 * Three failures told apart, because they mean three different things to do: check the password,
 * try again later, or nothing. One "could not send" would leave the owner guessing.
 */
object ContributeWording {

    fun result(contribution: Contribution): String = when (contribution) {
        is Contribution.Sent ->
            "Sent. It is in the food database now, so nobody else has to type it."

        is Contribution.Unreachable ->
            "Could not reach Open Food Facts. Your copy is saved here either way."

        is Contribution.Refused -> {
            val detail = contribution.detail?.takeIf { it.isNotBlank() }
            "Open Food Facts would not accept it — usually a wrong username or password. " +
                "Your copy is saved here either way." + (detail?.let { " ($it)" } ?: "")
        }
    }

    const val NO_ACCOUNT =
        "Add an Open Food Facts username and password in Settings to send products up. " +
            "Your own copy works without one."

    const val ONLY_THE_LABEL =
        "Copy these from the package itself. Nothing estimated goes into the shared database."
}
