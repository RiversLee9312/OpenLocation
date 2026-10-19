package dev.openlocation.runtime

import org.junit.Assert.*
import org.junit.Test

class DeviceProfileTest {
    @Test
    fun everyIdentityComponentMustMatchExactly() {
        val p =
            DeviceProfile(
                "fixture",
                "model",
                "device",
                36,
                "full/fingerprint:16/release",
                "binary-hash",
            )
        assertTrue(p.matches("model", "device", 36, "full/fingerprint:16/release", "binary-hash"))
        assertFalse(p.matches("similar model", "device", 36, p.fingerprint, "binary-hash"))
        assertFalse(p.matches("model", "other device", 36, p.fingerprint, "binary-hash"))
        assertFalse(p.matches("model", "device", 35, p.fingerprint, "binary-hash"))
        assertFalse(p.matches("model", "device", 36, p.fingerprint + "-ota", "binary-hash"))
        assertFalse(p.matches("model", "device", 36, p.fingerprint, "changed-binary"))
    }
}
