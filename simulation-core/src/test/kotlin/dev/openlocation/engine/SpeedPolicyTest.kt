package dev.openlocation.engine

import dev.openlocation.scenario.*
import kotlin.test.*
import kotlinx.serialization.json.*

class SpeedPolicyTest {
    private fun drivingScene() =
        SceneConfig(
            routeWgs84 = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)),
            locomotion = Locomotion.CAR,
            phonePlacement = PhonePlacement.MOUNT,
            speedMetersPerSecond = 35.0,
            routeDeviationMeters = 1.5,
            routeDeviationWavelengthMeters = 60.0,
        )

    @Test
    fun optionalCurvePolicyAllowsConfiguredCruiseSpeedWithoutRemovingStartsOrStops() {
        val scene = drivingScene()
        val limited = SceneEngine(scene)
        val unrestricted = SceneEngine(scene.copy(curveSpeedLimitEnabled = false))
        val limitedPeak =
            (1..400).maxOf {
                limited.sample(it * 100_000_000L).velocity.norm()
            }
        assertTrue(
            limitedPeak < 30.0,
            "Path variation must still participate in enabled curve limiting",
        )
        val cruise = unrestricted.sample(20_000_000_000L)
        assertEquals(35.0, cruise.velocity.norm(), 1e-8)
        assertEquals(35.0, cruise.observedLocation!!.speed, 1e-8)
        assertEquals(4.0, unrestricted.sample(2_000_000_000L).velocity.norm(), 1e-8)
        val stopped = unrestricted.sample(((unrestricted.durationSeconds + 1) * 1e9).toLong())
        assertTrue(stopped.finished)
        assertEquals(unrestricted.lengthMeters, stopped.progressMeters, 0.02)
        assertTrue(stopped.velocity.norm() < 0.05)
    }

    @Test
    fun highwayTargetAboveTheFormerSliderCeilingReachesTheLocationOutput() {
        val scene =
            drivingScene()
                .copy(
                    routeWgs84 = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.05)),
                    routeDeviationMeters = 0.0,
                    speedMetersPerSecond = SceneConfig.MAX_SPEED_METERS_PER_SECOND,
                    curveSpeedLimitEnabled = false,
                )
        val cruise = SceneEngine(scene).sample(40_000_000_000L)
        assertEquals(70.0, cruise.velocity.norm(), 1e-8)
        assertEquals(70.0, cruise.observedLocation!!.speed, 1e-8)
    }

    @Test
    fun legacyScenesKeepCurveLimitingAndTheChoiceSurvivesSerialization() {
        val scene = drivingScene()
        val legacy =
            JsonObject(
                SceneCodec.json.parseToJsonElement(SceneCodec.encode(scene)).jsonObject -
                    "curveSpeedLimitEnabled"
            )
        assertTrue(SceneCodec.decode(legacy.toString()).curveSpeedLimitEnabled)
        val restored =
            SceneCodec.decode(SceneCodec.encode(scene.copy(curveSpeedLimitEnabled = false)))
        assertFalse(restored.curveSpeedLimitEnabled)
        assertEquals(35.0, restored.speedMetersPerSecond)
    }
}
