package dev.openlocation.runtime

import dev.openlocation.scenario.SceneConfig
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class SpeedInputTest {
    @Test
    fun highwayValuesUseTheSceneRangeInsteadOfTheOldModeSliderRange() {
        assertEquals(
            220.0 / 3.6,
            SpeedInput.metersPerSecond("220", SpeedUnit.KILOMETERS_PER_HOUR, Locale.US)!!,
            1e-9,
        )
        assertEquals(
            70.0,
            SpeedInput.metersPerSecond("252", SpeedUnit.KILOMETERS_PER_HOUR, Locale.US)!!,
            1e-9,
        )
        assertNull(SpeedInput.metersPerSecond("253", SpeedUnit.KILOMETERS_PER_HOUR, Locale.US))
        assertNull(SpeedInput.metersPerSecond("-1", SpeedUnit.METERS_PER_SECOND, Locale.US))
        for (text in listOf("", "NaN", "Infinity", "220 km/h", "1,000", "2 0")) {
            assertNull(
                text,
                SpeedInput.metersPerSecond(text, SpeedUnit.METERS_PER_SECOND, Locale.US),
            )
        }
    }

    @Test
    fun decimalNotationAndBoundaryFormattingWorkAcrossLocalesAndUnits() {
        assertEquals(
            220.5 / 3.6,
            SpeedInput.metersPerSecond("220,5", SpeedUnit.KILOMETERS_PER_HOUR, Locale.GERMANY)!!,
            1e-9,
        )
        assertEquals(
            220.5 / 3.6,
            SpeedInput.metersPerSecond("220.5", SpeedUnit.KILOMETERS_PER_HOUR, Locale.GERMANY)!!,
            1e-9,
        )
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.forLanguageTag("ar"))) {
            for (unit in SpeedUnit.entries) {
                val maximum = unit.fromMetersPerSecond(SceneConfig.MAX_SPEED_METERS_PER_SECOND)
                val text = SpeedInput.format(maximum, locale)
                assertEquals(70.0, SpeedInput.metersPerSecond(text, unit, locale)!!, 1e-8)
            }
        }
    }
}
