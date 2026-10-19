package dev.openlocation.hook

import android.location.*
import de.robv.android.xposed.XposedHelpers
import dev.openlocation.engine.*
import kotlin.math.*

/** Android parcelables generated from the shared orbital epoch. */
internal object GnssPackets {
    fun status(epoch: GnssEpoch): GnssStatus =
        GnssStatus.Builder()
            .apply {
                epoch.satellites.forEach {
                    addSatellite(
                        it.constellation,
                        it.svid,
                        it.cn0DbHz.toFloat(),
                        it.elevationDegrees.toFloat(),
                        it.azimuthDegrees.toFloat(),
                        true,
                        true,
                        it.usedInFix,
                        true,
                        it.carrierFrequencyHz.toFloat(),
                        true,
                        (it.cn0DbHz - 2).coerceAtLeast(0.0).toFloat(),
                    )
                }
            }
            .build()

    fun raw(epoch: GnssEpoch): GnssMeasurementsEvent {
        val gpsNs = (epoch.utcMillis - GnssEngine.GPS_EPOCH_MILLIS + 18_000) * 1_000_000L
        val clock = XposedHelpers.newInstance(GnssClock::class.java) as GnssClock
        fun set(target: Any, name: String, value: Any) {
            XposedHelpers.callMethod(target, name, value)
        }
        set(clock, "setTimeNanos", epoch.elapsedNs)
        set(clock, "setFullBiasNanos", epoch.elapsedNs - gpsNs)
        set(clock, "setBiasNanos", -epoch.receiverClockBiasNs)
        set(clock, "setBiasUncertaintyNanos", .5)
        set(clock, "setDriftNanosPerSecond", 0.0)
        set(clock, "setDriftUncertaintyNanosPerSecond", .01)
        set(clock, "setHardwareClockDiscontinuityCount", 0)
        set(clock, "setLeapSecond", 18)
        set(clock, "setElapsedRealtimeNanos", epoch.elapsedNs)
        set(clock, "setElapsedRealtimeUncertaintyNanos", 100.0)
        val measurements =
            epoch.satellites
                .filter { it.cn0DbHz >= 10 }
                .map { sat ->
                    val m =
                        XposedHelpers.newInstance(GnssMeasurement::class.java) as GnssMeasurement
                    val gnssOffset = if (sat.constellation == 5) 14_000_000_000L else 0L
                    // Receiver bias is represented in the receiver clock, not satellite transmit
                    // time.
                    val geometricDelay =
                        (sat.pseudorangeMeters / GnssEngine.SPEED_OF_LIGHT * 1e9 -
                                epoch.receiverClockBiasNs)
                            .roundToLong()
                    val transmit =
                        Math.floorMod(gpsNs - gnssOffset - geometricDelay, GnssEngine.GPS_WEEK_NS)
                    set(m, "setSvid", sat.svid)
                    set(m, "setConstellationType", sat.constellation)
                    set(m, "setTimeOffsetNanos", 0.0)
                    set(
                        m,
                        "setState",
                        if (sat.cn0DbHz >= 20)
                            GnssMeasurement.STATE_CODE_LOCK or GnssMeasurement.STATE_TOW_DECODED
                        else GnssMeasurement.STATE_UNKNOWN,
                    )
                    set(m, "setReceivedSvTimeNanos", transmit)
                    set(
                        m,
                        "setReceivedSvTimeUncertaintyNanos",
                        max(1.0, sat.pseudorangeUncertaintyMeters / GnssEngine.SPEED_OF_LIGHT * 1e9)
                            .roundToLong(),
                    )
                    set(m, "setCn0DbHz", sat.cn0DbHz)
                    set(m, "setBasebandCn0DbHz", (sat.cn0DbHz - 2).coerceAtLeast(0.0))
                    set(m, "setCarrierFrequencyHz", sat.carrierFrequencyHz.toFloat())
                    set(m, "setCodeType", "C")
                    set(m, "setPseudorangeRateMetersPerSecond", sat.rangeRateMetersPerSecond)
                    set(
                        m,
                        "setPseudorangeRateUncertaintyMetersPerSecond",
                        max(.02, sat.pseudorangeUncertaintyMeters * .03),
                    )
                    set(
                        m,
                        "setAccumulatedDeltaRangeState",
                        if (sat.cn0DbHz >= 25) GnssMeasurement.ADR_STATE_VALID
                        else GnssMeasurement.ADR_STATE_UNKNOWN,
                    )
                    set(m, "setAccumulatedDeltaRangeMeters", sat.accumulatedDeltaRangeMeters)
                    set(
                        m,
                        "setAccumulatedDeltaRangeUncertaintyMeters",
                        max(.002, sat.pseudorangeUncertaintyMeters * .02),
                    )
                    set(
                        m,
                        "setMultipathIndicator",
                        if (sat.cn0DbHz < 30) GnssMeasurement.MULTIPATH_INDICATOR_DETECTED
                        else GnssMeasurement.MULTIPATH_INDICATOR_NOT_DETECTED,
                    )
                    set(m, "setAutomaticGainControlLevelInDb", (40 - sat.cn0DbHz) * .5)
                    m
                }
        return GnssMeasurementsEvent.Builder()
            .setClock(clock)
            .setMeasurements(measurements)
            .setIsFullTracking(true)
            .build()
    }
}
