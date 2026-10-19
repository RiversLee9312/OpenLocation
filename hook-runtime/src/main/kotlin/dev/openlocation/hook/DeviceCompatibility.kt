package dev.openlocation.hook

/** A fingerprint mismatch disables adapters before any native service hook is installed. */
internal object DeviceCompatibility {
    fun matches(): Boolean {
        val text =
            requireNotNull(
                    javaClass.classLoader?.getResourceAsStream("assets/device_profiles.json")
                )
                .bufferedReader()
                .use { it.readText() }
        return dev.openlocation.runtime.DeviceProfiles.parse(text).any { it.matchesCurrent() }
    }
}
