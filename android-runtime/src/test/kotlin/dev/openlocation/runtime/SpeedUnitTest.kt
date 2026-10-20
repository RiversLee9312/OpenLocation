package dev.openlocation.runtime

import org.junit.Assert.*
import org.junit.Test

class SpeedUnitTest {
    @Test
    fun conventionalUnitsAndSavedPreferencesPreserveThePhysicalSpeed() {
        assertEquals(35.0, SpeedUnit.METERS_PER_SECOND.fromMetersPerSecond(35.0), 0.0)
        assertEquals(126.0, SpeedUnit.KILOMETERS_PER_HOUR.fromMetersPerSecond(35.0), 1e-10)
        assertEquals(50.0, SpeedUnit.MILES_PER_HOUR.fromMetersPerSecond(22.352), 1e-10)
        SpeedUnit.entries.forEach {
            assertEquals(it, SpeedUnit.fromId(it.id))
            assertEquals(35.0, it.toMetersPerSecond(it.fromMetersPerSecond(35.0)), 1e-10)
        }
        assertEquals(SpeedUnit.METERS_PER_SECOND, SpeedUnit.fromId(null))
        assertEquals(SpeedUnit.METERS_PER_SECOND, SpeedUnit.fromId("unknown-future-unit"))
    }
}
