package dev.openlocation.app.data

import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import dev.openlocation.scenario.SceneCodec

/** Signature-gated low-frequency control. No disk or IPC is performed on the sensor hot path. */
class ControlProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = requireNotNull(context)
        val trustedController =
            ctx.packageManager.checkSignatures(
                Binder.getCallingUid(),
                android.os.Process.myUid(),
            ) == PackageManager.SIGNATURE_MATCH
        check(
            trustedController ||
                method in setOf("snapshot", "report") && Binder.getCallingUid() == 1000
        ) {
            "Untrusted caller"
        }
        val store = SceneStore.get(ctx)
        when (method) {
            "snapshot" -> Unit
            "report" -> {
                check(Binder.getCallingUid() == 1000)
                ctx.getSharedPreferences("backend", 0)
                    .edit()
                    .putString("channels", extras?.getString("channels")?.take(200) ?: "")
                    .putLong("seen", android.os.SystemClock.elapsedRealtime())
                    .apply()
            }
            "start" -> store.start()
            "pause" -> store.togglePause()
            "stop" -> store.stop()
            "target" -> {
                check(trustedController)
                check(
                    TargetAccess.setEnabled(
                        ctx,
                        requireNotNull(arg),
                        extras?.getBoolean("enabled", true) ?: true,
                    )
                )
            }
            "configure" -> {
                require((arg?.length ?: 0) <= 2_000_000)
                val config = SceneCodec.decode(requireNotNull(arg))
                store.configure(config)
            }
            else -> error("Unknown command")
        }
        return Bundle().apply {
            putString("session", SceneCodec.encodeSession(store.snapshot()))
            putIntArray("targets", TargetAccess.uids(ctx))
            putString("backend", ctx.getSharedPreferences("backend", 0).getString("channels", ""))
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri) = "application/vnd.openlocation.session+json"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}
