package dev.openlocation.hook

import android.content.Context
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean

/** The sole entry point. Target application processes never install an adapter. */
class OpenLocationModule : IXposedHookLoadPackage {
    companion object {
        private val installed = AtomicBoolean(false)
    }

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (param.packageName != "android" || param.processName != "android") return
        val server = XposedHelpers.findClass("com.android.server.SystemServer", param.classLoader)
        XposedBridge.hookAllMethods(
            server,
            "startOtherServices",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    if (!installed.compareAndSet(false, true)) return
                    val context =
                        XposedHelpers.getObjectField(p.thisObject, "mSystemContext") as Context
                    val client = SessionClient(context)
                    if (!runCatching { DeviceCompatibility.matches() }.getOrDefault(false)) {
                        client.channels.add("device_mismatch")
                        client.connect()
                        XposedBridge.log(
                            "OpenLocation: device mismatch; all adapters disabled model=${android.os.Build.MODEL} device=${android.os.Build.DEVICE} fingerprint=${android.os.Build.FINGERPRINT}"
                        )
                        return
                    }
                    listOf<Pair<String, () -> Unit>>(
                            "location" to
                                {
                                    ServerLocationAdapter(param.classLoader, client).install()
                                },
                            "gnss" to { ServerGnssAdapter(param.classLoader, client).install() },
                            "sensors" to { ServerSensorAdapter(client).install() },
                        )
                        .forEach { (name, install) ->
                            runCatching(install)
                                .onSuccess { client.channels.add(name) }
                                .onFailure {
                                    XposedBridge.log("OpenLocation: $name unavailable: $it")
                                    XposedBridge.log(it)
                                }
                        }
                    client.connect()
                    XposedBridge.log(
                        "OpenLocation: system service backend initialized pid=${android.os.Process.myPid()}"
                    )
                }
            },
        )
    }
}
