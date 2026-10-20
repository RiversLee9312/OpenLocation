@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.openlocation.probe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class ProbeActivity : ComponentActivity() {
    private lateinit var monitor: ProbeMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        monitor = ProbeMonitor(this)
        setContent {
            val state by monitor.state.collectAsStateWithLifecycle()
            var error by remember { mutableStateOf<Int?>(null) }
            var tab by remember { mutableIntStateOf(0) }
            var query by remember { mutableStateOf("") }
            val permission =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    if (
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED ||
                            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) !=
                                PackageManager.PERMISSION_GRANTED
                    )
                        error = R.string.permission
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
                            .onFailure { error = R.string.export_error }
                }
            val colors =
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(this)
                else dynamicLightColorScheme(this)
            MaterialExpressiveTheme(
                colorScheme = colors,
                motionScheme = MotionScheme.expressive(),
            ) {
                Scaffold(
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        PrimaryTabRow(selectedTabIndex = tab) {
                            listOf(R.string.overview, R.string.heading, R.string.sensors)
                                .forEachIndexed { index, title ->
                                    Tab(
                                        selected = tab == index,
                                        onClick = { tab = index },
                                        text = { Text(stringResource(title)) },
                                    )
                                }
                        }
                        LazyColumn(
                            Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
                        ) {
                            item {
                                ProbeSourceCard(state)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (state.monitoring) monitor.stop()
                                        else {
                                            error = null
                                            permission.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    Manifest.permission.ACTIVITY_RECOGNITION,
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    shapes =
                                        ButtonDefaults.shapesFor(
                                            ButtonDefaults.LargeContainerHeight
                                        ),
                                ) {
                                    Text(
                                        stringResource(
                                            if (state.monitoring) R.string.stop else R.string.start
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                error?.let {
                                    Text(
                                        stringResource(it),
                                        Modifier.padding(top = 12.dp),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            when (tab) {
                                0 -> {
                                    item { ProbeLocationCard(state) }
                                    item {
                                        ProbeCard {
                                            Text(
                                                stringResource(R.string.capture),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                FilledTonalButton(
                                                    onClick = monitor::record,
                                                    enabled = state.monitoring,
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Text(
                                                        stringResource(
                                                            if (state.recording)
                                                                R.string.stop_recording
                                                            else R.string.record
                                                        )
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        export.launch(
                                                            "OpenLocation-recording.ndjson"
                                                        )
                                                    },
                                                    enabled =
                                                        !state.recording &&
                                                            monitor.recordingFile.exists(),
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Text(stringResource(R.string.export))
                                                }
                                            }
                                            if (state.limitReached)
                                                Text(
                                                    stringResource(R.string.record_limit),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                        }
                                    }
                                }
                                1 -> item { ProbeHeadingCard(state) }
                                else -> {
                                    item {
                                        OutlinedTextField(
                                            query,
                                            { query = it },
                                            Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            label = {
                                                Text(stringResource(R.string.filter_sensors))
                                            },
                                            shape = MaterialTheme.shapes.extraLarge,
                                        )
                                    }
                                    val rows =
                                        state.readings.filter {
                                            it.sensor.name.contains(query, true) ||
                                                it.sensor.type.toString() == query.trim()
                                        }
                                    if (rows.isEmpty())
                                        item {
                                            Text(
                                                stringResource(
                                                    if (state.monitoring) R.string.no_sensors
                                                    else R.string.start_to_inspect
                                                ),
                                                Modifier.padding(16.dp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    items(rows, key = { it.instanceKey }) { SensorReadingCard(it) }
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
