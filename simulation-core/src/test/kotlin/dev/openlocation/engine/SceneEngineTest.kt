package dev.openlocation.engine

import dev.openlocation.scenario.*
import kotlin.test.*

class SceneEngineTest {
    @Test
    fun phoneForwardTracksMotionWithBoundedConfigurableHeadingVariation() {
        for (placement in PhonePlacement.entries) for (mode in
            listOf(Locomotion.WALK, Locomotion.RUN, Locomotion.CAR)) {
            val amplitude = .08
            val config =
                DemoScenes.create(mode)
                    .copy(
                        phonePlacement = placement,
                        headingVariationRadians = amplitude,
                        environmentSegments = emptyList(),
                        routeDeviationMeters = 3.0,
                    )
            val engine = SceneEngine(config)
            val aligned = SceneEngine(config.copy(headingVariationRadians = 0.0))
            var maximum = 0.0
            for (second in 5..35) {
                val ns = second * 1_000_000_000L
                val frame = engine.sample(ns)
                val forward = frame.orientation.rotate(Vec3(0.0, 1.0, 0.0))
                val delta =
                    kotlin.math.atan2(
                        forward.x * frame.velocity.y - forward.y * frame.velocity.x,
                        forward.x * frame.velocity.x + forward.y * frame.velocity.y,
                    )
                maximum = maxOf(maximum, kotlin.math.abs(delta))
                assertTrue(kotlin.math.abs(delta) <= amplitude + 1e-9)
                val exact = aligned.sample(ns)
                val axis = exact.orientation.rotate(Vec3(0.0, 1.0, 0.0))
                assertEquals(0.0, axis.x * exact.velocity.y - axis.y * exact.velocity.x, 1e-8)
                assertTrue(axis.x * exact.velocity.x + axis.y * exact.velocity.y > 0)
                val magnetic = frame.orientation.rotate(frame.magneticField)
                assertEquals(22.0, magnetic.y, 1e-8)
            }
            assertTrue(maximum > .005, "Heading variation must respond to its configured amplitude")
        }
    }

    private fun scene(mode: Locomotion = Locomotion.WALK) =
        SceneConfig(routeWgs84 = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)), locomotion = mode)

    @Test
    fun reproducibleAcrossConsumersAndReadOrder() {
        val a = SceneEngine(scene())
        val b = SceneEngine(scene())
        a.sample(42_000_000_000)
        assertEquals(a.sample(10_000_000_000), b.sample(10_000_000_000))
    }

    @Test
    fun gravityAndFusionAgree() {
        val frame = SceneEngine(scene(Locomotion.STILL)).sample(500_000_000)
        assertEquals(9.80665, frame.accelerometer.norm(), 1e-8)
        assertEquals(0.0, frame.linearAcceleration.norm(), 1e-8)
        assertEquals(0.0, frame.gyroscope.norm(), 1e-8)
        assertEquals(Vec3(0.0, 0.0, 9.80665), frame.orientation.rotate(frame.gravity))
    }

    @Test
    fun vehiclesNeverCreateStepsAndWalkingFollowsDistance() {
        for (mode in listOf(Locomotion.CAR, Locomotion.BICYCLE)) assertEquals(
            1000L,
            SceneEngine(scene(mode)).sample(20_000_000_000).stepCounter,
        )
        val s = scene()
        val frame = SceneEngine(s).sample(20_000_000_000)
        assertEquals(
            (frame.progressMeters / s.strideMeters).toLong() + s.initialStepCounter,
            frame.stepCounter,
        )
    }

    @Test
    fun tunnelStopsObservationButKeepsMotion() {
        val e =
            SceneEngine(
                scene(Locomotion.CAR)
                    .copy(
                        environmentSegments =
                            listOf(EnvironmentSegment(2.0, 5.0, Environment.TUNNEL))
                    )
            )
        assertNull(e.sample(3_000_000_000).observedLocation)
        assertTrue(e.sample(4_000_000_000).progressMeters > e.sample(3_000_000_000).progressMeters)
        assertNotNull(e.sample(6_000_000_000).observedLocation)
    }

    @Test
    fun pauseUsesLiveTimestampsAndFrozenProgress() {
        val s = SessionSnapshot(scene(), true, true, 100, 200, 0)
        assertEquals(100L, s.progressNs(300))
        assertEquals(100L, s.progressNs(400))
        val resumed = s.copy(paused = false, pausedTotalNs = 200)
        assertEquals(150L, resumed.progressNs(450))
    }

    @Test
    fun formatRoundTripAndValidation() {
        val s = scene()
        assertEquals(s, SceneCodec.decode(SceneCodec.encode(s)))
        assertFailsWith<IllegalArgumentException> { s.copy(speedMetersPerSecond = -1.0) }
        assertFailsWith<IllegalArgumentException> {
            s.copy(routeWgs84 = listOf(GeoPoint(0.0, 0.0), GeoPoint(5.0, 5.0))).let(::SceneEngine)
        }
    }

    @Test
    fun straightAccelerationBrakingAndStopHaveCorrectSigns() {
        val engine =
            SceneEngine(
                scene(Locomotion.CAR)
                    .copy(
                        speedMetersPerSecond = 6.0,
                        speedPlan =
                            listOf(
                                SpeedKnot(0.0, 6.0),
                                SpeedKnot(10.0, 6.0),
                                SpeedKnot(13.0, 0.0),
                                SpeedKnot(16.0, 0.0),
                                SpeedKnot(20.0, 6.0),
                            ),
                    )
            )
        fun acceleration(t: Double): Vec3 {
            val f = engine.sample((t * 1e9).toLong())
            return f.orientation.rotate(f.linearAcceleration)
        }
        assertTrue(acceleration(1.0).x > 1.9)
        assertTrue(kotlin.math.abs(acceleration(6.0).x) < 0.01)
        assertTrue(acceleration(11.0).x < -1.9)
        assertEquals(0.0, engine.sample(15_000_000_000).velocity.norm(), 0.01)
        assertTrue(engine.sample(21_000_000_000).velocity.norm() > 0.1)
    }

    @Test
    fun turningHasConsistentLateralAccelerationAndOrientation() {
        val radius = 100.0
        val points =
            (0..32).map { i ->
                val a = i * kotlin.math.PI / 32
                GeoPoint(
                    Math.toDegrees(radius * kotlin.math.sin(a) / 6378137.0),
                    Math.toDegrees(radius * kotlin.math.cos(a) / 6378137.0),
                )
            }
        val engine =
            SceneEngine(scene(Locomotion.CAR).copy(routeWgs84 = points, speedMetersPerSecond = 5.0))
        val frame = engine.sample(20_000_000_000)
        val body = frame.linearAcceleration
        assertEquals(25.0 / radius, kotlin.math.abs(body.x), 0.04)
        assertEquals(5.0 / radius, kotlin.math.abs(frame.gyroscope.z), 0.01)
    }

    @Test
    fun rotatedPhoneKeepsTheSameWorldGravityAndZeroSpeedStaysActive() {
        val f =
            SceneEngine(scene(Locomotion.STILL).copy(phonePlacement = PhonePlacement.POCKET))
                .sample(10_000_000_000)
        assertTrue(kotlin.math.abs(f.gravity.x) > 5)
        assertEquals(9.80665, f.orientation.rotate(f.gravity).z, 1e-8)
        assertFalse(
            SceneEngine(scene().copy(speedMetersPerSecond = 0.0)).sample(10_000_000_000).finished
        )
    }

    @Test
    fun seededSpeedStrideAndArmVariationsRemainCoherent() {
        val config =
            scene()
                .copy(
                    speedVariationMetersPerSecond = 0.3,
                    strideVariationMeters = 0.12,
                    armSwingVariationRadians = 0.15,
                )
        val engine = SceneEngine(config)
        val other = SceneEngine(config)
        val speeds = (500..2000).step(10).map { engine.sample(it * 10_000_000L).velocity.norm() }
        assertTrue(speeds.all { it in 1.09..1.71 })
        assertTrue(speeds.max() - speeds.min() > 0.1)
        assertEquals(engine.sample(12_340_000_000), other.sample(12_340_000_000))
        assertTrue((0..100).all { engine.strideAtSeconds(it / 10.0) in 0.63..0.87 })
        val f = engine.sample(8_000_000_000)
        assertEquals(9.80665, f.gravity.norm(), 1e-8)
        assertEquals(f.accelerometer, f.gravity + f.linearAcceleration)
        assertNotEquals(
            f.gyroscope,
            SceneEngine(config.copy(armSwingVariationRadians = 0.0))
                .sample(8_000_000_000)
                .gyroscope,
        )
    }

    @Test
    fun physicalAndObservedPathsRespectConfiguredCorridor() {
        val config =
            scene(Locomotion.CAR)
                .copy(
                    speedMetersPerSecond = 6.0,
                    routeDeviationMeters = 4.0,
                    routeDeviationWavelengthMeters = 70.0,
                )
        val engine = SceneEngine(config)
        for (i in 1..1000) {
            val frame = engine.sample(i * 100_000_000L)
            val y = Math.toRadians(frame.truePosition.latitude) * 6378137.0
            assertTrue(kotlin.math.abs(y) <= 2.80001)
            val observation = frame.observedLocation!!
            val dy = Math.toRadians(observation.position.latitude) * 6378137.0
            val dx =
                Math.toRadians(observation.position.longitude - frame.truePosition.longitude) *
                    6378137.0
            assertTrue(kotlin.math.sqrt(dx * dx + dy * dy) <= 4.00001)
        }
    }
}
