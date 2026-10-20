package dev.openlocation.scenario

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Versioned wire format. Coordinates and altitudes always use WGS84, SI units. */
@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double, val altitude: Double = 0.0) {
    init {
        require(latitude.isFinite() && latitude in -85.0..85.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(altitude.isFinite())
    }
}

@Serializable
enum class Locomotion {
    STILL,
    WALK,
    RUN,
    BICYCLE,
    CAR,
}

@Serializable
enum class PhonePlacement {
    HAND,
    POCKET,
    BACKPACK,
    MOUNT,
}

@Serializable
enum class Environment {
    OPEN,
    URBAN,
    COVERED,
    TUNNEL,
    RECOVERY,
}

@Serializable
data class EnvironmentSegment(
    val startSeconds: Double,
    val endSeconds: Double,
    val environment: Environment,
)

@Serializable data class SpeedKnot(val seconds: Double, val metersPerSecond: Double)

/** Zenith carrier-to-noise density and bounded signal variation, in dB-Hz / dB. */
@Serializable
data class GnssProfile(
    val cn0DbHz: Double = 44.0,
    val variationDb: Double = 3.0,
    val elevationMaskDegrees: Double = 5.0,
    val orbitalCatalogOmm: String? = null,
) {
    init {
        require(cn0DbHz.isFinite() && cn0DbHz in 0.0..55.0)
        require(variationDb.isFinite() && variationDb in 0.0..20.0)
        require(elevationMaskDegrees.isFinite() && elevationMaskDegrees in 0.0..45.0)
        require(orbitalCatalogOmm == null || orbitalCatalogOmm.length <= 200_000)
    }
}

/** Per-mode editor state; all random amplitudes are bounded, seeded continuous variations. */
@Serializable
data class MotionProfile(
    val speed: Double,
    val speedVariation: Double = 0.0,
    val variationPeriodSeconds: Double = 8.0,
    val stride: Double = 0.75,
    val strideVariation: Double = 0.0,
    val armSwingRadians: Double = 0.15,
    val armSwingVariationRadians: Double = 0.0,
    val placement: PhonePlacement = PhonePlacement.HAND,
    val headingVariationRadians: Double = 0.035,
)

@Serializable
data class OutputRates(val imuHz: Int = 100, val locationHz: Int = 2) {
    init {
        require(imuHz in 1..100)
        require(locationHz in 1..10)
    }
}

@Serializable
data class SceneConfig(
    val schemaVersion: Int = 1,
    val sceneId: String = "default",
    val revision: Long = 1,
    val seed: Long = 20261019,
    val routeWgs84: List<GeoPoint>,
    val routeDeviationMeters: Double = 0.0,
    val routeDeviationWavelengthMeters: Double = 60.0,
    val curveSpeedLimitEnabled: Boolean = true,
    val locomotion: Locomotion = Locomotion.WALK,
    val phonePlacement: PhonePlacement = PhonePlacement.HAND,
    val speedMetersPerSecond: Double = 1.4,
    val speedVariationMetersPerSecond: Double = 0.0,
    val variationPeriodSeconds: Double = 8.0,
    val speedPlan: List<SpeedKnot> = emptyList(),
    val environmentSegments: List<EnvironmentSegment> = emptyList(),
    val outputRates: OutputRates = OutputRates(),
    val initialStepCounter: Long = 1000,
    val strideMeters: Double = 0.75,
    val strideVariationMeters: Double = 0.0,
    val armSwingRadians: Double = 0.15,
    val armSwingVariationRadians: Double = 0.0,
    val profiles: Map<Locomotion, MotionProfile> = emptyMap(),
    val gnss: GnssProfile = GnssProfile(),
    val headingVariationRadians: Double = 0.035,
) {
    init {
        require(schemaVersion == 1) { "Unsupported schema version" }
        require(routeWgs84.size in 2..10000)
        require(routeDeviationMeters.isFinite() && routeDeviationMeters in 0.0..50.0)
        require(
            routeDeviationWavelengthMeters.isFinite() &&
                routeDeviationWavelengthMeters in 10.0..500.0
        )
        require(revision >= 1 && initialStepCounter >= 0)
        require(speedMetersPerSecond.isFinite() && speedMetersPerSecond in 0.0..70.0)
        require(
            speedVariationMetersPerSecond.isFinite() && speedVariationMetersPerSecond in 0.0..20.0
        )
        require(variationPeriodSeconds.isFinite() && variationPeriodSeconds in 2.0..120.0)
        require(strideMeters.isFinite() && strideMeters in 0.2..2.5)
        require(
            strideVariationMeters.isFinite() && strideVariationMeters in 0.0..strideMeters * 0.75
        )
        require(armSwingRadians.isFinite() && armSwingRadians in 0.0..1.2)
        require(armSwingVariationRadians.isFinite() && armSwingVariationRadians in 0.0..0.6)
        require(headingVariationRadians.isFinite() && headingVariationRadians in 0.0..0.262)
        require(speedPlan.zipWithNext().all { (a, b) -> a.seconds < b.seconds })
        require(
            speedPlan.all {
                it.seconds.isFinite() &&
                    it.seconds >= 0 &&
                    it.metersPerSecond.isFinite() &&
                    it.metersPerSecond in 0.0..70.0
            }
        )
        require(
            environmentSegments.all {
                it.startSeconds.isFinite() &&
                    it.endSeconds.isFinite() &&
                    it.startSeconds >= 0 &&
                    it.endSeconds > it.startSeconds
            }
        )
        require(
            environmentSegments
                .sortedBy { it.startSeconds }
                .zipWithNext()
                .all { (a, b) -> a.endSeconds <= b.startSeconds }
        )
    }

    fun profile() =
        MotionProfile(
            speedMetersPerSecond,
            speedVariationMetersPerSecond,
            variationPeriodSeconds,
            strideMeters,
            strideVariationMeters,
            armSwingRadians,
            armSwingVariationRadians,
            phonePlacement,
            headingVariationRadians,
        )

    fun withProfile(mode: Locomotion, profile: MotionProfile) =
        copy(
            locomotion = mode,
            speedMetersPerSecond = profile.speed,
            speedVariationMetersPerSecond = profile.speedVariation,
            variationPeriodSeconds = profile.variationPeriodSeconds,
            strideMeters = profile.stride,
            strideVariationMeters = profile.strideVariation,
            armSwingRadians = profile.armSwingRadians,
            armSwingVariationRadians = profile.armSwingVariationRadians,
            phonePlacement = profile.placement,
            headingVariationRadians = profile.headingVariationRadians,
        )
}

/** Scene progress freezes on pause; delivery timestamps always use the live monotonic clock. */
@Serializable
data class SessionSnapshot(
    val scene: SceneConfig,
    val running: Boolean = false,
    val paused: Boolean = false,
    val startElapsedNs: Long = 0,
    val pausedAtElapsedNs: Long = 0,
    val pausedTotalNs: Long = 0,
    val startUtcMillis: Long = 0,
) {
    fun utcMillis(nowNs: Long) = startUtcMillis + (nowNs - startElapsedNs) / 1_000_000

    fun progressNs(nowNs: Long): Long =
        if (!running) 0
        else
            ((if (paused) pausedAtElapsedNs else nowNs) - startElapsedNs - pausedTotalNs)
                .coerceAtLeast(0)
}

object SceneCodec {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(scene: SceneConfig): String = json.encodeToString(SceneConfig.serializer(), scene)

    fun decode(text: String): SceneConfig = json.decodeFromString(SceneConfig.serializer(), text)

    fun encodeSession(session: SessionSnapshot): String =
        json.encodeToString(SessionSnapshot.serializer(), session)

    fun decodeSession(text: String): SessionSnapshot =
        json.decodeFromString(SessionSnapshot.serializer(), text)
}

object DemoScenes {
    fun route() =
        listOf(
            GeoPoint(31.2304, 121.4737, 8.0),
            GeoPoint(31.2316, 121.4746, 9.0),
            GeoPoint(31.2326, 121.4764, 10.0),
            GeoPoint(31.2318, 121.4781, 9.0),
            GeoPoint(31.2300, 121.4773, 8.0),
            GeoPoint(31.2304, 121.4737, 8.0),
        )

    fun create(mode: Locomotion = Locomotion.WALK, revision: Long = 1) =
        SceneConfig(
            routeWgs84 = route(),
            locomotion = mode,
            revision = revision,
            phonePlacement =
                if (mode == Locomotion.CAR) PhonePlacement.MOUNT else PhonePlacement.HAND,
            speedMetersPerSecond =
                when (mode) {
                    Locomotion.STILL -> 0.0
                    Locomotion.WALK -> 1.4
                    Locomotion.RUN -> 3.0
                    Locomotion.BICYCLE -> 5.5
                    Locomotion.CAR -> 12.0
                },
            strideMeters = if (mode == Locomotion.RUN) 1.15 else 0.75,
            environmentSegments =
                listOf(
                    EnvironmentSegment(0.0, 20.0, Environment.OPEN),
                    EnvironmentSegment(20.0, 45.0, Environment.URBAN),
                    EnvironmentSegment(45.0, 65.0, Environment.TUNNEL),
                    EnvironmentSegment(65.0, 80.0, Environment.RECOVERY),
                ),
        )
}
