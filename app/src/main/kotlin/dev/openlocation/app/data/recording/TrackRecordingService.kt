package dev.openlocation.app.data.recording

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import dev.openlocation.app.MainActivity
import dev.openlocation.app.R

/**
 * A user-started location FGS; requests physical GPS directly, without background-location access.
 */
class TrackRecordingService : Service(), LocationListener {
    private val worker = HandlerThread("OpenLocation-recorder").apply { start() }
    private val handler = Handler(worker.looper)
    private val repository by lazy { TrackRepository.get(this) }
    private val manager by lazy { getSystemService(LocationManager::class.java) }
    private var trackId = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    "recording",
                    getString(R.string.record_track),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        val open =
            PendingIntent.getActivity(
                this,
                2,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                3,
                Intent(this, TrackRecordingService::class.java).setAction("stop"),
                PendingIntent.FLAG_IMMUTABLE,
            )
        startForeground(
            2,
            Notification.Builder(this, "recording")
                .setSmallIcon(R.drawable.ic_route)
                .setContentTitle(getString(R.string.recording_track))
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(
                    Notification.Action.Builder(null, getString(R.string.stop), stop).build()
                )
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post {
            if (intent?.action == "stop") {
                if (trackId != 0L) repository.finish(trackId)
                stopSelf()
                return@post
            }
            if (trackId != 0L) return@post
            if (!dev.openlocation.app.data.UsageConsent.accepted(this)) {
                stopSelf()
                return@post
            }
            trackId = repository.begin()
            try {
                check(
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                )
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    repository.finish(trackId, "provider")
                    stopSelf()
                    return@post
                }
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    this,
                    worker.looper,
                )
                handler.postDelayed(deadline, 12 * 60 * 60 * 1000L)
            } catch (_: SecurityException) {
                repository.finish(trackId, "permission")
                stopSelf()
            } catch (_: IllegalStateException) {
                repository.finish(trackId, "permission")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private val deadline = Runnable {
        repository.finish(trackId, "limit")
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        try {
            repository.append(trackId, location)
            if (repository.list().none { it.id == trackId && it.active }) stopSelf()
        } catch (_: android.database.sqlite.SQLiteException) {
            runCatching { repository.finish(trackId, "storage") }
            stopSelf()
        }
    }

    override fun onProviderDisabled(provider: String) {
        repository.finish(trackId, "provider")
        stopSelf()
    }

    override fun onDestroy() {
        handler.post {
            manager.removeUpdates(this)
            if (trackId != 0L) repository.finish(trackId)
            handler.removeCallbacksAndMessages(null)
            worker.quitSafely()
        }
        super.onDestroy()
    }
}
