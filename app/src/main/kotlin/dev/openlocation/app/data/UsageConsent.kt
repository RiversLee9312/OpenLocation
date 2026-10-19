package dev.openlocation.app.data

import android.content.Context

/** Versioned, local-only acknowledgement. Changing the notice requires a new version. */
object UsageConsent {
    private const val VERSION = 1

    fun accepted(context: Context) =
        context.getSharedPreferences("usage", 0).getInt("accepted", 0) == VERSION

    fun accept(context: Context) {
        context.getSharedPreferences("usage", 0).edit().putInt("accepted", VERSION).apply()
    }
}
