package dev.openlocation.runtime

import java.io.StringWriter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class RecordedTrackTest {
    private val fix = RecordedFix(31.2, 121.5, 12.3, 3f, 1_700_000_000_123, 100_000_000_000)

    @Test
    fun rejectsMockStaleDuplicateAndInvalidFixes() {
        assertTrue(fix.isUsable(fix.elapsedNanos + 100, 0, false))
        assertFalse(fix.isUsable(fix.elapsedNanos + 100, 0, true))
        assertFalse(fix.isUsable(fix.elapsedNanos + 16_000_000_000, 0, false))
        assertFalse(fix.isUsable(fix.elapsedNanos, fix.elapsedNanos, false))
        assertFalse(fix.copy(latitude = Double.NaN).isUsable(fix.elapsedNanos, 0, false))
        assertFalse(fix.copy(accuracy = -1f).isUsable(fix.elapsedNanos, 0, false))
    }

    @Test
    fun exportsUtcElevationAndAccuracyUnderCommaDecimalLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val output = StringWriter()
            RecordedTrackGpx.write(
                output,
                sequenceOf(fix, fix.copy(altitude = null, utcMillis = fix.utcMillis + 1000)),
            )
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val document = factory.newDocumentBuilder().parse(output.toString().byteInputStream())
            val points =
                document.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkpt")
            assertEquals(2, points.length)
            assertEquals("31.20000000", points.item(0).attributes.getNamedItem("lat").nodeValue)
            assertEquals(
                "2023-11-14T22:13:20.123Z",
                document
                    .getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "time")
                    .item(0)
                    .textContent,
            )
            assertEquals(
                1,
                document.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "ele").length,
            )
            assertEquals(
                "3.000",
                document
                    .getElementsByTagNameNS("https://openlocation.dev/gpx/1", "accuracy")
                    .item(0)
                    .textContent,
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
