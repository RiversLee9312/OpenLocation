package dev.openlocation.app.data.recording

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Copy once into private storage; no long-lived access to a user's document provider is needed. */
object TrackDocuments {
    fun import(context: Context, uri: Uri): Long {
        val resolver = context.contentResolver
        val name =
            runCatching {
                    resolver
                        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }
                .getOrNull()
                ?.substringBeforeLast('.')
                ?.filterNot { it.isISOControl() }
                ?.trim()
                ?.take(120)
                ?.takeIf { it.isNotEmpty() } ?: "GPX"
        val bytes = resolver.openInputStream(uri)!!.use { it.readNBytes(8 * 1024 * 1024 + 1) }
        require(bytes.size <= 8 * 1024 * 1024)
        return TrackRepository.get(context).importGpx(bytes, name)
    }
}
