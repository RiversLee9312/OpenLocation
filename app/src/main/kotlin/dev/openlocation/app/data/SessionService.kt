package dev.openlocation.app.data

import android.app.*
import android.content.Intent
import android.os.*
import dev.openlocation.app.MainActivity
import dev.openlocation.app.R
import dev.openlocation.engine.SceneEngine

/** User-started synthetic coordination only; deliberately does not request real location. */
class SessionService : Service() {
    private val worker = HandlerThread("OpenLocation-coordinator").apply { start() }
    private val handler = Handler(worker.looper)
    private var engine: SceneEngine? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    "session",
                    getString(R.string.session_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, SessionService::class.java).setAction("stop"),
                PendingIntent.FLAG_IMMUTABLE,
            )
        startForeground(
            1,
            Notification.Builder(this, "session")
                .setSmallIcon(R.drawable.ic_route)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.running))
                .setContentIntent(open)
                .addAction(
                    Notification.Action.Builder(null, getString(R.string.stop), stop).build()
                )
                .setOngoing(true)
                .build(),
        )
        handler.post(tick)
    }

    private val tick =
        object : Runnable {
            override fun run() {
                val store = SceneStore.get(this@SessionService)
                val state = store.snapshot()
                if (!state.running) {
                    stopSelf()
                    return
                }
                val activeEngine =
                    engine?.takeIf { it.config == state.scene }
                        ?: SceneEngine(state.scene).also { engine = it }
                val frame =
                    activeEngine.sample(state.progressNs(SystemClock.elapsedRealtimeNanos()))
                store.checkpointSteps(frame.stepCounter)
                if (frame.finished) store.stop()
                handler.postDelayed(this, 1000)
            }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            SceneStore.get(this).stop()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.quitSafely()
        SceneStore.get(this).stop()
        super.onDestroy()
    }
}
