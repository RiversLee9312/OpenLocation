package dev.openlocation.engine

import dev.openlocation.scenario.*
import kotlin.math.*

data class LocationObservation(
    val position: GeoPoint,
    val accuracyMeters: Double,
    val speed: Double,
    val bearingDegrees: Double,
)

data class SampleFrame(
    val revision: Long,
    val sequence: Long,
    val measurementElapsedNs: Long,
    val deliveryElapsedNs: Long,
    val truePosition: GeoPoint,
    val velocity: Vec3,
    val orientation: Quaternion,
    val observedLocation: LocationObservation?,
    val environment: Environment,
    val accelerometer: Vec3,
    val gyroscope: Vec3,
    val gravity: Vec3,
    val linearAcceleration: Vec3,
    val magneticField: Vec3,
    val stepCounter: Long,
    val progressMeters: Double,
    val finished: Boolean,
    val measurementUtcMillis: Long = 0,
)

/**
 * Stateless random-access evaluation on a fixed 10 ms dynamics timeline. Independent readers see
 * identical samples. Noise is seeded continuous harmonic noise, a reproducible prototype, not a
 * calibrated hardware or satellite constellation model.
 */
class SceneEngine(val config: SceneConfig) {
    private val route =
        Route(
            config.routeWgs84,
            config.routeDeviationMeters,
            config.routeDeviationWavelengthMeters,
            config.seed,
        )
    val nominalRouteWgs84 = route.nominalPreview()

    private data class Tick(val distance: Double, val speed: Double, val steps: Double)

    private val dt = 0.01
    private val ticks: List<Tick>
    val lengthMeters
        get() = route.length

    val durationSeconds
        get() = (ticks.size - 1) * dt

    init {
        val acceleration =
            when (config.locomotion) {
                Locomotion.CAR -> 2.0
                Locomotion.BICYCLE -> 1.0
                else -> 1.5
            }
        ticks = buildList {
            var s = 0.0
            var v = 0.0
            var steps = 0.0
            add(Tick(s, v, steps))
            val maxSeconds =
                if (
                    config.locomotion == Locomotion.STILL ||
                        config.speedMetersPerSecond == 0.0 && config.speedPlan.isEmpty()
                )
                    1.0
                else 14400.0
            for (i in 1..(maxSeconds / dt).toInt()) {
                val t = i * dt
                var desired = desiredSpeed(t)
                if (config.curveSpeedLimitEnabled) {
                    val curve = abs(route.at(s).curvature)
                    if (curve > 1e-6) desired = min(desired, sqrt(2.5 / curve))
                }
                desired =
                    min(desired, sqrt(2 * acceleration * (route.length - s).coerceAtLeast(0.0)))
                val next =
                    (v + (desired - v).coerceIn(-acceleration * dt, acceleration * dt))
                        .coerceAtLeast(0.0)
                val ds = (v + next) * 0.5 * dt
                s = (s + ds).coerceAtMost(route.length)
                v = next
                if (config.locomotion == Locomotion.WALK || config.locomotion == Locomotion.RUN)
                    steps += ds / strideAtSeconds(t - dt / 2)
                add(Tick(s, v, steps))
                if (s >= route.length - 0.01 && v < 0.05) break
            }
        }
    }

    private fun desiredSpeed(t: Double): Double {
        val baseline = baselineSpeed(t)
        if (baseline <= 0) return 0.0
        return (baseline + config.speedVariationMetersPerSecond * variation(t, 11)).coerceIn(
            0.0,
            SceneConfig.MAX_SPEED_METERS_PER_SECOND,
        )
    }

    private fun baselineSpeed(t: Double): Double {
        if (config.locomotion == Locomotion.STILL) return 0.0
        val plan = config.speedPlan
        if (plan.isEmpty()) return config.speedMetersPerSecond
        if (t <= plan.first().seconds) return plan.first().metersPerSecond
        val i = plan.indexOfFirst { it.seconds >= t }
        if (i < 0) return plan.last().metersPerSecond
        val a = plan[i - 1]
        val b = plan[i]
        val u = (t - a.seconds) / (b.seconds - a.seconds)
        return a.metersPerSecond * (1 - u) + b.metersPerSecond * u
    }

    private fun variation(t: Double, channel: Int) =
        noise(t * 2 * PI / (0.37 * config.variationPeriodSeconds), channel)

    fun strideAtSeconds(t: Double) =
        (config.strideMeters + config.strideVariationMeters * variation(t, 12)).coerceAtLeast(0.1)

    private fun armAngle(t: Double): Double {
        if (
            config.phonePlacement != PhonePlacement.HAND ||
                config.locomotion !in listOf(Locomotion.WALK, Locomotion.RUN)
        )
            return 0.0
        val state = tick(t)
        val amplitude =
            (config.armSwingRadians + config.armSwingVariationRadians * variation(t, 13)).coerceIn(
                0.0,
                1.4,
            )
        return amplitude * min(state.speed, 1.0) * sin(state.steps * PI)
    }

    private fun armOffset(t: Double): Vec3 {
        val angle = armAngle(t)
        if (angle == 0.0) return Vec3.ZERO
        val tangent = route.at(tick(t).distance).tangent
        val heading = atan2(tangent.y, tangent.x) - PI / 2
        return Quaternion.euler(heading, 0.0, 0.0)
            .rotate(Vec3(0.0, 0.55 * sin(angle), 0.55 * (1 - cos(angle))))
    }

    private fun tick(t: Double): Tick {
        val idx = (t / dt).coerceIn(0.0, ticks.lastIndex.toDouble())
        val i = floor(idx).toInt()
        val b = ticks[(i + 1).coerceAtMost(ticks.lastIndex)]
        val a = ticks[i]
        val u = idx - i
        return Tick(
            a.distance * (1 - u) + b.distance * u,
            a.speed * (1 - u) + b.speed * u,
            a.steps * (1 - u) + b.steps * u,
        )
    }

    private fun orientation(t: Double): Quaternion {
        val state = tick(t)
        val path = route.at(state.distance)
        val walking = config.locomotion == Locomotion.WALK || config.locomotion == Locomotion.RUN
        val phase = state.steps * 2 * PI
        val sway = if (walking) min(state.speed, 1.0) * 0.05 else 0.0
        val pitch =
            when (config.phonePlacement) {
                PhonePlacement.POCKET -> 1.25
                PhonePlacement.BACKPACK -> 0.4
                else -> 0.0
            }
        // Phone +Y points forward. ENU yaw is east counterclockwise.
        val yaw = atan2(path.tangent.y, path.tangent.x) - PI / 2
        val headingVariation =
            config.headingVariationRadians * variation(t, 14) * min(state.speed, 1.0)
        val pitchAngle = pitch + sway * sin(phase) + armAngle(t)
        val rollAngle = sway * cos(phase)
        val tiltedForward = Quaternion.euler(0.0, pitchAngle, rollAngle).rotate(Vec3(0.0, 1.0, 0.0))
        val tiltHeading = atan2(tiltedForward.y, tiltedForward.x) - PI / 2
        return Quaternion.euler(
            yaw + headingVariation - tiltHeading,
            pitchAngle,
            rollAngle,
        )
    }

    private fun noise(t: Double, channel: Int): Double {
        val phase =
            ((config.seed xor (channel.toLong() * 0x9e3779b9L)) and 0xffff).toDouble() / 65536 *
                2 *
                PI
        return (sin(t * 0.37 + phase) +
            0.55 * sin(t * 1.17 + phase * 2.17) +
            0.2 * sin(t * 4.31 + phase * 0.83)) / 1.75
    }

    fun sample(
        progressNs: Long,
        deliveryElapsedNs: Long = progressNs,
        utcMillis: Long = 0,
    ): SampleFrame {
        val t = progressNs.coerceAtLeast(0) / 1e9
        val state = tick(t)
        val path = route.at(state.distance)
        val velocity = path.tangent * state.speed
        val prev = tick((t - dt).coerceAtLeast(0.0))
        val next = tick(t + dt)
        val pv = route.at(prev.distance).tangent * prev.speed
        val nv = route.at(next.distance).tangent * next.speed
        val worldAcceleration = (nv - pv) * (1 / (if (t < dt) dt else 2 * dt))
        val q = orientation(t)
        val dq = orientation((t - dt).coerceAtLeast(0.0)).conjugate() * orientation(t + dt)
        val sign = if (dq.w < 0) -1.0 else 1.0
        val gyro = Vec3(dq.x, dq.y, dq.z) * (sign * 2 / (if (t < dt) dt else 2 * dt))
        val walking = config.locomotion == Locomotion.WALK || config.locomotion == Locomotion.RUN
        val phase = state.steps * 2 * PI
        val amp = if (walking) min(state.speed, 3.0) else 0.0
        val gait =
            Vec3(
                amp * 0.22 * sin(phase),
                amp * 0.16 * cos(phase),
                amp * 0.6 * sin(phase) + amp * 0.18 * sin(2 * phase),
            )
        val vibration =
            if (config.locomotion == Locomotion.CAR || config.locomotion == Locomotion.BICYCLE)
                Vec3(0.0, 0.0, 0.04 * min(state.speed, 5.0) * sin(t * 37))
            else Vec3.ZERO
        val armAcceleration =
            (armOffset(t + dt) - armOffset(t) * 2.0 + armOffset((t - dt).coerceAtLeast(0.0))) *
                (1 / (dt * dt))
        val linear = q.inverseRotate(worldAcceleration + armAcceleration) + gait + vibration
        val gravity = q.inverseRotate(Vec3(0.0, 0.0, 9.80665))
        val env =
            config.environmentSegments
                .firstOrNull { t >= it.startSeconds && t < it.endSeconds }
                ?.environment ?: Environment.OPEN
        val segment =
            config.environmentSegments.firstOrNull { t >= it.startSeconds && t < it.endSeconds }
        val sigma =
            when (env) {
                Environment.OPEN -> 2.0
                Environment.URBAN -> 18.0
                Environment.COVERED -> 8.0
                Environment.TUNNEL -> 0.0
                Environment.RECOVERY -> 2 + 28 * exp(-(t - (segment?.startSeconds ?: t)) / 4)
            } * 10.0.pow((44 - config.gnss.cn0DbHz) / 20)
        var observedOffset =
            path.position - path.nominalPosition +
                Vec3(noise(t, 1) * sigma, noise(t, 2) * sigma, noise(t, 3) * sigma * 0.5)
        if (config.routeDeviationMeters > 0) {
            val horizontal =
                sqrt(observedOffset.x * observedOffset.x + observedOffset.y * observedOffset.y)
            val factor = min(1.0, config.routeDeviationMeters / horizontal.coerceAtLeast(1e-9))
            observedOffset =
                Vec3(observedOffset.x * factor, observedOffset.y * factor, observedOffset.z)
        }
        val observed =
            if (env == Environment.TUNNEL || config.gnss.cn0DbHz < 18) null
            else
                LocationObservation(
                    route.projection.geo(path.nominalPosition + observedOffset),
                    sigma * 1.5,
                    state.speed,
                    (90 - Math.toDegrees(atan2(path.tangent.y, path.tangent.x)) + 360) % 360,
                )
        return SampleFrame(
            config.revision,
            (progressNs / 10_000_000),
            deliveryElapsedNs,
            deliveryElapsedNs,
            route.projection.geo(path.position),
            velocity,
            q,
            observed,
            env,
            gravity + linear,
            gyro,
            gravity,
            linear,
            q.inverseRotate(Vec3(0.0, 22.0, -42.0)),
            config.initialStepCounter + floor(state.steps + 1e-9).toLong(),
            state.distance,
            t >= durationSeconds &&
                state.distance >= route.length - 0.01 &&
                state.speed < 0.05 &&
                config.locomotion != Locomotion.STILL,
            utcMillis,
        )
    }
}
