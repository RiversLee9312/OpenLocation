@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.openlocation.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.openlocation.app.R
import dev.openlocation.app.data.recording.TrackSummary
import dev.openlocation.scenario.GeoPoint
import java.text.NumberFormat

@Composable
fun TrackRecordingScreen(sceneVm: SceneViewModel, vm: TrackRecordingViewModel = viewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            sceneVm.message(it)
            vm.clearMessage()
        }
    }
    val context = LocalContext.current
    val current = state.current
    val active = state.active != null
    var history by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TrackSummary?>(null) }
    var exporting by remember { mutableStateOf<Long?>(null) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) vm.start()
            else sceneVm.message(R.string.recording_permission)
        }
    val exporter =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/gpx+xml")
        ) { uri ->
            exporting?.let { id -> if (uri != null) vm.export(id, uri) }
            exporting = null
        }
    val elapsed =
        if (current == null) 0L
        else
            ((if (active) state.now else current.ended ?: current.started) - current.started)
                .coerceAtLeast(0) / 1000
    val points =
        remember(state.points) {
            // Web Mercator display clips polar latitudes; stored/exported observations stay
            // unchanged.
            state.points.map {
                GeoPoint(it.latitude.coerceIn(-85.0, 85.0), it.longitude, it.altitude ?: 0.0)
            }
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(36.dp))) {
            if (points.isNotEmpty())
                RouteMap(
                    points,
                    points.lastOrNull(),
                    Modifier.fillMaxSize(),
                    fitToPoints = !active,
                    followPosition = active,
                )
            else
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Symbol(R.drawable.ic_route, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(
                                if (active) R.string.waiting_gps else R.string.record_track_hint
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            if (points.isNotEmpty())
                Surface(
                    Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Symbol(
                            if (active) R.drawable.ic_fiber_manual_record
                            else R.drawable.ic_check_circle,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(
                                if (active) R.string.recording_track else R.string.recorded_tracks
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            if (state.tracks.any { !it.active })
                FilledTonalIconButton(
                    onClick = { history = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) {
                    Symbol(R.drawable.ic_history, stringResource(R.string.recorded_tracks))
                }
        }
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.elapsed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                DateUtils.formatElapsedTime(elapsed),
                fontSize = 48.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            )
            current
                ?.takeUnless { it.active }
                ?.let {
                    Text(
                        it.displayName(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RecordingMetric(
                stringResource(R.string.distance),
                stringResource(R.string.distance_value, (current?.distance ?: 0.0) / 1000),
                Modifier.weight(1f),
                RoundedCornerShape(
                    topStart = 28.dp,
                    bottomStart = 28.dp,
                    topEnd = 8.dp,
                    bottomEnd = 8.dp,
                ),
            )
            RecordingMetric(
                stringResource(R.string.track_point_count),
                NumberFormat.getIntegerInstance().format(current?.points ?: 0),
                Modifier.weight(1f),
                RoundedCornerShape(
                    topStart = 8.dp,
                    bottomStart = 8.dp,
                    topEnd = 28.dp,
                    bottomEnd = 28.dp,
                ),
            )
        }
        val issue = recordingIssue(current?.status)
        if (issue != null)
            Text(
                stringResource(issue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        if (active)
            state.points.lastOrNull()?.let {
                Text(
                    stringResource(R.string.accuracy_value, it.accuracy),
                    Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        Button(
            onClick = {
                if (active) vm.stop()
                else if (
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                )
                    vm.start()
                else
                    permission.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(64.dp),
        ) {
            Symbol(if (active) R.drawable.ic_stop else R.drawable.ic_fiber_manual_record)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(if (active) R.string.stop_recording else R.string.start_recording),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (current != null && !active)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        exporting = current.id
                        exporter.launch("OpenLocation-${current.id}.gpx")
                    },
                    enabled = current.points > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Symbol(R.drawable.ic_upload)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_gpx))
                }
                IconButton(onClick = { deleting = current }) {
                    Symbol(R.drawable.ic_delete, stringResource(R.string.delete_recording))
                }
            }
        Spacer(Modifier.height(8.dp))
    }
    if (history)
        ModalBottomSheet(
            onDismissRequest = { history = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                stringResource(R.string.recorded_tracks),
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            LazyColumn(
                Modifier.fillMaxWidth().fillMaxHeight(.8f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.tracks.filter { !it.active }, key = { it.id }) { track ->
                    Surface(
                        onClick = {
                            vm.select(track.id)
                            history = false
                        }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(track.displayName())
                            },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.recording_stats,
                                        DateUtils.formatElapsedTime(
                                            ((track.ended ?: track.started) - track.started)
                                                .coerceAtLeast(0) / 1000
                                        ),
                                        track.distance / 1000,
                                        pluralStringResource(
                                            R.plurals.route_points,
                                            track.points,
                                            track.points,
                                        ),
                                    )
                                )
                            },
                            leadingContent = { Symbol(R.drawable.ic_route) },
                            trailingContent = { Symbol(R.drawable.ic_chevron_right) },
                        )
                    }
                }
            }
        }
    deleting?.let { track ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_recording_question)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(track.id)
                        deleting = null
                    }
                ) {
                    Text(stringResource(R.string.delete_recording))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RecordingMetric(
    label: String,
    value: String,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Surface(modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
        }
    }
}

private fun recordingIssue(status: String?) =
    when (status) {
        "interrupted" -> R.string.recording_interrupted
        "permission" -> R.string.recording_permission
        "provider" -> R.string.recording_gps_disabled
        "limit" -> R.string.recording_limit
        "storage" -> R.string.recording_failed
        else -> null
    }
