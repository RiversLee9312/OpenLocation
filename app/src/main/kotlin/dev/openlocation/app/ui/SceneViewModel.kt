package dev.openlocation.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openlocation.app.R
import dev.openlocation.app.data.*
import dev.openlocation.engine.*
import dev.openlocation.runtime.Contract
import dev.openlocation.runtime.Gpx
import dev.openlocation.runtime.SpeedUnit
import dev.openlocation.scenario.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SceneUiState(
    val session: SessionSnapshot,
    val frame: SampleFrame? = null,
    val lengthMeters: Double = 0.0,
    val duration: Double = 0.0,
    val dynamicColor: Boolean = true,
    val speedUnit: SpeedUnit = SpeedUnit.METERS_PER_SECOND,
    val error: Int? = null,
    val nominalRoute: List<GeoPoint> = emptyList(),
    val targets: Set<String> = setOf(Contract.PROBE_PACKAGE),
    val refreshingOrbits: Boolean = false,
    val compatible: Boolean = false,
    val backend: Set<String> = emptySet(),
    val verifiedChannels: Set<String> = emptySet(),
)

class SceneViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SceneStore.get(application)
    private val prefs = application.getSharedPreferences("ui", 0)
    private val _ui =
        MutableStateFlow(
            SceneUiState(
                store.snapshot(),
                dynamicColor = prefs.getBoolean("dynamic", true),
                speedUnit = SpeedUnit.fromId(prefs.getString("speedUnit", null)),
                targets = TargetAccess.enabled(application),
                compatible = dev.openlocation.runtime.DeviceProfiles.matches(application),
                verifiedChannels =
                    dev.openlocation.runtime.DeviceProfiles.verifiedChannels(application),
            )
        )
    val ui = _ui.asStateFlow()
    private var engine: SceneEngine? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val s = store.snapshot()
                val e =
                    engine?.takeIf { it.config == s.scene }
                        ?: SceneEngine(s.scene).also { engine = it }
                val now = SystemClock.elapsedRealtimeNanos()
                val frame = e.sample(s.progressNs(now), now)
                _ui.update {
                    it.copy(
                        session = s,
                        frame = frame,
                        lengthMeters = e.lengthMeters,
                        duration = e.durationSeconds,
                        nominalRoute = e.nominalRouteWgs84,
                        backend =
                            application.getSharedPreferences("backend", 0).let { backend ->
                                if (now / 1_000_000 - backend.getLong("seen", 0) in 0..6000)
                                    backend
                                        .getString("channels", "")!!
                                        .split(",")
                                        .filter(String::isNotEmpty)
                                        .toSet()
                                else emptySet()
                            },
                    )
                }
                delay(100)
            }
        }
    }

    fun start() {
        if (!_ui.value.compatible) {
            message(R.string.device_mismatch_title)
            return
        }
        store.start()
        getApplication<Application>()
            .startForegroundService(Intent(getApplication(), SessionService::class.java))
    }

    fun pause() = store.togglePause()

    fun target(pkg: String, value: Boolean) {
        if (TargetAccess.setEnabled(getApplication(), pkg, value))
            _ui.update { it.copy(targets = TargetAccess.enabled(getApplication())) }
        else message(R.string.app_missing)
    }

    fun stop() {
        engine?.let { e ->
            val s = store.snapshot()
            store.checkpointSteps(
                e.sample(s.progressNs(SystemClock.elapsedRealtimeNanos())).stepCounter
            )
        }
        store.stop()
        getApplication<Application>()
            .stopService(Intent(getApplication(), SessionService::class.java))
    }

    fun configure(transform: (SceneConfig) -> SceneConfig) {
        if (store.snapshot().running) {
            message(R.string.edit_stopped)
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                    val old = store.snapshot().scene
                    val next = transform(old)
                    store.configure(
                        next.copy(
                            profiles =
                                old.profiles +
                                    (old.locomotion to old.profile()) +
                                    (next.locomotion to next.profile())
                        )
                    )
                }
                .onFailure { message(R.string.route_limit) }
        }
    }

    fun mode(mode: Locomotion) = configure { old ->
        old.withProfile(mode, old.profiles[mode] ?: DemoScenes.create(mode).profile())
    }

    fun replaceRoute(points: List<GeoPoint>, completed: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success =
                withContext(Dispatchers.Default) {
                    runCatching {
                            check(!store.snapshot().running)
                            val scene = store.snapshot().scene.copy(routeWgs84 = points)
                            SceneEngine(
                                scene
                            ) // Validate extent, distinct points and total length before
                            // committing.
                            check(!store.snapshot().running)
                            store.configure(scene)
                        }
                        .isSuccess
                }
            completed(success)
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                    val context = getApplication<Application>()
                    val id = dev.openlocation.app.data.recording.TrackDocuments.import(context, uri)
                    val points =
                        dev.openlocation.app.data.recording.TrackRepository.get(context)
                            .scenePoints(id)
                    configure { it.copy(routeWgs84 = points) }
                }
                .onFailure { message(R.string.import_failed) }
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                    getApplication<Application>()
                        .contentResolver
                        .openOutputStream(uri, "wt")!!
                        .bufferedWriter()
                        .use { it.write(Gpx.write(store.snapshot().scene.routeWgs84)) }
                    message(R.string.route_saved)
                }
                .onFailure { message(R.string.export_failed) }
        }
    }

    fun useTrack(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                    dev.openlocation.app.data.recording.TrackRepository.get(getApplication())
                        .scenePoints(id)
                }
                .onSuccess { points -> configure { it.copy(routeWgs84 = points) } }
                .onFailure { message(R.string.route_limit) }
        }
    }

    fun saveRoute() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                    val context = getApplication<Application>()
                    dev.openlocation.app.data.recording.TrackRepository.get(context)
                        .importGpx(
                            Gpx.write(store.snapshot().scene.routeWgs84).toByteArray(),
                            context.getString(R.string.routes),
                            "saved",
                        )
                }
                .onSuccess { message(R.string.route_saved) }
                .onFailure { message(R.string.export_failed) }
        }
    }

    fun dynamic(value: Boolean) {
        prefs.edit().putBoolean("dynamic", value).apply()
        _ui.update { it.copy(dynamicColor = value) }
    }

    fun speedUnit(value: SpeedUnit) {
        prefs.edit().putString("speedUnit", value.id).apply()
        _ui.update { it.copy(speedUnit = value) }
    }

    fun refreshOrbits() {
        if (store.snapshot().running) {
            message(R.string.edit_stopped)
            return
        }
        if (_ui.value.refreshingOrbits) return
        _ui.update { it.copy(refreshingOrbits = true) }
        viewModelScope.launch {
            runCatching {
                    val catalog =
                        withContext(Dispatchers.IO) {
                            val connection =
                                java.net
                                    .URI(
                                        "https://celestrak.org/NORAD/elements/gp.php?GROUP=gnss&FORMAT=json"
                                    )
                                    .toURL()
                                    .openConnection() as javax.net.ssl.HttpsURLConnection
                            connection.connectTimeout = 8000
                            connection.readTimeout = 8000
                            try {
                                require(connection.responseCode == 200)
                                val bytes = connection.inputStream.use { it.readNBytes(200_001) }
                                require(bytes.size <= 200_000)
                                bytes.toString(Charsets.UTF_8).also {
                                    GnssEngine.validateCatalog(it)
                                }
                            } finally {
                                connection.disconnect()
                            }
                        }
                    withContext(Dispatchers.Default) {
                        val current = store.snapshot()
                        check(!current.running)
                        store.configure(
                            current.scene.copy(
                                gnss = current.scene.gnss.copy(orbitalCatalogOmm = catalog)
                            )
                        )
                    }
                    message(R.string.orbits_updated)
                }
                .onFailure { message(R.string.orbit_update_failed) }
            _ui.update { it.copy(refreshingOrbits = false) }
        }
    }

    fun message(id: Int?) {
        _ui.update { it.copy(error = id) }
    }
}
