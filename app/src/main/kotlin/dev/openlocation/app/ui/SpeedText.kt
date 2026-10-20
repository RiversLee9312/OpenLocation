package dev.openlocation.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.openlocation.app.R
import dev.openlocation.runtime.SpeedUnit

internal fun SpeedUnit.label(): Int =
    when (this) {
        SpeedUnit.METERS_PER_SECOND -> R.string.unit_mps
        SpeedUnit.KILOMETERS_PER_HOUR -> R.string.unit_kmh
        SpeedUnit.MILES_PER_HOUR -> R.string.unit_mph
    }

@Composable
internal fun speedText(metersPerSecond: Double, unit: SpeedUnit): String =
    stringResource(
        R.string.speed_quantity,
        unit.fromMetersPerSecond(metersPerSecond),
        stringResource(unit.label()),
    )
