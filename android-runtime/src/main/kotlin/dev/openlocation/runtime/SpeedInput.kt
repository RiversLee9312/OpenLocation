package dev.openlocation.runtime

import dev.openlocation.scenario.SceneConfig
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

/** Locale-aware UI boundary. Persisted scenes and engine inputs always use meters per second. */
object SpeedInput {
    /** No grouping: a decimal input must never silently become a thousands-separated value. */
    fun format(value: Double, locale: Locale): String =
        NumberFormat.getNumberInstance(locale)
            .apply {
                isGroupingUsed = false
                maximumFractionDigits = 9
                roundingMode = RoundingMode.DOWN
            }
            .format(value)

    /**
     * Accept the locale's decimal notation and ungrouped ASCII decimals; consume the whole input.
     */
    fun metersPerSecond(text: String, unit: SpeedUnit, locale: Locale): Double? {
        val input = text.trim()
        if (input.isEmpty()) return null
        val position = ParsePosition(0)
        val localized =
            NumberFormat.getNumberInstance(locale)
                .apply { isGroupingUsed = false }
                .parse(input, position)
                ?.toDouble()
                ?.takeIf { position.index == input.length }
        val value = localized ?: input.toDoubleOrNull() ?: return null
        val metersPerSecond = unit.toMetersPerSecond(value)
        return metersPerSecond.takeIf {
            it.isFinite() && it in 0.0..SceneConfig.MAX_SPEED_METERS_PER_SECOND
        }
    }
}
