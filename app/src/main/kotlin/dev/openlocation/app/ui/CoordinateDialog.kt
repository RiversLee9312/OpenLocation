package dev.openlocation.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.openlocation.app.R
import dev.openlocation.scenario.GeoPoint

@Composable
fun CoordinateDialog(initial: GeoPoint? = null, onDismiss: () -> Unit, onSave: (GeoPoint) -> Unit) {
    var latitude by remember { mutableStateOf(initial?.latitude?.toString().orEmpty()) }
    var longitude by remember { mutableStateOf(initial?.longitude?.toString().orEmpty()) }
    var altitude by remember { mutableStateOf(initial?.altitude?.toString() ?: "0") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_coordinates)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    latitude,
                    { latitude = it },
                    label = { Text(stringResource(R.string.latitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                )
                OutlinedTextField(
                    longitude,
                    { longitude = it },
                    label = { Text(stringResource(R.string.longitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                )
                OutlinedTextField(
                    altitude,
                    { altitude = it },
                    label = { Text(stringResource(R.string.altitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
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
                            GeoPoint(
                                dev.openlocation.runtime.CoordinateNumbers.parse(latitude),
                                dev.openlocation.runtime.CoordinateNumbers.parse(longitude),
                                dev.openlocation.runtime.CoordinateNumbers.parse(altitude),
                            )
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
