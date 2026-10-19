package dev.openlocation.runtime

import java.io.Writer
import java.time.Instant
import java.util.Locale

/** A real receiver observation, kept separate from synthetic scene coordinates. */
data class RecordedFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Float,
    val utcMillis: Long,
    val elapsedNanos: Long,
) {
    fun isUsable(nowNanos: Long, previousNanos: Long, isMock: Boolean): Boolean =
        !isMock &&
            latitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude.isFinite() &&
            longitude in -180.0..180.0 &&
            (altitude == null || altitude.isFinite()) &&
            accuracy.isFinite() &&
            accuracy in 0f..1000f &&
            utcMillis > 0 &&
            elapsedNanos > previousNanos &&
            nowNanos - elapsedNanos in 0..15_000_000_000L
}

/** Streaming GPX 1.1 export: no locale-sensitive numbers, account data, or synthetic metadata. */
object RecordedTrackGpx {
    fun write(output: Writer, points: Sequence<RecordedFix>) {
        output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        output.write(
            "<gpx version=\"1.1\" creator=\"OpenLocation\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:ol=\"https://openlocation.dev/gpx/1\"><trk><name>OpenLocation</name><trkseg>\n"
        )
        points.forEach { p ->
            require(p.latitude.isFinite() && p.latitude in -90.0..90.0)
            require(p.longitude.isFinite() && p.longitude in -180.0..180.0)
            output.write(
                String.format(
                    Locale.US,
                    "<trkpt lat=\"%.8f\" lon=\"%.8f\">",
                    p.latitude,
                    p.longitude,
                )
            )
            p.altitude?.let {
                require(it.isFinite())
                output.write(String.format(Locale.US, "<ele>%.3f</ele>", it))
            }
            output.write("<time>${Instant.ofEpochMilli(p.utcMillis)}</time>")
            require(p.accuracy.isFinite() && p.accuracy >= 0)
            output.write(
                String.format(
                    Locale.US,
                    "<extensions><ol:accuracy>%.3f</ol:accuracy></extensions></trkpt>\n",
                    p.accuracy,
                )
            )
        }
        output.write("</trkseg></trk></gpx>\n")
    }
}
