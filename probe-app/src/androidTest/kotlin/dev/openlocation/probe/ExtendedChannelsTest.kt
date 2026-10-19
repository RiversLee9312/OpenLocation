package dev.openlocation.probe

import android.hardware.*
import android.location.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.openlocation.engine.*
import dev.openlocation.runtime.Contract
import dev.openlocation.scenario.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.*
import org.junit.*
import org.junit.Assert.*

class ExtendedChannelsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val location = context.getSystemService(LocationManager::class.java)

    private fun call(method: String, arg: String? = null) =
        context.contentResolver.call(Contract.URI, method, arg, null)

    private val scene = DemoScenes.create(Locomotion.RUN).copy(environmentSegments = emptyList())
    private lateinit var session: SessionSnapshot

    @Before
    fun start() {
        context.startActivity(
            android.content
                .Intent(context, ProbeActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        SystemClock.sleep(500)
        call("stop")
        call("configure", SceneCodec.encode(scene))
        call("start")
        SystemClock.sleep(800)
        session = SceneCodec.decodeSession(call("snapshot")!!.getString("session")!!)
    }

    @After
    fun stop() {
        call("stop")
    }

    private fun verifySamples(data: DoubleArray) {
        assertTrue("No NDK samples: ${data.take(3)}", data[0] >= 20)
        val engine = SceneEngine(session.scene)
        val rows = data.drop(3).chunked(4).sortedBy { it[0] }
        rows.take(12).forEach { row ->
            val timestamp = row[0].toLong()
            val expected = engine.sample(session.progressNs(timestamp), timestamp).accelerometer
            assertEquals(expected.x, row[1], .03)
            assertEquals(expected.y, row[2], .03)
            assertEquals(expected.z, row[3], .03)
        }
        assertTrue(rows.zipWithNext().all { (a, b) -> a[0] < b[0] })
    }

    @Test
    fun ndkQueueAndBothDirectMemoryTypesFollowTheSharedFrame() {
        val queue = NativeProbe.queue(1300)
        verifySamples(queue)
        val memory = NativeProbe.direct(false)
        verifySamples(memory)
        assertEquals(1.0, memory[2], 0.0)
        val hardware = NativeProbe.direct(true)
        verifySamples(hardware)
        assertEquals(1.0, hardware[2], 0.0)
        android.util.Log.i(
            "OpenLocationTest",
            "NDK queue=${queue[0]} ashmem=${memory[0]} hardware=${hardware[0]} sharedFrame=PASS directStop=PASS",
        )
    }

    @Test
    fun javaDirectMemoryAndSystemProvidersAreSynthetic() {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        val memory = MemoryFile("OpenLocation-test", 104 * 128)
        try {
            manager.createDirectChannel(memory).use { channel ->
                val token = channel.configure(sensor, SensorDirectChannel.RATE_NORMAL)
                assertTrue(token > 0)
                SystemClock.sleep(600)
                assertTrue(channel.configure(sensor, SensorDirectChannel.RATE_STOP) > 0)
                val bytes = ByteArray(104 * 128)
                memory.readBytes(bytes, 0, 0, bytes.size)
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val rows = mutableListOf<Double>()
                for (i in 0 until 128) if (buffer.getInt(i * 104 + 12) != 0) {
                    assertEquals(token, buffer.getInt(i * 104 + 4))
                    rows.add(buffer.getLong(i * 104 + 16).toDouble())
                    repeat(3) { j -> rows.add(buffer.getFloat(i * 104 + 24 + j * 4).toDouble()) }
                }
                verifySamples(
                    doubleArrayOf(rows.size / 4.0, token.toDouble(), 1.0) + rows.toDoubleArray()
                )
            }
        } finally {
            memory.close()
        }
        for (provider in listOf("network", "fused", "passive")) {
            val last = location.getLastKnownLocation(provider)
            assertNotNull("$provider absent", last)
            assertEquals(provider, last!!.provider)
            assertTrue(kotlin.math.abs(last.latitude - 31.23) < .02)
        }
        android.util.Log.i("OpenLocationTest", "Java direct=PASS system network/fused/passive=PASS")
    }

    @Test
    fun rawGnssStatusNmeaAndReceiverClockShareOrbitalGeometry() {
        val epoch = ArrayBlockingQueue<GnssMeasurementsEvent>(4)
        val statuses = ArrayBlockingQueue<GnssStatus>(4)
        val sentences = CopyOnWriteArrayList<String>()
        val executor = Executor { it.run() }
        val raw =
            object : GnssMeasurementsEvent.Callback() {
                override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                    epoch.offer(event)
                }
            }
        val status =
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(s: GnssStatus) {
                    statuses.offer(s)
                }
            }
        val nmea = OnNmeaMessageListener { message, _ -> sentences.add(message) }
        try {
            assertTrue(location.registerGnssMeasurementsCallback(executor, raw))
            assertTrue(location.registerGnssStatusCallback(executor, status))
            assertTrue(location.addNmeaListener(executor, nmea))
            val event = epoch.poll(5, TimeUnit.SECONDS) ?: error("Raw GNSS callback missing")
            val sky = statuses.poll(5, TimeUnit.SECONDS) ?: error("GNSS status missing")
            assertTrue(sky.satelliteCount >= 12)
            assertTrue(event.measurements.size >= 12)
            val clock = event.clock
            assertTrue(clock.hasFullBiasNanos())
            assertTrue(clock.hasElapsedRealtimeNanos())
            val utc = session.utcMillis(clock.elapsedRealtimeNanos)
            val frame =
                SceneEngine(session.scene)
                    .sample(
                        session.progressNs(clock.elapsedRealtimeNanos),
                        clock.elapsedRealtimeNanos,
                        utc,
                    )
            val reference = GnssEngine(session.scene).sample(frame, utc)
            event.measurements.forEach { m ->
                val sat =
                    reference.satellites.first {
                        it.constellation == m.constellationType && it.svid == m.svid
                    }
                val receiveTow =
                    Math.floorMod(clock.timeNanos - clock.fullBiasNanos, GnssEngine.GPS_WEEK_NS)
                        .toDouble() - clock.biasNanos
                val transmit =
                    m.receivedSvTimeNanos.toDouble() + (if (m.constellationType == 5) 14e9 else 0.0)
                val delay =
                    (receiveTow - transmit + GnssEngine.GPS_WEEK_NS) % GnssEngine.GPS_WEEK_NS
                assertEquals(sat.pseudorangeMeters, delay * 1e-9 * GnssEngine.SPEED_OF_LIGHT, 1.0)
                assertEquals(sat.cn0DbHz, m.cn0DbHz, .01)
            }
            SystemClock.sleep(300)
            assertTrue(sentences.any { it.startsWith("\$GNGGA") })
            android.util.Log.i(
                "OpenLocationTest",
                "GNSS satellites=${sky.satelliteCount} raw=${event.measurements.size} clock/range/CN0=PASS NMEA=PASS",
            )
        } finally {
            location.unregisterGnssMeasurementsCallback(raw)
            location.unregisterGnssStatusCallback(status)
            location.removeNmeaListener(nmea)
        }
    }
}
