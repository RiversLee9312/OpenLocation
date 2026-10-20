package dev.openlocation.probe

import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.openlocation.runtime.values
import java.util.Locale
import kotlin.math.*

@Composable
fun ProbeCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun ProbeSourceCard(state: ProbeState) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.source), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(
                    if (!state.monitoring) R.string.not_monitoring
                    else if (state.synthetic) R.string.hooked else R.string.real
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (state.monitoring)
                Text(
                    stringResource(
                        R.string.active_channels,
                        state.readings.count { it.samples > 0 },
                        state.readings.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
        }
    }
}

@Composable
fun ProbeLocationCard(state: ProbeState) = ProbeCard {
    Text(stringResource(R.string.gps), style = MaterialTheme.typography.titleMedium)
    state.location?.let { location ->
        Text(
            stringResource(
                R.string.coordinates,
                location.latitude,
                location.longitude,
                location.accuracy.toDouble(),
            ),
            style = MaterialTheme.typography.titleLarge,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.speed_value, location.speed.toDouble()))
            Text(stringResource(R.string.bearing_value, location.bearing.toDouble()))
        }
        Text(
            stringResource(R.string.timestamp, location.elapsedRealtimeNanos),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
        ?: Text(
            stringResource(R.string.no_location),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    HorizontalDivider()
    Text(
        stringResource(
            R.string.gnss_counts,
            state.usedSatellites,
            state.visibleSatellites,
            state.measurements,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Uses the received event, never the scene reference, for the displayed heading. */
fun observedAngles(row: SensorReading): FloatArray? {
    if (row.samples == 0L) return null
    if (row.sensor.type == 3) return row.values.copyOf(3)
    val matrix = FloatArray(9)
    SensorManager.getRotationMatrixFromVector(matrix, row.values)
    return SensorManager.getOrientation(matrix, FloatArray(3))
        .mapIndexed { i, v ->
            val degrees = Math.toDegrees(v.toDouble()).toFloat()
            if (i == 0) (degrees + 360) % 360 else degrees
        }
        .toFloatArray()
}

@Composable
fun ProbeHeadingCard(state: ProbeState) {
    val rows = state.readings.filter { it.sensor.type in setOf(3, 11, 15, 20) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val row =
        rows.firstOrNull { it.instanceKey == selected }
            ?: rows.firstOrNull { it.sensor.type == 11 }
            ?: rows.firstOrNull()
    val angles = row?.let(::observedAngles)
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    ProbeCard {
        Text(stringResource(R.string.heading), style = MaterialTheme.typography.titleMedium)
        if (row != null) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(row.sensor.name, maxLines = 2)
                        Text(
                            stringResource(R.string.instance, row.instanceKey, row.sensor.type),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                DropdownMenu(expanded, { expanded = false }) {
                    rows.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(item.sensor.name)
                                    Text(
                                        stringResource(
                                            R.string.instance,
                                            item.instanceKey,
                                            item.sensor.type,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                            onClick = {
                                selected = item.instanceKey
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) {
                val radius = size.minDimension * .44f
                for (i in 0 until 36) rotate(i * 10f) {
                    drawLine(
                        outline,
                        Offset(center.x, center.y - radius),
                        Offset(
                            center.x,
                            center.y - radius + if (i % 9 == 0) 18.dp.toPx() else 8.dp.toPx(),
                        ),
                        2.dp.toPx(),
                    )
                }
                if (angles != null)
                    rotate(angles[0]) {
                        drawPath(
                            Path().apply {
                                moveTo(center.x, center.y - radius * .72f)
                                lineTo(center.x - radius * .24f, center.y + radius * .4f)
                                lineTo(center.x, center.y + radius * .2f)
                                lineTo(center.x + radius * .24f, center.y + radius * .4f)
                                close()
                            },
                            primary,
                        )
                    }
            }
        }
        Text(
            angles?.let { stringResource(R.string.angle_value, it[0].toDouble()) }
                ?: stringResource(R.string.waiting),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (angles != null)
            Text(
                stringResource(R.string.tilt_values, angles[1].toDouble(), angles[2].toDouble()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        if (state.synthetic)
            state.reference?.values(3)?.let {
                Text(
                    stringResource(R.string.expected_heading, it[0].toDouble()),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        row?.let {
            Text(
                stringResource(R.string.rate, it.hz, it.samples, it.gaps),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun SensorReadingCard(row: SensorReading) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(row.sensor.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.rate, row.hz, row.samples, row.gaps),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (row.samples > 0)
                    row.values.toList().chunked(3).joinToString("\n") { group ->
                        group.joinToString("  ") { String.format(Locale.getDefault(), "%.3f", it) }
                    }
                else stringResource(R.string.waiting),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
            )
            if (expanded) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.instance, row.instanceKey, row.sensor.type),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    stringResource(R.string.timestamp, row.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    stringResource(R.string.latency, row.delayMs),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    stringResource(R.string.collapse_details),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else
                Text(
                    stringResource(R.string.expand_details),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}
