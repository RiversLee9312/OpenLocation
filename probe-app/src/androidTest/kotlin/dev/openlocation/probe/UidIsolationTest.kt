package dev.openlocation.probe

import android.hardware.*
import android.location.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.openlocation.engine.SceneEngine
import dev.openlocation.runtime.Contract
import dev.openlocation.scenario.*
import java.util.concurrent.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Build with -PobserverSuffix=.observer: a separate, unselected UID exercises normal Android APIs.
 */
class UidIsolationTest {
    @Test
    fun unselectedUidReceivesPhysicalDataDuringAnotherUidSimulation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".observer"))
        context.startActivity(
            android.content
                .Intent(context, ProbeActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        SystemClock.sleep(500)
        val resolver = context.contentResolver
        fun call(method: String, arg: String? = null) =
            resolver.call(Contract.URI, method, arg, null)!!
        val thread = HandlerThread("isolation-test").apply { start() }
        val sensors = context.getSystemService(SensorManager::class.java)
        val locations = context.getSystemService(LocationManager::class.java)
        val errors = CopyOnWriteArrayList<Double>()
        val fixes = CopyOnWriteArrayList<Location>()
        call("stop")
        val scene = DemoScenes.create(Locomotion.RUN).copy(environmentSegments = emptyList())
        call("configure", SceneCodec.encode(scene))
        val session = call("start").getString("session")!!.let(SceneCodec::decodeSession)
        val engine = SceneEngine(session.scene)
        val listener =
            object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                override fun onSensorChanged(event: SensorEvent) {
                    val expected =
                        engine
                            .sample(session.progressNs(event.timestamp), event.timestamp)
                            .accelerometer
                    errors.add(
                        kotlin.math.abs(event.values[0] - expected.x) +
                            kotlin.math.abs(event.values[1] - expected.y) +
                            kotlin.math.abs(event.values[2] - expected.z)
                    )
                }
            }
        val gps = LocationListener { fixes.add(it) }
        try {
            assertFalse(
                call("snapshot").getIntArray("targets")!!.contains(android.os.Process.myUid())
            )
            assertNull(System.getProperty(Contract.HOOK_PROPERTY))
            sensors.registerListener(
                listener,
                sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                20_000,
                Handler(thread.looper),
            )
            locations.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000,
                0f,
                gps,
                thread.looper,
            )
            SystemClock.sleep(6000)
            assertTrue("Physical sensor samples missing", errors.size > 100)
            assertTrue("Unselected UID received synthetic motion", errors.average() > .5)
            println("Unselected UID: physical IMU=${errors.size}; synthetic motion isolation PASS")
            // A post-upgrade cold GNSS start can take longer than the motion sample window.
            // Keep the GPS assertion strict; waiting is not evidence of a physical fix.
            val gpsDeadline = SystemClock.elapsedRealtime() + 60_000
            while (fixes.isEmpty() && SystemClock.elapsedRealtime() < gpsDeadline) {
                SystemClock.sleep(250)
            }
            assertTrue("Physical GPS fix missing", fixes.isNotEmpty())
            assertTrue(
                "Unselected UID received selected UID route",
                fixes.all { fix ->
                    val distances = FloatArray(1)
                    Location.distanceBetween(
                        fix.latitude,
                        fix.longitude,
                        scene.routeWgs84.first().latitude,
                        scene.routeWgs84.first().longitude,
                        distances,
                    )
                    distances[0] > 10_000
                },
            )
            println(
                "Unselected UID: physical IMU=${errors.size}, physical GPS=${fixes.size}; route isolation PASS"
            )
        } finally {
            sensors.unregisterListener(listener)
            locations.removeUpdates(gps)
            call("stop")
            thread.quitSafely()
        }
    }
}
