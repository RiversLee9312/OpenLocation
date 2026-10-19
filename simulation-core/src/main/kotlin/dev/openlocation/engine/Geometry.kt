package dev.openlocation.engine

import dev.openlocation.scenario.GeoPoint
import kotlin.math.*

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(v: Vec3) = Vec3(x + v.x, y + v.y, z + v.z)

    operator fun minus(v: Vec3) = Vec3(x - v.x, y - v.y, z - v.z)

    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)

    fun norm() = sqrt(x * x + y * y + z * z)

    fun array() = floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat())

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
    }
}

/** Hamilton quaternion, body → ENU. Android rotation-vector order is x,y,z,w. */
data class Quaternion(val w: Double, val x: Double, val y: Double, val z: Double) {
    operator fun times(q: Quaternion) =
        Quaternion(
            w * q.w - x * q.x - y * q.y - z * q.z,
            w * q.x + x * q.w + y * q.z - z * q.y,
            w * q.y - x * q.z + y * q.w + z * q.x,
            w * q.z + x * q.y - y * q.x + z * q.w,
        )

    fun conjugate() = Quaternion(w, -x, -y, -z)

    fun rotate(v: Vec3): Vec3 {
        val q = this * Quaternion(0.0, v.x, v.y, v.z) * conjugate()
        return Vec3(q.x, q.y, q.z)
    }

    fun inverseRotate(v: Vec3) = conjugate().rotate(v)

    fun androidArray() = floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat(), 0f)

    companion object {
        fun euler(yaw: Double, pitch: Double, roll: Double): Quaternion {
            val cy = cos(yaw / 2)
            val sy = sin(yaw / 2)
            val cp = cos(pitch / 2)
            val sp = sin(pitch / 2)
            val cr = cos(roll / 2)
            val sr = sin(roll / 2)
            return Quaternion(
                cr * cp * cy + sr * sp * sy,
                sr * cp * cy - cr * sp * sy,
                cr * sp * cy + sr * cp * sy,
                cr * cp * sy - sr * sp * cy,
            )
        }
    }
}

/** Local tangent approximation for short routes, explicit extent limit: 50 km from origin. */
internal class LocalProjection(val origin: GeoPoint) {
    private val r = 6378137.0

    fun enu(p: GeoPoint): Vec3 {
        val deltaLon = ((p.longitude - origin.longitude + 540) % 360) - 180
        return Vec3(
            Math.toRadians(deltaLon) * r * cos(Math.toRadians(origin.latitude)),
            Math.toRadians(p.latitude - origin.latitude) * r,
            p.altitude - origin.altitude,
        )
    }

    fun geo(p: Vec3) =
        GeoPoint(
            origin.latitude + Math.toDegrees(p.y / r),
            ((origin.longitude + Math.toDegrees(p.x / (r * cos(Math.toRadians(origin.latitude))))) +
                540) % 360 - 180,
            origin.altitude + p.z,
        )
}

internal data class PathPoint(
    val position: Vec3,
    val tangent: Vec3,
    val curvature: Double,
    val nominalPosition: Vec3,
)

/** Rounded Catmull–Rom route, resampled into an arc-length lookup. Duplicate points are removed. */
internal class Route(
    points: List<GeoPoint>,
    deviationMeters: Double = 0.0,
    wavelengthMeters: Double = 60.0,
    seed: Long = 0,
) {
    val projection = LocalProjection(points.first())
    private val samples: List<Vec3>
    private val nominalSamples: List<Vec3>
    private val distances: DoubleArray
    val length: Double
        get() = distances.last()

    init {
        val clean =
            points
                .map { projection.enu(it) }
                .fold(mutableListOf<Vec3>()) { result, p ->
                    if (result.isEmpty() || (p - result.last()).norm() > 0.1) result.add(p)
                    result
                }
        require(clean.all { it.norm() <= 50000 }) { "Route exceeds local 50 km projection limit" }
        require(clean.size >= 2) { "Route needs two distinct points" }
        require(clean.zipWithNext().sumOf { (a, b) -> (b - a).norm() } <= 50000) {
            "Route length exceeds 50 km"
        }
        nominalSamples = buildList {
            for (i in 0 until clean.lastIndex) {
                val p0 = clean[(i - 1).coerceAtLeast(0)]
                val p1 = clean[i]
                val p2 = clean[i + 1]
                val p3 = clean[(i + 2).coerceAtMost(clean.lastIndex)]
                val n = ceil((p2 - p1).norm() / 1.0).toInt().coerceIn(8, 5000)
                for (j in 0 until n) {
                    val t = j.toDouble() / n
                    val t2 = t * t
                    val t3 = t2 * t
                    add(
                        (p1 * 2.0 +
                            (p2 - p0) * t +
                            (p0 * 2.0 - p1 * 5.0 + p2 * 4.0 - p3) * t2 +
                            (p0 * (-1.0) + p1 * 3.0 - p2 * 3.0 + p3) * t3) * 0.5
                    )
                }
            }
            add(clean.last())
        }
        val nominalDistances = DoubleArray(nominalSamples.size)
        for (i in 1..nominalSamples.lastIndex) nominalDistances[i] =
            nominalDistances[i - 1] + (nominalSamples[i] - nominalSamples[i - 1]).norm()
        val phase = ((seed xor 0x6a09e667L) and 0xffff).toDouble() / 65536 * 2 * PI
        samples = nominalSamples.mapIndexed { i, p ->
            val tangent =
                nominalSamples[(i + 1).coerceAtMost(nominalSamples.lastIndex)] -
                    nominalSamples[(i - 1).coerceAtLeast(0)]
            val horizontal = sqrt(tangent.x * tangent.x + tangent.y * tangent.y).coerceAtLeast(1e-9)
            val normal = Vec3(-tangent.y / horizontal, tangent.x / horizontal, 0.0)
            val s = nominalDistances[i]
            val fade =
                sin(
                    PI / 2 *
                        minOf(1.0, s / 10, (nominalDistances.last() - s) / 10).coerceAtLeast(0.0)
                )
            val a = s * 2 * PI / wavelengthMeters
            val offset =
                deviationMeters *
                    0.7 *
                    fade *
                    (0.5 * sin(a + phase) +
                        0.35 * sin(a * 1.73 + phase * 1.31) +
                        0.15 * sin(a * 2.97 + phase * 0.57))
            p + normal * offset
        }
        distances = DoubleArray(samples.size)
        for (i in 1..samples.lastIndex) distances[i] =
            distances[i - 1] + (samples[i] - samples[i - 1]).norm()
    }

    private fun position(distance: Double, points: List<Vec3> = samples): Vec3 {
        val d = distance.coerceIn(0.0, length)
        val hit = distances.binarySearch(d)
        if (hit >= 0) return points[hit]
        val i = (-hit - 2).coerceIn(0, samples.lastIndex - 1)
        val u = (d - distances[i]) / (distances[i + 1] - distances[i]).coerceAtLeast(1e-9)
        return points[i] * (1 - u) + points[i + 1] * u
    }

    fun at(distance: Double): PathPoint {
        val p = position(distance)
        val before = position(distance - 1)
        val after = position(distance + 1)
        val tangent = (after - before).let { it * (1 / it.norm().coerceAtLeast(1e-9)) }
        val a = p - before
        val b = after - p
        val angle = atan2(a.x * b.y - a.y * b.x, a.x * b.x + a.y * b.y)
        val curvature =
            if (a.norm() < 0.1 || b.norm() < 0.1) 0.0 else angle / ((a.norm() + b.norm()) / 2)
        return PathPoint(p, tangent, curvature, position(distance, nominalSamples))
    }

    fun nominalPreview() =
        nominalSamples
            .filterIndexed { i, _ ->
                i % maxOf(1, nominalSamples.size / 600) == 0 || i == nominalSamples.lastIndex
            }
            .map(projection::geo)
}
