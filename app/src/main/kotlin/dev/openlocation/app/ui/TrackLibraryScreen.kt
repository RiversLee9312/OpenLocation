@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.openlocation.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.openlocation.app.R
import dev.openlocation.app.data.recording.TrackSummary
import dev.openlocation.scenario.GeoPoint

@Composable
fun TrackLibraryScreen(
    sceneRunning: Boolean,
    sceneVm: SceneViewModel,
    onUse: (Long) -> Unit,
    vm: TrackLibraryViewModel = viewModel(),
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            sceneVm.message(it)
            vm.clearMessage()
        }
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Long?>(null) }
    var rename by remember { mutableStateOf<TrackSummary?>(null) }
    var deleting by remember { mutableStateOf<TrackSummary?>(null) }
    var exporting by remember { mutableStateOf<TrackSummary?>(null) }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(vm::import)
        }
    val exporter =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/gpx+xml")
        ) { uri ->
            exporting?.let { if (uri != null) vm.export(it.id, uri) }
            exporting = null
        }
    fun import() {
        importer.launch(
            arrayOf(
                "application/gpx+xml",
                "application/xml",
                "text/xml",
                "application/octet-stream",
            )
        )
    }
    if (tracks.isEmpty())
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Symbol(R.drawable.ic_route, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.no_tracks), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = { import() }) {
                Symbol(R.drawable.ic_download)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_gpx))
            }
        }
    else
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.weight(1f),
                    label = { Text(stringResource(R.string.search_tracks)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                )
                FilledTonalIconButton(onClick = { import() }, modifier = Modifier.size(56.dp)) {
                    Symbol(R.drawable.ic_download, stringResource(R.string.import_gpx))
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(tracks.filter { it.displayName().contains(query, true) }, key = { it.id }) {
                    track ->
                    Surface(
                        onClick = { selected = track.id },
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    track.displayName(),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Column {
                                    Text(
                                        stringResource(
                                            if (track.active) R.string.recording_track
                                            else track.sourceLabel()
                                        )
                                    )
                                    Text(trackSummary(track))
                                }
                            },
                            leadingContent = {
                                Symbol(
                                    if (track.kind == "recording") R.drawable.ic_fiber_manual_record
                                    else R.drawable.ic_route
                                )
                            },
                            trailingContent = { Symbol(R.drawable.ic_chevron_right) },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                        )
                    }
                }
            }
        }
    tracks
        .firstOrNull { it.id == selected }
        ?.let { track ->
            val fixes by
                produceState<List<dev.openlocation.runtime.RecordedFix>>(
                    emptyList(),
                    track.id,
                    track.points,
                ) {
                    value = vm.preview(track)
                }
            val points =
                remember(fixes) {
                    fixes.map {
                        GeoPoint(
                            it.latitude.coerceIn(-85.0, 85.0),
                            it.longitude,
                            it.altitude ?: 0.0,
                        )
                    }
                }
            ModalBottomSheet(
                onDismissRequest = { selected = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(track.displayName(), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(track.sourceLabel()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (points.isNotEmpty())
                        RouteMap(
                            points,
                            null,
                            Modifier.fillMaxWidth()
                                .height(220.dp)
                                .clip(MaterialTheme.shapes.extraLarge),
                        )
                    Text(
                        trackSummary(track),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = {
                            selected = null
                            onUse(track.id)
                        },
                        enabled = !track.active && !sceneRunning && track.points >= 2,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Symbol(R.drawable.ic_route)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.use_track))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                exporting = track
                                exporter.launch(
                                    track.displayName().replace('/', '_').replace('\\', '_') +
                                        ".gpx"
                                )
                            },
                            enabled = !track.active && track.points > 0,
                        ) {
                            Symbol(R.drawable.ic_upload)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.export_gpx))
                        }
                        TextButton(onClick = { rename = track }) {
                            Text(stringResource(R.string.rename_track))
                        }
                        TextButton(onClick = { deleting = track }, enabled = !track.active) {
                            Text(stringResource(R.string.delete_track))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    rename?.let { track ->
        var name by remember(track.id) { mutableStateOf(track.displayName()) }
        val valid = name.isNotBlank() && name.length <= 120 && name.none { it.isISOControl() }
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text(stringResource(R.string.rename_track)) },
            text = {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(R.string.track_name)) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = {
                        if (!valid) Text(stringResource(R.string.invalid_track_name))
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.rename(track.id, name)
                        rename = null
                    },
                    enabled = valid,
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { rename = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    deleting?.let { track ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_track_question)) },
            text = { Text(track.displayName()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(track.id)
                        selected = null
                        deleting = null
                    }
                ) {
                    Text(stringResource(R.string.delete_track))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
