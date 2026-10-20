package dev.openlocation.runtime

/** Display preference only. Scene files and Android location/sensor APIs always retain SI units. */
enum class SpeedUnit(val id: String, private val factor: Double) {
    METERS_PER_SECOND("mps", 1.0),
    KILOMETERS_PER_HOUR("kmh", 3.6),
    MILES_PER_HOUR("mph", 3600.0 / 1609.344);

    fun fromMetersPerSecond(value: Double): Double = value * factor

    fun toMetersPerSecond(value: Double): Double = value / factor

    companion object {
        fun fromId(id: String?): SpeedUnit =
            entries.firstOrNull { it.id == id } ?: METERS_PER_SECOND
    }
}
