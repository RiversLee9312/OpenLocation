@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.openlocation.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.openlocation.app.R
import dev.openlocation.scenario.*

@Composable
fun RouteScreen(
    state: SceneUiState,
    vm: SceneViewModel,
    onRecord: () -> Unit,
    onLibrary: () -> Unit,
) {
    val points = state.session.scene.routeWgs84
    val editable = !state.session.running
    var wavelength by
        remember(state.session.scene.routeDeviationWavelengthMeters) {
            mutableDoubleStateOf(state.session.scene.routeDeviationWavelengthMeters)
        }
    var deviation by
        remember(state.session.scene.routeDeviationMeters) {
            mutableDoubleStateOf(state.session.scene.routeDeviationMeters)
        }
    var dialog by remember { mutableStateOf(false) }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(vm::import)
        }
    val exporter =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/gpx+xml")
        ) {
            it?.let(vm::export)
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            onClick = onRecord,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.record_track)) },
                supportingContent = { Text(stringResource(R.string.record_track_hint)) },
                leadingContent = { Symbol(R.drawable.ic_fiber_manual_record) },
                trailingContent = { Symbol(R.drawable.ic_chevron_right) },
                colors =
                    ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
            )
        }
        Surface(
            onClick = onLibrary,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.track_library)) },
                supportingContent = { Text(stringResource(R.string.track_library_description)) },
                leadingContent = { Symbol(R.drawable.ic_route) },
                trailingContent = { Symbol(R.drawable.ic_chevron_right) },
                colors =
                    ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
            )
        }
        RouteMap(
            state.nominalRoute.ifEmpty { points },
            null,
            Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(28.dp)),
            editable,
            deviationMeters = deviation,
        ) { point ->
            vm.configure { it.copy(routeWgs84 = it.routeWgs84 + point) }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    pluralStringResource(R.plurals.route_points, points.size, points.size),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.route_coordinates),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                IconButton(
                    onClick = { vm.configure { it.copy(routeWgs84 = it.routeWgs84.dropLast(1)) } },
                    enabled = editable && points.size > 2,
                ) {
                    Symbol(R.drawable.ic_undo, stringResource(R.string.undo))
                }
                FilledTonalIconButton(onClick = { dialog = true }, enabled = editable) {
                    Symbol(R.drawable.ic_add, stringResource(R.string.add_point))
                }
            }
        }
        Text(
            stringResource(if (editable) R.string.route_hint else R.string.edit_stopped),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (editable) {
            ParameterSlider(
                R.string.route_variation,
                deviation,
                0.0..30.0,
                stringResource(R.string.meters_value, deviation),
            ) {
                deviation = it
            }
            if (deviation > 0)
                ParameterSlider(
                    R.string.route_variation_period,
                    wavelength,
                    10.0..200.0,
                    stringResource(R.string.meters_value, wavelength),
                ) {
                    wavelength = it
                }
            OutlinedButton(
                onClick = {
                    vm.configure {
                        it.copy(
                            routeDeviationMeters = deviation,
                            routeDeviationWavelengthMeters = wavelength,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::saveRoute, enabled = editable) {
                Symbol(R.drawable.ic_add)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save_to_library))
            }
            FilledTonalButton(
                onClick = {
                    importer.launch(
                        arrayOf(
                            "application/gpx+xml",
                            "application/xml",
                            "text/xml",
                            "application/octet-stream",
                        )
                    )
                },
                enabled = editable,
            ) {
                Symbol(R.drawable.ic_download)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_gpx))
            }
            OutlinedButton(onClick = { exporter.launch("OpenLocation.gpx") }) {
                Symbol(R.drawable.ic_upload)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_gpx))
            }
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                points.take(8).forEachIndexed { i, p ->
                    Text(
                        stringResource(R.string.point_value, i + 1, p.latitude, p.longitude),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (points.size > 8)
                    Text(
                        pluralStringResource(R.plurals.route_points, points.size, points.size),
                        style = MaterialTheme.typography.labelMedium,
                    )
            }
        }
        TextButton(
            onClick = { vm.configure { it.copy(routeWgs84 = DemoScenes.route()) } },
            enabled = editable,
        ) {
            Text(stringResource(R.string.reset_route))
        }
        Spacer(Modifier.height(8.dp))
    }
    if (dialog)
        CoordinateDialog(
            onDismiss = { dialog = false },
            onSave = { point ->
                vm.configure { it.copy(routeWgs84 = it.routeWgs84 + point) }
                dialog = false
            },
        )
}

@Composable
private fun CoordinateDialog(onDismiss: () -> Unit, onSave: (GeoPoint) -> Unit) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var altitude by remember { mutableStateOf("0") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_point)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    latitude,
                    { latitude = it },
                    label = { Text(stringResource(R.string.latitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    longitude,
                    { longitude = it },
                    label = { Text(stringResource(R.string.longitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    altitude,
                    { altitude = it },
                    label = { Text(stringResource(R.string.altitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (invalid)
                    Text(
                        stringResource(R.string.invalid_coordinates),
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                            GeoPoint(latitude.toDouble(), longitude.toDouble(), altitude.toDouble())
                        }
                        .onSuccess(onSave)
                        .onFailure { invalid = true }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
