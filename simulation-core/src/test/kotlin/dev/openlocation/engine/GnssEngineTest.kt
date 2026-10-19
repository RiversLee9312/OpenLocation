package dev.openlocation.engine

import dev.openlocation.scenario.*
import java.time.Instant
import kotlin.test.*

class GnssEngineTest {
    private val epoch = Instant.parse("2026-10-19T09:00:00Z").toEpochMilli()

    @Test
    fun orbitalGeometryRawRangesAndSignalStrengthAgree() {
        val scene = DemoScenes.create().copy(environmentSegments = emptyList())
        val frame = SceneEngine(scene).sample(10_000_000_000, 100_000_000_000, epoch)
        val engine = GnssEngine(scene)
        val sky = engine.sample(frame, epoch)
        assertTrue(sky.satellites.size >= 12)
        assertEquals(sky, GnssEngine(scene).sample(frame, epoch))
        sky.satellites.forEach {
            assertTrue(it.rangeMeters in 15e6..45e6)
            assertTrue(it.elevationDegrees in 5.0..90.0)
            assertTrue(it.azimuthDegrees in 0.0..<360.0)
            assertTrue(it.cn0DbHz in 0.0..55.0)
            assertTrue(kotlin.math.abs(it.rangeRateMetersPerSecond) < 3000)
            assertEquals(
                sky.receiverClockBiasNs * 1e-9 * GnssEngine.SPEED_OF_LIGHT,
                it.pseudorangeMeters - it.rangeMeters,
                100.0,
            )
        }
        val weakScene = scene.copy(gnss = scene.gnss.copy(cn0DbHz = 10.0))
        val weak = GnssEngine(weakScene).sample(frame, epoch)
        assertTrue(weak.satellites.none { it.usedInFix })
        assertTrue(weak.satellites.zip(sky.satellites).all { (a, b) -> a.cn0DbHz < b.cn0DbHz })
        assertNull(SceneEngine(weakScene).sample(0).observedLocation)
        val nmea = Nmea.sentences(sky, frame)
        assertTrue(nmea.first().contains("GNGGA,090000.000"))
        nmea.forEach { sentence ->
            val body = sentence.substringAfter('$').substringBefore('*')
            assertEquals(Nmea.checksum(body), sentence)
        }
        assertTrue(Nmea.dilution(sky.satellites.filter { it.usedInFix }).first < 10)
    }
}
