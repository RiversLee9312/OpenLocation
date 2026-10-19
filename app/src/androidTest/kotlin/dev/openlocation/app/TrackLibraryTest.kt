package dev.openlocation.app

import androidx.test.platform.app.InstrumentationRegistry
import dev.openlocation.app.data.recording.TrackRepository
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

/** Exercise actual SQLite migration/storage and lossless original-document export on device. */
class TrackLibraryTest {
    @Test
    fun importRenamePreviewUseAndDeletePreserveOriginalMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = TrackRepository.get(context)
        val original =
            """<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1" creator="fixture" xmlns="http://www.topografix.com/GPX/1/1"><metadata><desc>${"x".repeat(2_200_000)}</desc></metadata><trk><name>Fixture</name><trkseg><trkpt lat="37.79" lon="-122.40"><ele>2</ele><time>2026-10-19T00:00:00Z</time></trkpt><trkpt lat="37.791" lon="-122.401"><ele>3</ele><time>2026-10-19T00:00:01Z</time></trkpt></trkseg></trk></gpx>"""
                .toByteArray()
        val previousIds = repository.list().map { it.id }.toSet()
        val id = repository.importGpx(original, "Fixture")
        try {
            repository.rename(id, "Renamed")
            val track = repository.list().first { it.id == id }
            assertEquals("Renamed", track.name)
            assertEquals("imported", track.kind)
            assertEquals(2, track.points)
            assertTrue(track.distance > 100)
            assertEquals(2, repository.preview(id, track.points).size)
            assertEquals(2, repository.scenePoints(id).size)
            val exported = ByteArrayOutputStream().also { repository.export(id, it) }.toByteArray()
            assertArrayEquals(
                "Original XML/metadata must survive rename and export",
                original,
                exported,
            )
        } finally {
            repository.delete(id)
        }
        assertEquals(previousIds, repository.list().map { it.id }.toSet())
    }

    @Test
    fun invalidImportDoesNotCreatePartialTrack() {
        val repository =
            TrackRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = repository.list().map { it.id }
        assertTrue(
            runCatching { repository.importGpx("<gpx/>".toByteArray(), "Invalid") }.isFailure
        )
        assertEquals(previous, repository.list().map { it.id })
    }
}
