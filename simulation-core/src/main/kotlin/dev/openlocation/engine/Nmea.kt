package dev.openlocation.engine

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

/** NMEA 0183 sentences share the raw epoch's sky, fix validity and receiver timestamp. */
object Nmea {
    private fun f(pattern: String, vararg args: Any) = String.format(Locale.US, pattern, *args)

    fun checksum(body: String): String {
        val check = body.fold(0) { sum, c -> sum xor c.code }
        return "\$" + body + f("*%02X\r\n", check)
    }

    fun sentences(epoch: GnssEpoch, frame: SampleFrame): List<String> {
        val utc = Instant.ofEpochMilli(epoch.utcMillis).atZone(ZoneOffset.UTC)
        val time = utc.format(DateTimeFormatter.ofPattern("HHmmss.SSS", Locale.US))
        val date = utc.format(DateTimeFormatter.ofPattern("ddMMyy", Locale.US))
        val used = epoch.satellites.filter { it.usedInFix }
        val valid = used.size >= 4 && frame.observedLocation != null
        val point = frame.observedLocation?.position ?: frame.truePosition
        fun coordinate(value: Double, lat: Boolean): String {
            val degrees = floor(abs(value)).toInt()
            return f(if (lat) "%02d%09.6f" else "%03d%09.6f", degrees, (abs(value) - degrees) * 60)
        }
        val lat = coordinate(point.latitude, true)
        val lon = coordinate(point.longitude, false)
        val ns = if (point.latitude >= 0) "N" else "S"
        val ew = if (point.longitude >= 0) "E" else "W"
        val dop = dilution(used)
        return buildList {
            add(
                checksum(
                    f(
                        "GNGGA,%s,%s,%s,%s,%s,%d,%02d,%.1f,%.2f,M,0.0,M,,",
                        time,
                        lat,
                        ns,
                        lon,
                        ew,
                        if (valid) 1 else 0,
                        used.size.coerceAtMost(99),
                        dop.second,
                        point.altitude,
                    )
                )
            )
            add(
                checksum(
                    f(
                        "GNRMC,%s,%s,%s,%s,%s,%s,%.2f,%.2f,%s,,,A",
                        time,
                        if (valid) "A" else "V",
                        lat,
                        ns,
                        lon,
                        ew,
                        frame.velocity.norm() * 1.94384449,
                        frame.observedLocation?.bearingDegrees ?: 0.0,
                        date,
                    )
                )
            )
            epoch.satellites
                .groupBy { it.constellation }
                .forEach { (type, satellites) ->
                    val talker =
                        when (type) {
                            1 -> "GP"
                            2 -> "GP"
                            4 -> "GQ"
                            5 -> "GB"
                            else -> "GN"
                        }
                    val pages = ceil(satellites.size / 4.0).toInt()
                    satellites.chunked(4).forEachIndexed { index, group ->
                        val body =
                            f("%sGSV,%d,%d,%02d", talker, pages, index + 1, satellites.size) +
                                group.joinToString("") {
                                    f(
                                        ",%02d,%02d,%03d,%02d",
                                        it.svid,
                                        it.elevationDegrees.roundToInt(),
                                        it.azimuthDegrees.roundToInt() % 360,
                                        it.cn0DbHz.roundToInt(),
                                    )
                                }
                        add(checksum(body))
                    }
                    val ids =
                        used.filter { it.constellation == type }.take(12).map { f("%02d", it.svid) }
                    val slots = (ids + List(12 - ids.size) { "" }).joinToString(",")
                    add(
                        checksum(
                            f(
                                "%sGSA,A,%d,%s,%.1f,%.1f,%.1f",
                                talker,
                                if (valid) 3 else 1,
                                slots,
                                dop.first,
                                dop.second,
                                dop.third,
                            )
                        )
                    )
                }
        }
    }

    /** Invert HᵀH in local ENU to derive PDOP/HDOP/VDOP from this visible geometry. */
    fun dilution(used: List<SatelliteObservation>): Triple<Double, Double, Double> {
        if (used.size < 4) return Triple(99.9, 99.9, 99.9)
        val normal = Array(4) { DoubleArray(8) }
        used.forEach {
            val el = Math.toRadians(it.elevationDegrees)
            val az = Math.toRadians(it.azimuthDegrees)
            val h = doubleArrayOf(cos(el) * sin(az), cos(el) * cos(az), sin(el), 1.0)
            for (i in 0..3) for (j in 0..3) normal[i][j] += h[i] * h[j]
        }
        for (i in 0..3) normal[i][i + 4] = 1.0
        for (i in 0..3) {
            val pivot = (i..3).maxBy { abs(normal[it][i]) }
            if (abs(normal[pivot][i]) < 1e-10) return Triple(99.9, 99.9, 99.9)
            val swap = normal[i]
            normal[i] = normal[pivot]
            normal[pivot] = swap
            val value = normal[i][i]
            for (j in 0..7) normal[i][j] /= value
            for (k in 0..3) if (k != i) {
                val factor = normal[k][i]
                for (j in 0..7) normal[k][j] -= factor * normal[i][j]
            }
        }
        val horizontal = (normal[0][4] + normal[1][5]).coerceAtLeast(0.0)
        val vertical = normal[2][6].coerceAtLeast(0.0)
        return Triple(sqrt(horizontal + vertical), sqrt(horizontal), sqrt(vertical))
    }
}
