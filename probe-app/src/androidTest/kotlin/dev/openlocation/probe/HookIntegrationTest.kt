package dev.openlocation.probe

import android.hardware.*
import android.location.*
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.openlocation.engine.SceneEngine
import dev.openlocation.runtime.*
import dev.openlocation.scenario.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** End-to-end tests exercise Android consumer APIs through the installed LSPosed runtime. */
@RunWith(AndroidJUnit4::class)
class HookIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(SensorManager::class.java)
    private val thread = HandlerThread("hook-test").apply { start() }
    private val handler = Handler(thread.looper)

    private fun call(method: String, arg: String? = null) =
        context.contentResolver.call(Contract.URI, method, arg, null)

    private fun start(scene: SceneConfig = DemoScenes.create()) {
        context.startActivity(
            android.content
                .Intent(context, ProbeActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        SystemClock.sleep(500)
        assertNull(
            "Target process must not load application hooks",
            System.getProperty(Contract.HOOK_PROPERTY),
        )
        assertTrue(
            "System backend unavailable",
            call("snapshot")!!.getString("backend")!!.contains("sensors"),
        )
        call("stop")
        call("configure", SceneCodec.encode(scene))
        call("start")
        SystemClock.sleep(800)
    }

    @After
    fun finish() {
        call("stop")
        thread.quitSafely()
    }

    @Test
    fun sensorsScheduleCancelBatchFlushAndPause() {
        start()
        val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        val detector = manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)!!
        val counter = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!!
        val times = CopyOnWriteArrayList<Long>()
        val steps = AtomicInteger()
        val lastCounter = AtomicReference(0f)
        val flush = CountDownLatch(1)
        val listener =
            object : SensorEventListener2 {
                override fun onSensorChanged(e: SensorEvent) {
                    when (e.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> times.add(e.timestamp)
                        Sensor.TYPE_STEP_DETECTOR -> {
                            assertEquals(1f, e.values[0], 0f)
                            steps.incrementAndGet()
                        }
                        Sensor.TYPE_STEP_COUNTER -> lastCounter.set(e.values[0])
                    }
                }

                override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

                override fun onFlushCompleted(s: Sensor?) {
                    flush.countDown()
                }
            }
        assertTrue(manager.registerListener(listener, accel, 20000, 100000, handler))
        assertTrue(manager.registerListener(listener, detector, 20000, handler))
        assertTrue(manager.registerListener(listener, counter, 20000, handler))
        SystemClock.sleep(2500)
        assertTrue("Synthetic IMU callbacks missing", times.size >= 60)
        assertTrue("Step detector needs active scheduling on stationary hardware", steps.get() > 0)
        assertTrue(lastCounter.get() > 1000f)
        assertTrue(times.zipWithNext().all { (a, b) -> b > a })
        assertTrue(manager.flush(listener))
        assertTrue(flush.await(2, TimeUnit.SECONDS))
        call("pause")
        SystemClock.sleep(500)
        val pausedCount = times.size
        val pausedSteps = lastCounter.get()
        SystemClock.sleep(400)
        assertEquals(pausedCount, times.size)
        assertEquals(pausedSteps, lastCounter.get(), 0f)
        call("pause")
        SystemClock.sleep(700)
        assertTrue(times.size > pausedCount)
        assertTrue(times.zipWithNext().all { (a, b) -> b > a })
        manager.unregisterListener(listener)
        SystemClock.sleep(100)
        val end = times.size
        SystemClock.sleep(200)
        assertEquals(end, times.size)
        android.util.Log.i(
            "OpenLocationTest",
            "sensorSamples=${times.size} steps=${steps.get()} counter=${lastCounter.get()} unregister=PASS flush=PASS pause=PASS",
        )
    }

    @Suppress("MissingPermission")
    @Test
    fun gpsLastCurrentContinuousTunnelAndRemoval() {
        start(
            DemoScenes.create(Locomotion.CAR)
                .copy(
                    environmentSegments = listOf(EnvironmentSegment(2.0, 4.0, Environment.TUNNEL))
                )
        )
        val location = context.getSystemService(LocationManager::class.java)
        assertNotNull(location.getLastKnownLocation("gps"))
        val current = CountDownLatch(1)
        location.getCurrentLocation("gps", null, context.mainExecutor) { sample ->
            assertNotNull(sample)
            current.countDown()
        }
        assertTrue(current.await(2, TimeUnit.SECONDS))
        val callbacks = CopyOnWriteArrayList<Location>()
        val listener = LocationListener { callbacks.add(Location(it)) }
        location.requestLocationUpdates("gps", 100L, 0f, listener, thread.looper)
        SystemClock.sleep(600)
        assertTrue(callbacks.isNotEmpty())
        assertTrue(
            "Legacy GPS satellite summary missing",
            (callbacks.first().extras?.getInt("satellites", 0) ?: 0) > 0,
        )
        SystemClock.sleep(1000)
        assertNull("Tunnel must not produce fresh GPS", location.getLastKnownLocation("gps"))
        val tunnelCount = callbacks.size
        SystemClock.sleep(500)
        assertEquals(tunnelCount, callbacks.size)
        SystemClock.sleep(2200)
        assertTrue(callbacks.size > tunnelCount)
        assertTrue(
            callbacks.zipWithNext().all { (a, b) ->
                b.elapsedRealtimeNanos > a.elapsedRealtimeNanos
            }
        )
        location.removeUpdates(listener)
        SystemClock.sleep(100)
        val end = callbacks.size
        SystemClock.sleep(250)
        assertEquals(end, callbacks.size)
        android.util.Log.i(
            "OpenLocationTest",
            "gpsCallbacks=${callbacks.size} last=PASS current=PASS tunnel=PASS removal=PASS",
        )
    }

    @Test
    fun allSensorInstancesUseOneFrameAndIndependentRates() {
        start()
        val session = SceneCodec.decodeSession(call("snapshot")!!.getString("session")!!)
        val engine = SceneEngine(session.scene)
        val counts = ConcurrentHashMap<Sensor, AtomicInteger>()
        val errors = CopyOnWriteArrayList<String>()
        val verify = AtomicBoolean(true)
        val instances =
            manager.getSensorList(Sensor.TYPE_ALL).filter { it.type in Contract.SUPPORTED_SENSORS }
        val listener =
            object : SensorEventListener {
                override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

                override fun onSensorChanged(event: SensorEvent) {
                    counts.computeIfAbsent(event.sensor) { AtomicInteger() }.incrementAndGet()
                    if (!verify.get()) return
                    val expected =
                        engine
                            .sample(session.progressNs(event.timestamp), event.timestamp)
                            .values(event.sensor.type)!!
                    if (
                        event.values.size != expected.size ||
                            event.values.indices.any {
                                kotlin.math.abs(event.values[it] - expected[it]) > 0.002f
                            }
                    )
                        errors.add(
                            "instance ${event.sensor.id} type ${event.sensor.type} differs from scene"
                        )
                }
            }
        instances.forEach {
            assertTrue(
                "Registration failed ${it.name}",
                manager.registerListener(listener, it, 20000, handler),
            )
        }
        val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        val slowCount = AtomicInteger()
        val slow =
            object : SensorEventListener {
                override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

                override fun onSensorChanged(e: SensorEvent) {
                    slowCount.incrementAndGet()
                }
            }
        assertTrue(manager.registerListener(slow, accel, 40000, handler))
        SystemClock.sleep(2400)
        assertTrue(errors.take(4).toString(), errors.isEmpty())
        assertTrue(
            "Missing instances: ${instances.filter { (counts[it]?.get() ?: 0)==0 }.map { it.name }}",
            instances.all { (counts[it]?.get() ?: 0) > 0 },
        )
        val ratio = counts[accel]!!.get().toDouble() / slowCount.get()
        assertTrue("Subscription frequency ratio $ratio", ratio in 1.5..2.5)
        verify.set(false)
        call("stop")
        SystemClock.sleep(400)
        val nativeStart = counts[accel]!!.get()
        SystemClock.sleep(300)
        assertTrue("Native subscriptions did not resume", counts[accel]!!.get() > nativeStart)
        manager.unregisterListener(listener)
        manager.unregisterListener(slow)
        android.util.Log.i(
            "OpenLocationTest",
            "instances=${instances.size} types=${instances.map { it.type }.toSet().sorted()} sameFrame=PASS rateRatio=$ratio restoreNative=PASS",
        )
    }

    @Suppress("MissingPermission")
    @Test
    fun existingGpsSubscriptionTransitionsAndStepBaselinePersists() {
        call("stop")
        call("configure", SceneCodec.encode(DemoScenes.create()))
        SystemClock.sleep(600)
        val location = context.getSystemService(LocationManager::class.java)
        val count = AtomicInteger()
        val callback = LocationListener { count.incrementAndGet() }
        location.requestLocationUpdates("gps", 100L, 0f, callback, thread.looper)
        call("start")
        SystemClock.sleep(1800)
        assertTrue("Existing GPS subscription did not switch", count.get() > 0)
        val before = SceneCodec.decodeSession(call("snapshot")!!.getString("session")!!)
        val beforeCount =
            SceneEngine(before.scene)
                .sample(before.progressNs(SystemClock.elapsedRealtimeNanos()))
                .stepCounter
        call("stop")
        val stopped = SceneCodec.decodeSession(call("snapshot")!!.getString("session")!!)
        assertTrue(stopped.scene.initialStepCounter >= beforeCount)
        call("start")
        val restarted = SceneCodec.decodeSession(call("snapshot")!!.getString("session")!!)
        assertEquals(stopped.scene.initialStepCounter, restarted.scene.initialStepCounter)
        location.removeUpdates(callback)
        android.util.Log.i(
            "OpenLocationTest",
            "existingGps=PASS cumulativeSteps=${restarted.scene.initialStepCounter}",
        )
    }
}
