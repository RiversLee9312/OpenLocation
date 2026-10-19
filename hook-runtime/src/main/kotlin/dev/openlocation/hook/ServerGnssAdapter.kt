package dev.openlocation.hook

import android.Manifest
import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.os.*
import de.robv.android.xposed.*
import dev.openlocation.engine.Nmea
import java.util.concurrent.ConcurrentHashMap

/** GNSS listener transports live in system_server; no LocationManager hook is installed in apps. */
internal class ServerGnssAdapter(
    private val loader: ClassLoader,
    private val client: SessionClient,
) {
    private data class Subscription(
        val callback: IInterface,
        val uid: Int,
        val pkg: String,
        val kind: String,
        var started: Boolean = false,
    )

    private val subscriptions = ConcurrentHashMap<IBinder, Subscription>()
    private val publishing = ThreadLocal.withInitial { false }
    private val kinds =
        mapOf(
            "registerGnssStatusCallback" to "status",
            "registerGnssNmeaCallback" to "nmea",
            "addGnssMeasurementsListener" to "raw",
        )

    fun install() {
        val service =
            XposedHelpers.findClass("com.android.server.location.LocationManagerService", loader)
        (kinds.keys +
                setOf(
                    "unregisterGnssStatusCallback",
                    "unregisterGnssNmeaCallback",
                    "removeGnssMeasurementsListener",
                ))
            .forEach { name ->
                XposedBridge.hookAllMethods(
                    service,
                    name,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.setObjectExtra("uid", Binder.getCallingUid())
                            if (name !in kinds)
                                p.args.filterIsInstance<IInterface>().firstOrNull()?.let {
                                    subscriptions.remove(it.asBinder())
                                }
                        }

                        override fun afterHookedMethod(p: MethodHookParam) {
                            if (name !in kinds || p.hasThrowable()) return
                            val callback =
                                p.args.filterIsInstance<IInterface>().firstOrNull() ?: return
                            val pkg = p.args.filterIsInstance<String>().firstOrNull() ?: return
                            val sub =
                                Subscription(
                                    callback,
                                    p.getObjectExtra("uid") as Int,
                                    pkg,
                                    kinds.getValue(name),
                                )
                            val binder = callback.asBinder()
                            subscriptions[binder] = sub
                            XposedBridge.log(
                                "OpenLocation: GNSS ${sub.kind} uid=${sub.uid} package=${sub.pkg} selected=${client.selected(sub.uid)}"
                            )
                            runCatching {
                                binder.linkToDeath({ subscriptions.remove(binder, sub) }, 0)
                            }
                        }
                    },
                )
            }
        listOf("IGnssStatusListener", "IGnssNmeaListener", "IGnssMeasurementsListener").forEach {
            name ->
            val proxy = XposedHelpers.findClass("android.location.$name\$Stub\$Proxy", null)
            proxy.declaredMethods
                .filter { it.name.startsWith("on") }
                .forEach { method ->
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                if (publishing.get()) return
                                val sub =
                                    subscriptions[(p.thisObject as IInterface).asBinder()] ?: return
                                if (client.selected(sub.uid)) p.result = null
                            }
                        },
                    )
                }
        }
        client.handler.post(tick)
    }

    private fun permitted(sub: Subscription): Boolean {
        if (
            client.context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, -1, sub.uid) !=
                PackageManager.PERMISSION_GRANTED
        )
            return false
        val ops = client.context.getSystemService(AppOpsManager::class.java)
        return ops.noteOpNoThrow(AppOpsManager.OPSTR_FINE_LOCATION, sub.uid, sub.pkg) ==
            AppOpsManager.MODE_ALLOWED
    }

    private val tick =
        object : Runnable {
            override fun run() {
                try {
                    if (client.delivering && subscriptions.values.any { client.selected(it.uid) }) {
                        val frame = client.frame()
                        val epoch = frame?.let(client::sky)
                        if (frame != null && epoch != null) {
                            val status by lazy { GnssPackets.status(epoch) }
                            val raw by lazy { GnssPackets.raw(epoch) }
                            val sentences by lazy { Nmea.sentences(epoch, frame) }
                            publishing.set(true)
                            try {
                                subscriptions.forEach { (binder, sub) ->
                                    if (client.selected(sub.uid))
                                        runCatching {
                                                if (!permitted(sub)) return@runCatching
                                                when (sub.kind) {
                                                    "status" -> {
                                                        if (!sub.started) {
                                                            XposedHelpers.callMethod(
                                                                sub.callback,
                                                                "onGnssStarted",
                                                            )
                                                            XposedHelpers.callMethod(
                                                                sub.callback,
                                                                "onFirstFix",
                                                                500,
                                                            )
                                                            sub.started = true
                                                        }
                                                        XposedHelpers.callMethod(
                                                            sub.callback,
                                                            "onSvStatusChanged",
                                                            status,
                                                        )
                                                    }
                                                    "raw" ->
                                                        XposedHelpers.callMethod(
                                                            sub.callback,
                                                            "onGnssMeasurementsReceived",
                                                            raw,
                                                        )
                                                    "nmea" ->
                                                        sentences.forEach {
                                                            XposedHelpers.callMethod(
                                                                sub.callback,
                                                                "onNmeaReceived",
                                                                epoch.utcMillis,
                                                                it,
                                                            )
                                                        }
                                                }
                                            }
                                            .onFailure {
                                                subscriptions.remove(binder, sub)
                                                XposedBridge.log(it)
                                            }
                                    else sub.started = false
                                }
                            } finally {
                                publishing.set(false)
                            }
                        }
                    }
                } catch (failure: Throwable) {
                    XposedBridge.log(failure)
                } finally {
                    client.handler.postDelayed(this, 1000)
                }
            }
        }
}
