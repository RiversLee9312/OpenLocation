package dev.openlocation.hook

import android.location.Location
import android.os.SystemClock
import de.robv.android.xposed.*
import dev.openlocation.runtime.location
import java.util.concurrent.ConcurrentHashMap

/** Replace data before permission filtering and transport, inside LocationProviderManager. */
internal class ServerLocationAdapter(
    private val loader: ClassLoader,
    private val client: SessionClient,
) {
    private val registrations = ConcurrentHashMap<Any, Any>()
    private val publishing = ThreadLocal.withInitial { false }
    private val lastQuery = ThreadLocal<Any?>()
    private lateinit var resultClass: Class<*>
    private var lastNs = 0L

    private fun uid(identity: Any) = XposedHelpers.callMethod(identity, "getUid") as Int

    private fun selected(registration: Any): Boolean {
        val identity = XposedHelpers.callMethod(registration, "getIdentity")
        // A broker's aggregate stream may only be replaced when every attributed recipient opts in.
        val request = XposedHelpers.callMethod(registration, "getRequest")
        val work =
            XposedHelpers.callMethod(request, "getWorkSource")
                ?: return client.selected(uid(identity))
        val count = XposedHelpers.callMethod(work, "size") as Int
        val recipients = (0 until count).map { XposedHelpers.callMethod(work, "getUid", it) as Int }
        return client.active &&
            dev.openlocation.runtime.TargetPolicy.allows(uid(identity), recipients, client.targets)
    }

    private fun provider(manager: Any) = XposedHelpers.getObjectField(manager, "mName") as String

    private fun result(location: Location) =
        XposedHelpers.callStaticMethod(resultClass, "wrap", arrayOf(location))

    private fun location(frame: dev.openlocation.engine.SampleFrame, provider: String): Location? =
        frame.location(provider)?.apply {
            // Legacy GPS clients consume these receiver summaries instead of GnssStatus callbacks.
            // Derive every value from the same sky model used by raw measurements and NMEA.
            if (provider == "gps")
                client
                    .sky(frame)
                    ?.satellites
                    ?.filter { it.usedInFix }
                    ?.let { satellites ->
                        extras =
                            android.os.Bundle().apply {
                                putInt("satellites", satellites.size)
                                putInt("maxCn0", satellites.maxOfOrNull { it.cn0DbHz.toInt() } ?: 0)
                                putInt(
                                    "meanCn0",
                                    if (satellites.isEmpty()) 0
                                    else satellites.map { it.cn0DbHz }.average().toInt(),
                                )
                            }
                    }
        }

    fun install() {
        val manager =
            XposedHelpers.findClass(
                "com.android.server.location.provider.LocationProviderManager",
                loader,
            )
        val registration = XposedHelpers.findClass("${manager.name}\$Registration", loader)
        resultClass = XposedHelpers.findClass("android.location.LocationResult", null)
        XposedBridge.hookAllMethods(
            registration,
            "onRegister",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    if (!p.hasThrowable())
                        registrations[p.thisObject] =
                            XposedHelpers.getObjectField(p.thisObject, "this\$0")
                }
            },
        )
        XposedBridge.hookAllMethods(
            registration,
            "onUnregister",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    registrations.remove(p.thisObject)
                }
            },
        )
        listOf("LocationRegistration", "GetCurrentLocationListenerRegistration").forEach { name ->
            val type = XposedHelpers.findClass("${manager.name}\$$name", loader)
            XposedBridge.hookAllMethods(
                type,
                "acceptLocationChange",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (!publishing.get() && p.args[0] != null && selected(p.thisObject))
                            p.result = null
                    }
                },
            )
        }
        // The normal method still enforces permission, coarse accuracy, AppOps and provider state.
        XposedBridge.hookAllMethods(
            manager,
            "getLastLocation",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val identity =
                        p.args.firstOrNull {
                            it?.javaClass?.name == "android.location.util.identity.CallerIdentity"
                        }
                    if (identity != null && client.selected(uid(identity)))
                        lastQuery.set(p.thisObject)
                }

                override fun afterHookedMethod(p: MethodHookParam) {
                    lastQuery.remove()
                }
            },
        )
        XposedBridge.hookAllMethods(
            manager,
            "getLastLocationUnsafe",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    if (lastQuery.get() === p.thisObject && !p.hasThrowable())
                        p.result = client.frame()?.let { location(it, provider(p.thisObject)) }
                }
            },
        )
        client.handler.post(tick)
    }

    private val tick =
        object : Runnable {
            override fun run() {
                try {
                    val now = SystemClock.elapsedRealtimeNanos()
                    val period =
                        1_000_000_000L / (client.state?.scene?.outputRates?.locationHz ?: 2)
                    if (client.delivering && now - lastNs >= period) {
                        lastNs = now
                        val frame = client.frame()
                        if (frame != null)
                            registrations.forEach { (registration, manager) ->
                                runCatching {
                                        if (!selected(registration)) return@runCatching
                                        val lock =
                                            XposedHelpers.getObjectField(
                                                manager,
                                                "mMultiplexerLock",
                                            )
                                        synchronized(lock) {
                                            if (
                                                registrations[registration] !== manager ||
                                                    XposedHelpers.callMethod(
                                                        registration,
                                                        "isActive",
                                                    ) != true
                                            )
                                                return@synchronized
                                            val location =
                                                location(frame, provider(manager))
                                                    ?: return@synchronized
                                            publishing.set(true)
                                            try {
                                                val operation =
                                                    XposedHelpers.callMethod(
                                                        registration,
                                                        "acceptLocationChange",
                                                        result(location),
                                                    )
                                                if (operation != null)
                                                    XposedHelpers.callMethod(
                                                        registration,
                                                        "executeOperation",
                                                        operation,
                                                    )
                                            } finally {
                                                publishing.set(false)
                                            }
                                        }
                                    }
                                    .onFailure {
                                        XposedBridge.log("OpenLocation: location delivery: $it")
                                    }
                            }
                    }
                } finally {
                    client.handler.postDelayed(this, 50)
                }
            }
        }
}
