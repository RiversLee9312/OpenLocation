package dev.openlocation.runtime

import org.junit.Assert.*
import org.junit.Test

class CoordinateNumbersTest {
    @Test
    fun signedAndLocalizedCoordinates() {
        assertEquals(-122.40999, CoordinateNumbers.parse(" −١٢٢٫٤٠٩٩٩ "), .00000001)
        assertEquals(31.2304, CoordinateNumbers.parse("31,2304"), .00000001)
        assertEquals(31.2304, CoordinateNumbers.parse("３１.２３０４"), .00000001)
        assertThrows(IllegalArgumentException::class.java) { CoordinateNumbers.parse("NaN") }
        assertThrows(IllegalArgumentException::class.java) { CoordinateNumbers.parse("1,234.5") }
    }
}
