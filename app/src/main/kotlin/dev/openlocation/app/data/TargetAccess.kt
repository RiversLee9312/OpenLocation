package dev.openlocation.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.openlocation.runtime.Contract
import java.security.MessageDigest

/** Target UIDs are resolved from certificate-pinned packages by the controller. */
object TargetAccess {
    private fun certificate(context: Context, pkg: String): String? =
        runCatching {
                val info =
                    context.packageManager.getPackageInfo(
                        pkg,
                        PackageManager.PackageInfoFlags.of(
                            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                        ),
                    )
                info.signingInfo!!
                    .apkContentsSigners
                    .map { signer ->
                        MessageDigest.getInstance("SHA-256")
                            .digest(signer.toByteArray())
                            .joinToString("") { "%02x".format(it) }
                    }
                    .sorted()
                    .joinToString(":")
            }
            .getOrNull()

    fun setEnabled(context: Context, pkg: String, enabled: Boolean): Boolean {
        require(pkg != context.packageName && pkg != "android" && pkg != Contract.PROBE_PACKAGE)
        val prefs = context.getSharedPreferences("targets", 0)
        if (enabled) {
            if (
                runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid }
                    .getOrDefault(-1) < 10000
            )
                return false
            prefs.edit().putString(pkg, certificate(context, pkg) ?: return false).apply()
        } else {
            prefs.edit().remove(pkg).apply()
            context.revokeUriPermission(pkg, Contract.URI, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.contentResolver.notifyChange(Contract.URI, null)
        return true
    }

    fun enabled(context: Context) =
        (context.getSharedPreferences("targets", 0).all.keys + Contract.PROBE_PACKAGE)
            .filter { pkg ->
                pkg == Contract.PROBE_PACKAGE ||
                    context.getSharedPreferences("targets", 0).getString(pkg, null)?.let { pin ->
                        pin == certificate(context, pkg)
                    } == true
            }
            .toSet()

    /** Migrate away from the retired application-process backend's URI grants. */
    fun refreshGrants(context: Context) {
        enabled(context).forEach { pkg ->
            runCatching {
                context.revokeUriPermission(
                    pkg,
                    Contract.URI,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    fun uids(context: Context) =
        enabled(context)
            .mapNotNull {
                runCatching { context.packageManager.getApplicationInfo(it, 0).uid }.getOrNull()
            }
            .filter { it >= 10000 }
            .distinct()
            .toIntArray()
}
