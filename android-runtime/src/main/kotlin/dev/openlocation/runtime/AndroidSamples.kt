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
        Sensor.TYPE_MAGNETIC_FIELD -> magneticField.array()
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
    val SUPPORTED_SENSORS = setOf(1, 2, 4, 9, 10, 11, 18, 19)
}
