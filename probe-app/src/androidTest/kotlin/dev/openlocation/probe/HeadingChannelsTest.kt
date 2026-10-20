package dev.openlocation.probe

import android.hardware.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.openlocation.runtime.Contract
import dev.openlocation.scenario.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test

/** Independently cross-checks deprecated, fused and raw heading paths through public APIs. */
class HeadingChannelsTest {
    @Test
    fun allHeadingSourcesFollowTheRoute() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(
            android.content
                .Intent(context, ProbeActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val resolver = context.contentResolver
        fun call(method: String, value: String? = null) =
            resolver.call(Contract.URI, method, value, null)!!
        val scene =
            DemoScenes.create(Locomotion.RUN)
                .copy(headingVariationRadians = 0.0, environmentSegments = emptyList())
        val manager = context.getSystemService(SensorManager::class.java)
        val thread = HandlerThread("heading-verification").apply { start() }
        val received = ConcurrentHashMap<Int, FloatArray>()
        val errors = CopyOnWriteArrayList<String>()
        val expectedTypes = setOf(3, 11, 14, 15, 16, 20, 35)
        val sensors = manager.getSensorList(Sensor.TYPE_ALL).filter { it.type in expectedTypes }
        val listener =
            object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                override fun onSensorChanged(event: SensorEvent) {
                    received[event.sensor.type] = event.values.copyOf()
                    if (
                        event.sensor.type in setOf(14, 16, 35) &&
                            event.values.drop(3).any { kotlin.math.abs(it) > .0001f }
                    )
                        errors.add("Nonzero ideal bias")
                }
            }
        try {
            call("stop")
            call("configure", SceneCodec.encode(scene))
            call("start")
            SystemClock.sleep(700)
            sensors.forEach {
                assertTrue(manager.registerListener(listener, it, 20_000, Handler(thread.looper)))
            }
            SystemClock.sleep(2000)
            assertTrue(
                "Missing heading sensor types: ${expectedTypes - received.keys}",
                received.keys.containsAll(expectedTypes),
            )
            val matrix = FloatArray(9)
            val vector = received.getValue(11)
            SensorManager.getRotationMatrixFromVector(matrix, vector)
            val angles = SensorManager.getOrientation(matrix, FloatArray(3))
            val reference = (Math.toDegrees(angles[0].toDouble()) + 360) % 360
            fun difference(a: Double, b: Double) = kotlin.math.abs((a - b + 540) % 360 - 180)
            assertTrue(
                "Legacy azimuth diverged",
                difference(reference, received.getValue(3)[0].toDouble()) < 8,
            )
            for (type in listOf(15, 20)) {
                SensorManager.getRotationMatrixFromVector(matrix, received.getValue(type))
                val heading =
                    Math.toDegrees(
                        SensorManager.getOrientation(matrix, FloatArray(3))[0].toDouble()
                    )
                assertTrue("Rotation source $type diverged", difference(reference, heading) < 8)
            }
            assertTrue(errors.joinToString(), errors.isEmpty())
            println(
                "Heading sources 3/11/14/15/16/20/35: coherent azimuth, complete zero-bias payloads, no target-process hooks"
            )
        } finally {
            manager.unregisterListener(listener)
            call("stop")
            thread.quitSafely()
        }
    }
}
