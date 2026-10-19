package dev.openlocation.engine

import dev.openlocation.scenario.*
import java.time.Instant
import kotlin.math.*
import kotlinx.serialization.json.*
import sgp4.ElsetRec
import sgp4.SGP4

data class SatelliteObservation(
    val constellation: Int,
    val svid: Int,
    val catalogId: Int,
    val azimuthDegrees: Double,
    val elevationDegrees: Double,
    val cn0DbHz: Double,
    val rangeMeters: Double,
    val rangeRateMetersPerSecond: Double,
    val pseudorangeMeters: Double,
    val pseudorangeUncertaintyMeters: Double,
    val accumulatedDeltaRangeMeters: Double,
    val carrierFrequencyHz: Double,
    val usedInFix: Boolean,
)

data class GnssEpoch(
    val utcMillis: Long,
    val elapsedNs: Long,
    val receiverClockBiasNs: Double,
    val satellites: List<SatelliteObservation>,
)

/** OMM -> Vallado SGP4/SDP4 -> TEME/ECEF -> WGS84 local sky, independent of callback order. */
class GnssEngine(private val config: SceneConfig) {
    private data class Orbit(
        val json: JsonObject,
        val constellation: Int,
        val svid: Int,
        val epoch: Long,
    ) {
        fun number(key: String) = json.getValue(key).jsonPrimitive.double

        val catalogId
            get() = number("NORAD_CAT_ID").toInt()

        fun record() =
            ElsetRec().apply {
                satID = catalogId.toString()
                val julian = epoch / 86_400_000.0 + 2440587.5
                jdsatepoch = floor(julian)
                jdsatepochF = julian - jdsatepoch
                ecco = number("ECCENTRICITY")
                inclo = Math.toRadians(number("INCLINATION"))
                nodeo = Math.toRadians(number("RA_OF_ASC_NODE"))
                argpo = Math.toRadians(number("ARG_OF_PERICENTER"))
                mo = Math.toRadians(number("MEAN_ANOMALY"))
                no_kozai = number("MEAN_MOTION") * 2 * PI / 1440
                bstar = number("BSTAR")
                ndot = number("MEAN_MOTION_DOT") * 2 * PI / (1440.0 * 1440)
                nddot = number("MEAN_MOTION_DDOT") * 2 * PI / (1440.0 * 1440 * 1440)
                SGP4.sgp4init('i', this)
            }
    }

    companion object {
        const val SPEED_OF_LIGHT = 299792458.0
        const val GPS_EPOCH_MILLIS = 315964800000L
        const val GPS_WEEK_NS = 604800000000000L
        private val bundledOrbits: List<Orbit> by lazy {
            val text =
                requireNotNull(GnssEngine::class.java.getResourceAsStream("/gnss-omm.json"))
                    .bufferedReader()
                    .use { it.readText() }
            parseCatalog(text)
        }

        fun validateCatalog(text: String): Int = parseCatalog(text).size.also { require(it >= 24) }

        private fun parseCatalog(text: String): List<Orbit> {
            require(text.length <= 200_000)
            return Json.parseToJsonElement(text)
                .jsonArray
                .also { require(it.size <= 1000) }
                .mapNotNull { item ->
                    val obj = item.jsonObject
                    val name = obj.getValue("OBJECT_NAME").jsonPrimitive.content
                    val prn = Regex("PRN\\s+(\\d+)").find(name)?.groupValues?.get(1)?.toInt()
                    val bds = Regex("\\(C(\\d+)\\)").find(name)?.groupValues?.get(1)?.toInt()
                    val identity =
                        when {
                            name.startsWith("GPS ") && prn != null -> 1 to prn
                            bds != null -> 5 to bds
                            name.startsWith("QZS") && prn != null -> 4 to prn
                            prn != null && prn in 120..158 -> 2 to prn
                            else ->
                                null // Do not invent PRN/slot mappings absent from the source
                                     // catalogue.
                        } ?: return@mapNotNull null
                    Orbit(
                            obj,
                            identity.first,
                            identity.second,
                            Instant.parse(obj.getValue("EPOCH").jsonPrimitive.content + "Z")
                                .toEpochMilli(),
                        )
                        .also {
                            require(it.number("MEAN_MOTION") in .1..20.0)
                            require(it.number("ECCENTRICITY") in 0.0..<1.0)
                            it.record().also { record -> require(record.error == 0) }
                        }
                }
        }
    }

    private val orbits = config.gnss.orbitalCatalogOmm?.let { parseCatalog(it) } ?: bundledOrbits

    fun sample(frame: SampleFrame, utcMillis: Long): GnssEpoch {
        val geo = frame.truePosition
        val latitude = Math.toRadians(geo.latitude)
        val longitude = Math.toRadians(geo.longitude)
        val sinLat = sin(latitude)
        val cosLat = cos(latitude)
        val sinLon = sin(longitude)
        val cosLon = cos(longitude)
        val n = 6378137.0 / sqrt(1 - .00669437999014 * sinLat * sinLat)
        val receiver =
            Vec3(
                (n + geo.altitude) * cosLat * cosLon,
                (n + geo.altitude) * cosLat * sinLon,
                (n * (1 - .00669437999014) + geo.altitude) * sinLat,
            )
        fun enu(v: Vec3) =
            Vec3(
                -sinLon * v.x + cosLon * v.y,
                -sinLat * cosLon * v.x - sinLat * sinLon * v.y + cosLat * v.z,
                cosLat * cosLon * v.x + cosLat * sinLon * v.y + sinLat * v.z,
            )
        val seconds = frame.sequence * .01
        val attenuation =
            when (frame.environment) {
                Environment.OPEN -> 0.0
                Environment.URBAN -> 8.0
                Environment.COVERED -> 15.0
                Environment.TUNNEL -> 65.0
                Environment.RECOVERY -> 8.0
            }
        val biasNs = 85000.0
        val satellites =
            orbits
                .mapNotNull { orbit ->
                    val r = DoubleArray(3)
                    val v = DoubleArray(3)
                    val record = orbit.record()
                    if (
                        !SGP4.sgp4(record, (utcMillis - orbit.epoch) / 60000.0, r, v) ||
                            record.error != 0
                    )
                        return@mapNotNull null
                    val theta = SGP4.gstime(utcMillis / 86400000.0 + 2440587.5)
                    val c = cos(theta)
                    val s = sin(theta)
                    var pos =
                        Vec3(
                            (c * r[0] + s * r[1]) * 1000,
                            (-s * r[0] + c * r[1]) * 1000,
                            r[2] * 1000,
                        )
                    var delta = pos - receiver
                    // Earth rotates during signal transit (first-order Sagnac correction).
                    val rotation = 7.2921151467e-5 * delta.norm() / SPEED_OF_LIGHT
                    pos =
                        Vec3(
                            cos(rotation) * pos.x + sin(rotation) * pos.y,
                            -sin(rotation) * pos.x + cos(rotation) * pos.y,
                            pos.z,
                        )
                    delta = pos - receiver
                    val range = delta.norm()
                    val local = enu(delta)
                    val elevation = asin((local.z / range).coerceIn(-1.0, 1.0))
                    if (Math.toDegrees(elevation) < config.gnss.elevationMaskDegrees)
                        return@mapNotNull null
                    val azimuth = (Math.toDegrees(atan2(local.x, local.y)) + 360) % 360
                    val satelliteVelocity =
                        Vec3(
                            (c * v[0] + s * v[1]) * 1000 + 7.2921151467e-5 * pos.y,
                            (-s * v[0] + c * v[1]) * 1000 - 7.2921151467e-5 * pos.x,
                            v[2] * 1000,
                        )
                    val localVelocity = enu(satelliteVelocity) - frame.velocity
                    val rate =
                        (localVelocity.x * local.x +
                            localVelocity.y * local.y +
                            localVelocity.z * local.z) / range
                    val phase = (config.seed xor orbit.catalogId.toLong()).toDouble() * .017
                    val smooth =
                        .65 * sin(seconds * .19 + phase) + .35 * sin(seconds * .43 + phase * 1.7)
                    val cn0 =
                        (config.gnss.cn0DbHz - 12 * (1 - sin(elevation)) - attenuation +
                                config.gnss.variationDb * smooth)
                            .coerceIn(0.0, 55.0)
                    val sigma = (.35 * 10.0.pow((42 - cn0) / 20)).coerceIn(.08, 1000.0)
                    val error =
                        sigma *
                            (.7 * sin(seconds * .7 + phase) + .3 * sin(seconds * 1.7 + phase * .9))
                    val ionosphere = 4.0 / sin(elevation).coerceAtLeast(.1)
                    val clockMeters = biasNs * SPEED_OF_LIGHT * 1e-9
                    SatelliteObservation(
                        orbit.constellation,
                        orbit.svid,
                        orbit.catalogId,
                        azimuth,
                        Math.toDegrees(elevation),
                        cn0,
                        range,
                        rate,
                        range + clockMeters + ionosphere + error,
                        sigma,
                        range + clockMeters - ionosphere + error * .02,
                        if (orbit.constellation == 5) 1561098000.0 else 1575420000.0,
                        cn0 >= 25 && frame.observedLocation != null,
                    )
                }
                .sortedWith(compareBy({ it.constellation }, { it.svid }))
        return GnssEpoch(utcMillis, frame.measurementElapsedNs, biasNs, satellites)
    }
}
