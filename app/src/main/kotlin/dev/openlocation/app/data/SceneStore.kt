package dev.openlocation.app.data

import android.content.Context
import android.os.SystemClock
import dev.openlocation.engine.SceneEngine
import dev.openlocation.runtime.Contract
import dev.openlocation.scenario.*

/**
 * Single control-process owner. Persists configuration, never resumes stale sessions after reboot.
 */
class SceneStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("scenes", Context.MODE_PRIVATE)
    @Volatile
    private var current =
        SessionSnapshot(
            runCatching { SceneCodec.decode(prefs.getString("scene", null)!!) }
                .getOrElse { DemoScenes.create() }
                .let {
                    it.copy(
                        initialStepCounter =
                            maxOf(it.initialStepCounter, prefs.getLong("steps", 1000))
                    )
                }
        )
    private var checkpointEngine: SceneEngine? = null

    @Synchronized fun snapshot() = current

    @Synchronized
    fun configure(scene: SceneConfig) {
        check(!current.running) { "Stop before editing" }
        val prepared =
            scene.copy(
                revision = current.scene.revision + 1,
                initialStepCounter = prefs.getLong("steps", 1000),
            )
        val validated = SceneEngine(prepared)
        prepared.gnss.orbitalCatalogOmm?.let {
            dev.openlocation.engine.GnssEngine.validateCatalog(it)
        }
        checkpointEngine = validated
        current = SessionSnapshot(prepared)
        prefs.edit().putString("scene", SceneCodec.encode(current.scene)).apply()
        notifyChanged()
    }

    @Synchronized
    fun start() {
        if (current.running) return
        check(UsageConsent.accepted(context)) { "Usage notice must be acknowledged" }
        check(dev.openlocation.runtime.DeviceProfiles.matches(context)) {
            "Device profile mismatch"
        }
        TargetAccess.refreshGrants(context)
        current =
            current.copy(
                running = true,
                paused = false,
                startElapsedNs = SystemClock.elapsedRealtimeNanos(),
                startUtcMillis = System.currentTimeMillis(),
                pausedAtElapsedNs = 0,
                pausedTotalNs = 0,
            )
        notifyChanged()
    }

    @Synchronized
    fun togglePause() {
        if (!current.running) return
        val now = SystemClock.elapsedRealtimeNanos()
        current =
            if (current.paused)
                current.copy(
                    paused = false,
                    pausedTotalNs = current.pausedTotalNs + now - current.pausedAtElapsedNs,
                )
            else current.copy(paused = true, pausedAtElapsedNs = now)
        notifyChanged()
    }

    @Synchronized
    fun stop() {
        if (!current.running) return
        val engine =
            checkpointEngine?.takeIf { it.config == current.scene } ?: SceneEngine(current.scene)
        val steps =
            maxOf(
                prefs.getLong("steps", current.scene.initialStepCounter),
                engine.sample(current.progressNs(SystemClock.elapsedRealtimeNanos())).stepCounter,
            )
        prefs.edit().putLong("steps", steps).apply()
        current =
            current.copy(
                running = false,
                paused = false,
                scene = current.scene.copy(initialStepCounter = steps),
            )
        prefs.edit().putString("scene", SceneCodec.encode(current.scene)).apply()
        notifyChanged()
    }

    @Synchronized
    fun checkpointSteps(steps: Long) {
        val base = prefs.getLong("steps", 1000)
        if (steps > base) prefs.edit().putLong("steps", steps).apply()
        if (!current.running)
            current =
                current.copy(scene = current.scene.copy(initialStepCounter = maxOf(base, steps)))
    }

    private fun notifyChanged() {
        context.contentResolver.notifyChange(Contract.URI, null)
    }

    companion object {
        @Volatile private var instance: SceneStore? = null

        fun get(context: Context): SceneStore =
            instance
                ?: synchronized(this) {
                    instance ?: SceneStore(context.applicationContext).also { instance = it }
                }
    }
}
