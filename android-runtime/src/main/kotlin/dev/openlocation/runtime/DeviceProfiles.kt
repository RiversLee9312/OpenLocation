package dev.openlocation.runtime

import android.content.Context
import android.os.Build
import org.json.JSONObject

data class DeviceProfile(
    val id: String,
    val model: String,
    val device: String,
    val sdk: Int,
    val fingerprint: String,
    val sensorServiceSha256: String,
    val verifiedChannels: Set<String> = emptySet(),
) {
    fun matches(model: String, device: String, sdk: Int, fingerprint: String, serviceHash: String) =
        this.model == model &&
            this.device == device &&
            this.sdk == sdk &&
            this.fingerprint == fingerprint &&
            sensorServiceSha256 == serviceHash

    fun matchesCurrent(): Boolean {
        if (
            model != Build.MODEL ||
                device != Build.DEVICE ||
                sdk != Build.VERSION.SDK_INT ||
                fingerprint != Build.FINGERPRINT
        )
            return false
        val hash =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.io.File("/system/lib64/libsensorservice.so").readBytes())
                .joinToString("") { "%02x".format(it) }
        return matches(Build.MODEL, Build.DEVICE, Build.VERSION.SDK_INT, Build.FINGERPRINT, hash)
    }
}

object DeviceProfiles {
    fun parse(text: String): List<DeviceProfile> {
        val root = JSONObject(text)
        require(root.getInt("schema") == 1)
        val list = root.getJSONArray("profiles")
        return (0 until list.length()).map { i ->
            val p = list.getJSONObject(i)
            val channels = p.optJSONArray("verifiedChannels")
            DeviceProfile(
                p.getString("id"),
                p.getString("model"),
                p.getString("device"),
                p.getInt("sdk"),
                p.getString("fingerprint"),
                p.getString("sensorServiceSha256"),
                if (channels == null) emptySet()
                else (0 until channels.length()).map { channels.getString(it) }.toSet(),
            )
        }
    }

    fun read(context: Context) =
        parse(context.assets.open("device_profiles.json").bufferedReader().use { it.readText() })

    fun matches(context: Context) =
        runCatching { read(context).any { it.matchesCurrent() } }.getOrDefault(false)

    fun verifiedChannels(context: Context) =
        runCatching {
                read(context).firstOrNull { it.matchesCurrent() }?.verifiedChannels ?: emptySet()
            }
            .getOrDefault(emptySet())
}
