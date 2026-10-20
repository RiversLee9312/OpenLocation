package dev.openlocation.probe

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.*
import android.location.*
import android.os.*
import dev.openlocation.engine.*
import dev.openlocation.runtime.Contract
import dev.openlocation.scenario.*
import java.io.File
import kotlinx.coroutines.flow.*
import org.json.JSONObject

data class SensorReading(
    val sensor: Sensor,
    val values: FloatArray = floatArrayOf(),
    val timestamp: Long = 0,
    val samples: Long = 0,
    val hz: Double = 0.0,
    val gaps: Long = 0,
    val delayMs: Double = 0.0,
    val firstNs: Long = 0,
    val instanceKey: Int = 0,
)

data class ProbeState(
    val monitoring: Boolean = false,
    val recording: Boolean = false,
    val hooked: Boolean = false,
    val synthetic: Boolean = false,
    val readings: List<SensorReading> = emptyList(),
    val location: Location? = null,
    val reference: SampleFrame? = null,
    val limitReached: Boolean = false,
    val selected: Boolean = false,
    val visibleSatellites: Int = 0,
    val usedSatellites: Int = 0,
    val measurements: Int = 0,
)

/**
 * Foreground diagnostic capture. Values are copied at the callback boundary, timestamps preserved.
 */
class ProbeMonitor(private val context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val locations = context.getSystemService(LocationManager::class.java)
    private val worker = HandlerThread("Probe-capture").apply { start() }
    private val handler = Handler(worker.looper)
    private val _state =
        MutableStateFlow(ProbeState(hooked = System.getProperty(Contract.HOOK_PROPERTY) == "true"))
    val state = _state.asStateFlow()
    private var session: SessionSnapshot? = null
    private var engine: SceneEngine? = null
    private val rows = linkedMapOf<Int, SensorReading>()
    private val instanceKeys = java.util.IdentityHashMap<Sensor, Int>()
    private var writer: java.io.BufferedWriter? = null
    private var bytes = 0L
    val recordingFile = File(context.filesDir, "recording.ndjson")
    private val satelliteCallback =
        object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                _state.update {
                    it.copy(
                        visibleSatellites = status.satelliteCount,
                        usedSatellites = (0 until status.satelliteCount).count(status::usedInFix),
                    )
                }
            }
        }
    private val measurementCallback =
        object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                _state.update { it.copy(measurements = event.measurements.size) }
            }
        }
    private val locationListener = LocationListener { location ->
        _state.update { it.copy(location = Location(location)) }
        append(
            JSONObject()
                .put("kind", "location")
                .put("elapsedNs", location.elapsedRealtimeNanos)
                .put("latitude", location.latitude)
                .put("longitude", location.longitude)
                .put("accuracy", location.accuracy)
                .put("source", if (_state.value.synthetic) "synthetic" else "device")
        )
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (_state.value.monitoring) return
        _state.update { it.copy(monitoring = true) }
        handler.post {
            sensors
                .getSensorList(Sensor.TYPE_ALL)
                .filter { it.type in Contract.SUPPORTED_SENSORS }
                .forEachIndexed { index, sensor ->
                    val key = instanceKeys.getOrPut(sensor) { index + 1 }
                    rows[key] = SensorReading(sensor, instanceKey = key)
                    runCatching { sensors.registerListener(this, sensor, 20000, handler) }
                }
            runCatching {
                locations.requestLocationUpdates("gps", 500L, 0f, locationListener, worker.looper)
                locations.registerGnssStatusCallback(satelliteCallback, handler)
                locations.registerGnssMeasurementsCallback(measurementCallback, handler)
            }
            handler.post(refresh)
        }
    }

    fun stop() {
        _state.update { it.copy(monitoring = false) }
        handler.post {
            sensors.unregisterListener(this)
            locations.removeUpdates(locationListener)
            locations.unregisterGnssStatusCallback(satelliteCallback)
            locations.unregisterGnssMeasurementsCallback(measurementCallback)
            handler.removeCallbacks(refresh)
            stopRecording()
        }
    }

    fun record() {
        handler.post {
            if (writer != null) {
                stopRecording()
                return@post
            }
            bytes = 0
            writer = recordingFile.bufferedWriter()
            val metadata =
                JSONObject()
                    .put("kind", "metadata")
                    .put("schemaVersion", 1)
                    .put("device", Build.MODEL)
                    .put("api", Build.VERSION.SDK_INT)
                    .put("requestedPeriodUs", 20000)
            val instances = org.json.JSONArray()
            rows.values.forEach { row ->
                instances.put(
                    JSONObject()
                        .put("id", row.instanceKey)
                        .put("hardwareId", row.sensor.id)
                        .put("type", row.sensor.type)
                        .put("name", row.sensor.name)
                        .put("vendor", row.sensor.vendor)
                        .put("minDelayUs", row.sensor.minDelay)
                        .put("reportingMode", row.sensor.reportingMode)
                )
            }
            append(metadata.put("sensors", instances))
            _state.update { it.copy(recording = true, limitReached = false) }
        }
    }

    private fun stopRecording() {
        writer?.close()
        writer = null
        _state.update { it.copy(recording = false) }
    }

    private fun append(record: JSONObject) {
        if (writer == null) return
        val line = record.toString()
        bytes += line.toByteArray().size + 1
        if (bytes > 32 * 1024 * 1024) {
            stopRecording()
            _state.update { it.copy(limitReached = true) }
            return
        }
        writer?.apply {
            write(line)
            newLine()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val key = instanceKeys[event.sensor] ?: return
        val old = rows[key] ?: return
        val values = event.values.copyOf()
        val now = SystemClock.elapsedRealtimeNanos()
        val first = if (old.samples == 0L) event.timestamp else old.firstNs
        val count = old.samples + 1
        val rate =
            if (count > 1 && event.timestamp > first) (count - 1) * 1e9 / (event.timestamp - first)
            else 0.0
        val gaps =
            old.gaps +
                if (
                    old.timestamp > 0 &&
                        event.timestamp - old.timestamp > 60_000_000 &&
                        event.sensor.reportingMode == Sensor.REPORTING_MODE_CONTINUOUS
                )
                    1
                else 0
        rows[key] =
            SensorReading(
                event.sensor,
                values,
                event.timestamp,
                count,
                rate,
                gaps,
                (now - event.timestamp) / 1e6,
                first,
                key,
            )
        append(
            JSONObject()
                .put("kind", "sensor")
                .put("id", key)
                .put("hardwareId", event.sensor.id)
                .put("type", event.sensor.type)
                .put("elapsedNs", event.timestamp)
                .put("deliveryNs", now)
                .put("accuracy", event.accuracy)
                .put("revision", session?.scene?.revision)
                .put("source", if (_state.value.synthetic) "synthetic" else "device")
                .put("values", org.json.JSONArray(values.toList()))
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private val refresh =
        object : Runnable {
            override fun run() {
                runCatching {
                    val text =
                        context.contentResolver.call(Contract.URI, "snapshot", null, null)?.let {
                            bundle ->
                            _state.update {
                                it.copy(
                                    selected =
                                        bundle
                                            .getIntArray("targets")
                                            ?.contains(android.os.Process.myUid()) == true,
                                    hooked =
                                        bundle
                                            .getString("backend", "")
                                            .split(",")
                                            .contains("sensors"),
                                )
                            }
                            bundle.getString("session")
                        }
                    val next = text?.let(SceneCodec::decodeSession)
                    if (next != null && next.scene != session?.scene) {
                        engine = SceneEngine(next.scene)
                        session = next
                    } else session = next
                }
                val s = session
                val now = SystemClock.elapsedRealtimeNanos()
                _state.update {
                    it.copy(
                        readings = rows.values.toList(),
                        synthetic = it.selected && it.hooked && s?.running == true,
                        reference = s?.let { engine?.sample(it.progressNs(now), now) },
                    )
                }
                if (_state.value.monitoring) handler.postDelayed(this, 200)
            }
        }

    fun close() {
        stop()
        handler.post { worker.quitSafely() }
    }
}
