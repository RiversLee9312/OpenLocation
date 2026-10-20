package dev.openlocation.runtime

import android.hardware.Sensor
import android.location.Location
import dev.openlocation.engine.SampleFrame

/** Android boundary: no wall-clock values enter the simulation engine. */
fun SampleFrame.location(provider: String = "gps"): Location? =
    observedLocation?.let { observation ->
        Location(provider).apply {
            latitude = observation.position.latitude
            longitude = observation.position.longitude
            altitude = observation.position.altitude
            speed = observation.speed.toFloat()
            bearing = observation.bearingDegrees.toFloat()
            accuracy = observation.accuracyMeters.toFloat()
            verticalAccuracyMeters = (observation.accuracyMeters * 1.5).toFloat()
            speedAccuracyMetersPerSecond = 0.15f
            bearingAccuracyDegrees = 2f
            elapsedRealtimeNanos = measurementElapsedNs
            time =
                if (measurementUtcMillis > 0) measurementUtcMillis
                else
                    System.currentTimeMillis() -
                        (android.os.SystemClock.elapsedRealtimeNanos() - measurementElapsedNs) /
                            1_000_000
        }
    }

fun SampleFrame.values(type: Int): FloatArray? =
    when (type) {
        Sensor.TYPE_ACCELEROMETER -> accelerometer.array()
        Sensor.TYPE_GYROSCOPE -> gyroscope.array()
        Sensor.TYPE_GRAVITY -> gravity.array()
        Sensor.TYPE_LINEAR_ACCELERATION -> linearAcceleration.array()
        Sensor.TYPE_ROTATION_VECTOR -> orientation.androidArray()
        Sensor.TYPE_GAME_ROTATION_VECTOR -> orientation.androidArray().copyOf(4)
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> orientation.androidArray()
        Sensor.TYPE_ORIENTATION -> {
            val q = orientation
            val r01 = 2 * (q.x * q.y - q.w * q.z)
            val r11 = 1 - 2 * (q.x * q.x + q.z * q.z)
            val r20 = 2 * (q.x * q.z - q.w * q.y)
            val r21 = 2 * (q.y * q.z + q.w * q.x)
            val r22 = 1 - 2 * (q.x * q.x + q.y * q.y)
            floatArrayOf(
                ((Math.toDegrees(kotlin.math.atan2(r01, r11)) + 360) % 360).toFloat(),
                Math.toDegrees(kotlin.math.asin((-r21).coerceIn(-1.0, 1.0))).toFloat(),
                Math.toDegrees(kotlin.math.atan2(-r20, r22)).toFloat(),
            )
        }
        Sensor.TYPE_MAGNETIC_FIELD -> magneticField.array()
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> magneticField.array().copyOf(6)
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> gyroscope.array().copyOf(6)
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> accelerometer.array().copyOf(6)
        Sensor.TYPE_STEP_COUNTER -> floatArrayOf(stepCounter.toFloat())
        Sensor.TYPE_STEP_DETECTOR -> floatArrayOf(1f)
        else -> null
    }

object Contract {
    const val AUTHORITY = "dev.openlocation.app.control"
    const val PERMISSION = "dev.openlocation.permission.CONTROL"
    const val PROBE_PACKAGE = "dev.openlocation.probe"
    val URI: android.net.Uri = android.net.Uri.parse("content://$AUTHORITY/session")
    const val HOOK_PROPERTY = "dev.openlocation.hook.active"
    /**
     * Stable order shared with the native service's six-float event payload. Bias is ideal zero.
     */
    val SUPPORTED_SENSORS = setOf(1, 2, 3, 4, 9, 10, 11, 14, 15, 16, 18, 19, 20, 35)
}
