package dev.openlocation.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openlocation.app.R

@Composable
fun RouteScreen(
    state: SceneUiState,
    vm: SceneViewModel,
    onRecord: () -> Unit,
    onLibrary: () -> Unit,
) {
    val scene = state.session.scene
    var editor by remember { mutableStateOf(false) }
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
            state.nominalRoute.ifEmpty { scene.routeWgs84 },
            null,
            Modifier.fillMaxWidth().height(360.dp).clip(MaterialTheme.shapes.extraLarge),
            deviationMeters = scene.routeDeviationMeters,
        )
        Button(
            onClick = { editor = true },
            enabled = !state.session.running,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Symbol(R.drawable.ic_edit)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.edit_route))
        }
        Spacer(Modifier.height(8.dp))
    }
    if (editor) RouteEditor(scene.routeWgs84, vm) { editor = false }
}
