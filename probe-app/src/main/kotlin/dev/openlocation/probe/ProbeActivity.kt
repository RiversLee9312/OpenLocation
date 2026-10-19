@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.openlocation.probe

import android.Manifest
import android.os.Bundle
import androidx.activity.*
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale

class ProbeActivity : ComponentActivity() {
    private lateinit var monitor: ProbeMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        monitor = ProbeMonitor(this)
        setContent {
            val state by monitor.state.collectAsState()
            val context = LocalContext.current
            val color =
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            val grant =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    monitor.start()
                }
            val export =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/x-ndjson")
                ) { uri ->
                    if (uri != null)
                        runCatching {
                            contentResolver.openOutputStream(uri, "wt")!!.use { output ->
                                monitor.recordingFile.inputStream().use { it.copyTo(output) }
                            }
                        }
                }
            MaterialExpressiveTheme(colorScheme = color) {
                Scaffold(
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
                ) { padding ->
                    LazyColumn(
                        Modifier.padding(padding).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Column(
                                    Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.source),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        stringResource(
                                            if (state.synthetic) R.string.hooked else R.string.real
                                        ),
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                    state.reference?.let {
                                        Text(
                                            stringResource(
                                                R.string.revision,
                                                it.revision,
                                                it.sequence,
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (state.monitoring) monitor.stop()
                                        else
                                            grant.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    Manifest.permission.ACTIVITY_RECOGNITION,
                                                )
                                            )
                                    }
                                ) {
                                    Text(
                                        stringResource(
                                            if (state.monitoring) R.string.stop else R.string.start
                                        )
                                    )
                                }
                                FilledTonalButton(
                                    onClick = monitor::record,
                                    enabled = state.monitoring,
                                ) {
                                    Text(
                                        stringResource(
                                            if (state.recording) R.string.recording
                                            else R.string.record
                                        )
                                    )
                                }
                                OutlinedButton(
                                    onClick = { export.launch("OpenLocation-recording.ndjson") },
                                    enabled = !state.recording && monitor.recordingFile.exists(),
                                ) {
                                    Text(stringResource(R.string.export))
                                }
                            }
                        }
                        if (state.limitReached)
                            item {
                                Text(
                                    stringResource(R.string.record_limit),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        item {
                            Text(
                                stringResource(R.string.gps),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            state.location?.let { location ->
                                Text(
                                    stringResource(
                                        R.string.coordinates,
                                        location.latitude,
                                        location.longitude,
                                        location.accuracy.toDouble(),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(
                                        R.string.timestamp,
                                        location.elapsedRealtimeNanos,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                                ?: Text(
                                    stringResource(R.string.no_location),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                        }
                        item {
                            Text(
                                stringResource(R.string.sensors),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(state.readings, key = { it.instanceKey }) { row ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        row.sensor.name,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.instance,
                                            row.instanceKey,
                                            row.sensor.type,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        if (row.samples > 0)
                                            row.values.joinToString("  ") {
                                                String.format(Locale.getDefault(), "%.3f", it)
                                            }
                                        else stringResource(R.string.waiting),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.rate,
                                            row.hz,
                                            row.samples,
                                            row.gaps,
                                        ),
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        monitor.close()
        super.onDestroy()
    }
}
