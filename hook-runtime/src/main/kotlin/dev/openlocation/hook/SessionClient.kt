package dev.openlocation.hook

import android.content.Context
import android.database.ContentObserver
import android.os.*
import de.robv.android.xposed.XposedBridge
import dev.openlocation.engine.GnssEngine
import dev.openlocation.engine.SceneEngine
import dev.openlocation.runtime.Contract
import dev.openlocation.scenario.*

/** One system-server observer and shared immutable engine for all selected UIDs. */
internal class SessionClient(@Volatile var context: Context) {
    val channels = java.util.concurrent.CopyOnWriteArraySet<String>()
    @Volatile
    var targets = emptySet<Int>()
        private set

    fun selected(uid: Int) = active && uid in targets

    val thread = HandlerThread("OpenLocation-samples").apply { start() }
    val handler = Handler(thread.looper)
    @Volatile
    var state: SessionSnapshot? = null
        private set

    @Volatile
    var engine: SceneEngine? = null
        private set

    val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    val active
        get() = state?.running == true

    val delivering
        get() = active && state?.paused == false

    private var observing = false
    private var lastError: String? = null

    fun frame() = state?.let { s ->
        val now = SystemClock.elapsedRealtimeNanos()
        engine?.sample(s.progressNs(now), now, s.utcMillis(now))
    }

    private var skyModel: GnssEngine? = null

    fun sky(f: dev.openlocation.engine.SampleFrame) = f.let {
        val config = state?.scene ?: return@let null
        val model = skyModel ?: GnssEngine(config).also { skyModel = it }
        model.sample(f, f.measurementUtcMillis)
    }

    fun connect() {
        handler.post(
            object : Runnable {
                override fun run() {
                    if (!observing)
                        runCatching { observe() }.onFailure { /* Poll until selected. */ }
                    reload()
                    handler.postDelayed(this, 2000)
                }
            }
        )
    }

    private fun observe() {
        context.contentResolver.registerContentObserver(
            Contract.URI,
            false,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    reload()
                }
            },
        )
        observing = true
    }

    private fun reload() {
        runCatching {
                val token = Binder.clearCallingIdentity()
                val bundle =
                    try {
                        context.contentResolver.call(Contract.URI, "snapshot", null, null)
                    } finally {
                        Binder.restoreCallingIdentity(token)
                    }
                val payload = bundle?.getString("session") ?: error("Controller unavailable")
                targets = bundle.getIntArray("targets")?.toSet() ?: emptySet()
                val next = SceneCodec.decodeSession(payload)
                lastError = null
                context.contentResolver.call(
                    Contract.URI,
                    "report",
                    null,
                    Bundle().apply { putString("channels", channels.sorted().joinToString(",")) },
                )
                if (next == state) return
                if (next.scene != state?.scene) {
                    engine = SceneEngine(next.scene)
                    skyModel = null
                }
                state = next
                listeners.toList().forEach { it() }
                XposedBridge.log(
                    "OpenLocation: revision=${next.scene.revision} active=${next.running} paused=${next.paused}"
                )
            }
            .onFailure {
                val previous = state
                state = null
                targets = emptySet()
                if (previous != null)
                    listeners.toList().forEach { notify ->
                        runCatching { notify() }.onFailure { failure -> XposedBridge.log(failure) }
                    }
                if (lastError != it.message) XposedBridge.log(it)
                lastError = it.message
            }
    }
}
