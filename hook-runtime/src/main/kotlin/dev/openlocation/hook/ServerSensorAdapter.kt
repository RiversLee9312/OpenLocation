package dev.openlocation.hook

import android.os.Build
import de.robv.android.xposed.XposedBridge
import dev.openlocation.runtime.values

internal object ServiceNative {
    external fun install(offsets: LongArray, api: Int): Boolean

    external fun publish(
        active: Boolean,
        delivering: Boolean,
        targets: IntArray,
        timestamp: Long,
        steps: Long,
        data: FloatArray,
    ): Int
}

/**
 * Resolve this device's service symbols. No stored addresses, struct-field offsets or app hooks.
 */
internal class ServerSensorAdapter(private val client: SessionClient) {
    fun install() {
        val symbols = ElfSymbols.read("/system/lib64/libsensorservice.so")
        val event = "_ZN7android13SensorService21SensorEventConnection"
        val direct = "_ZN7android13SensorService22SensorDirectConnection"
        val names =
            listOf(
                event + "10sendEventsEPK15sensors_event_tmPS2_PKNS_2wpIKS1_EE",
                event + "13enableDisableEiblli",
                event + "12setEventRateEil",
                event + "7destroyEv",
                direct +
                    "C1ERKNS_2spIS0_EEjiPK20sensors_direct_mem_tiRKNS_8String16E" +
                    (if (Build.VERSION.SDK_INT >= 36) "i" else ""),
                direct + "9configureEiPK20sensors_direct_cfg_t",
                direct + "7destroyEv",
                "_ZN7android20AidlSensorHalWrapper21registerDirectChannelEPK20sensors_direct_mem_tPi",
                "_ZN7android20HidlSensorHalWrapper21registerDirectChannelEPK20sensors_direct_mem_tPi",
                direct + "16configureChannelEii",
                direct + "7stopAllEb",
                "_ZN7android20AidlSensorHalWrapper22configureDirectChannelEiiPK20sensors_direct_cfg_t",
                "_ZN7android20HidlSensorHalWrapper22configureDirectChannelEiiPK20sensors_direct_cfg_t",
            )
        val offsets =
            names.map { requireNotNull(symbols[it]) { "Missing service symbol $it" } }.toLongArray()
        val expected =
            requireNotNull(
                    javaClass.classLoader?.getResourceAsStream("lib/arm64-v8a/libopenlocation.so")
                )
                .use { java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes()) }
        val mounted =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.io.File("/system/lib64/libopenlocation.so").readBytes())
        check(expected.contentEquals(mounted)) {
            "Install the native companion from this APK build"
        }
        System.load("/system/lib64/libopenlocation.so")
        check(ServiceNative.install(offsets, Build.VERSION.SDK_INT)) {
            "Native service hooks unavailable"
        }
        client.handler.post(tick)
        XposedBridge.log("OpenLocation: SensorService symbols resolved from installed ELF")
    }

    private val tick =
        object : Runnable {
            override fun run() {
                try {
                    val frame = if (client.active) client.frame() else null
                    val flat = FloatArray(40)
                    if (frame != null)
                        intArrayOf(1, 2, 4, 9, 10, 11, 18, 19).forEachIndexed { i, type ->
                            frame.values(type)?.copyInto(flat, i * 5)
                        }
                    ServiceNative.publish(
                        client.active,
                        client.delivering,
                        client.targets.toIntArray(),
                        frame?.measurementElapsedNs ?: 0,
                        frame?.stepCounter ?: 0,
                        flat,
                    )
                } catch (failure: Throwable) {
                    XposedBridge.log(failure)
                } finally {
                    client.handler.postDelayed(this, 5)
                }
            }
        }
}
