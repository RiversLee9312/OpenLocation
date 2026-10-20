@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.openlocation.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.openlocation.app.R
import dev.openlocation.runtime.Contract
import dev.openlocation.runtime.SpeedUnit
import dev.openlocation.scenario.*

@Composable
fun Symbol(id: Int, label: String? = null, modifier: Modifier = Modifier.size(24.dp)) {
    Icon(painterResource(id), label, modifier)
}

@Composable
fun OpenLocationApp(state: SceneUiState, vm: SceneViewModel) {
    if (!usageNoticeAccepted()) return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var licenses by rememberSaveable { mutableStateOf(false) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var library by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var mismatchWarning by rememberSaveable { mutableStateOf(!state.compatible) }
    if (mismatchWarning)
        AlertDialog(
            onDismissRequest = { mismatchWarning = false },
            title = { Text(stringResource(R.string.device_mismatch_title)) },
            text = { Text(stringResource(R.string.device_mismatch_body)) },
            confirmButton = {
                TextButton(onClick = { mismatchWarning = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(context.getString(it))
            vm.message(null)
        }
    }
    BackHandler(library || recording || licenses || settings || tab != 0) {
        if (library) library = false
        else if (recording) recording = false
        else if (licenses) licenses = false else if (settings) settings = false else tab = 0
    }
    val titles = listOf(R.string.scenes, R.string.routes, R.string.diagnostics)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (library) R.string.track_library
                            else if (recording) R.string.record_track
                            else if (licenses) R.string.open_source_licenses
                            else if (settings) R.string.settings else titles[tab]
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (library || recording || settings || licenses)
                        IconButton(
                            onClick = {
                                if (library) library = false
                                else if (recording) recording = false
                                else if (licenses) licenses = false else settings = false
                            }
                        ) {
                            Symbol(R.drawable.ic_arrow_back, stringResource(R.string.back))
                        }
                },
                actions = {
                    if (!library && !recording && !settings && !licenses)
                        IconButton(onClick = { settings = true }) {
                            Symbol(R.drawable.ic_settings, stringResource(R.string.settings))
                        }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        bottomBar = {
            if (!library && !recording && !settings && !licenses)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    listOf(R.drawable.ic_play_arrow, R.drawable.ic_route, R.drawable.ic_sensors)
                        .forEachIndexed { i, icon ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Symbol(icon) },
                                label = { Text(stringResource(titles[i])) },
                            )
                        }
                }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AnimatedContent(
            targetState =
                if (library) 6
                else if (recording) 5 else if (licenses) 4 else if (settings) 3 else tab,
            modifier = Modifier.padding(padding),
            label = "screen",
        ) { selected ->
            when (selected) {
                0 -> SceneScreen(state, vm, onRoute = { tab = 1 })
                1 ->
                    RouteScreen(
                        state,
                        vm,
                        onRecord = { recording = true },
                        onLibrary = { library = true },
                    )
                2 -> DiagnosticsScreen(state, vm)
                3 ->
                    SettingsScreen(
                        state,
                        vm,
                        onDiagnostics = {
                            settings = false
                            tab = 2
                        },
                        onLicenses = { licenses = true },
                    )
                5 -> TrackRecordingScreen(vm)
                6 ->
                    TrackLibraryScreen(
                        state.session.running,
                        vm,
                        onUse = {
                            vm.useTrack(it)
                            library = false
                            tab = 1
                        },
                    )
                else -> LicensesScreen()
            }
        }
    }
}

@Composable
private fun SceneScreen(state: SceneUiState, vm: SceneViewModel, onRoute: () -> Unit) {
    val scene = state.session.scene
    val active = state.session.running
    val frame = state.frame
    var motionSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.start()
        }
    val sec =
        (state.session.progressNs(android.os.SystemClock.elapsedRealtimeNanos()) / 1_000_000_000)
            .toInt()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!state.compatible)
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    stringResource(R.string.device_mismatch_body),
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    stringResource(R.string.speed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    speedText(
                        if (active) frame?.velocity?.norm() ?: 0.0 else scene.speedMetersPerSecond,
                        state.speedUnit,
                    ),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Surface(
                color =
                    if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    stringResource(
                        if (state.session.paused) R.string.paused
                        else if (active) R.string.running else R.string.ready
                    ),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Box {
                RouteMap(
                    state.nominalRoute.ifEmpty { scene.routeWgs84 },
                    frame?.truePosition,
                    Modifier.fillMaxWidth().height(258.dp).clip(RoundedCornerShape(28.dp)),
                    deviationMeters = scene.routeDeviationMeters,
                )
                FilledTonalIconButton(
                    onClick = onRoute,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                ) {
                    Symbol(R.drawable.ic_route, stringResource(R.string.edit_route))
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ) {
                    Text(
                        stringResource(R.string.distance_value, state.lengthMeters / 1000),
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(
                stringResource(R.string.distance),
                stringResource(R.string.distance_value, (frame?.progressMeters ?: 0.0) / 1000),
                Modifier.weight(1f),
            )
            Metric(
                stringResource(R.string.elapsed),
                stringResource(R.string.duration_value, sec / 60, sec % 60),
                Modifier.weight(1f),
            )
            Metric(
                stringResource(R.string.steps),
                stringResource(
                    R.string.step_value,
                    (frame?.stepCounter ?: scene.initialStepCounter) - scene.initialStepCounter,
                ),
                Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = state.compatible,
                onClick = {
                    if (active) vm.pause()
                    else if (
                        context.checkSelfPermission(
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    )
                        notificationPermission.launch(
                            android.Manifest.permission.POST_NOTIFICATIONS
                        )
                    else vm.start()
                },
                shapes = ButtonDefaults.shapesFor(64.dp),
                modifier = Modifier.weight(1f).height(64.dp),
                contentPadding = ButtonDefaults.contentPaddingFor(64.dp),
            ) {
                Symbol(
                    if (active && !state.session.paused) R.drawable.ic_pause
                    else R.drawable.ic_play_arrow
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(
                        if (!active) R.string.start
                        else if (state.session.paused) R.string.resume else R.string.pause
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (active)
                FilledTonalIconButton(
                    onClick = vm::stop,
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Symbol(R.drawable.ic_stop, stringResource(R.string.stop))
                }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(R.string.mode)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Locomotion.entries.forEach { mode ->
                    FilterChip(
                        selected = scene.locomotion == mode,
                        onClick = { vm.mode(mode) },
                        enabled = !active,
                        label = { Text(stringResource(mode.label())) },
                        leadingIcon = { Symbol(mode.icon(), modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
        OutlinedButton(
            onClick = { motionSettings = true },
            enabled = !active,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Symbol(R.drawable.ic_tune)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.configuration))
        }
        if (active)
            ListItem(
                headlineContent = {
                    Text(stringResource(frame?.environment?.label() ?: R.string.open_road))
                },
                supportingContent = { Text(stringResource(R.string.provider_label)) },
                trailingContent = {
                    val observation = frame?.observedLocation
                    Text(
                        if (observation == null) stringResource(R.string.no_fix)
                        else stringResource(R.string.accuracy_value, observation.accuracyMeters)
                    )
                },
                colors =
                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        Spacer(Modifier.height(4.dp))
    }
    if (motionSettings)
        MotionSettings(
            scene,
            state.speedUnit,
            onDismiss = { motionSettings = false },
            onSave = { draft ->
                vm.configure { draft }
                motionSettings = false
            },
        )
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun SectionTitle(id: Int) {
    Text(
        stringResource(id),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun Locomotion.label() =
    when (this) {
        Locomotion.STILL -> R.string.still
        Locomotion.WALK -> R.string.walk
        Locomotion.RUN -> R.string.run
        Locomotion.BICYCLE -> R.string.bicycle
        Locomotion.CAR -> R.string.car
    }

internal fun Locomotion.icon() =
    when (this) {
        Locomotion.STILL -> R.drawable.ic_stop
        Locomotion.WALK -> R.drawable.ic_directions_walk
        Locomotion.RUN -> R.drawable.ic_directions_run
        Locomotion.BICYCLE -> R.drawable.ic_directions_bike
        Locomotion.CAR -> R.drawable.ic_directions_car
    }

internal fun PhonePlacement.label() =
    when (this) {
        PhonePlacement.HAND -> R.string.hand
        PhonePlacement.POCKET -> R.string.pocket
        PhonePlacement.BACKPACK -> R.string.backpack
        PhonePlacement.MOUNT -> R.string.mount
    }

internal fun Environment.label() =
    when (this) {
        Environment.OPEN -> R.string.open_road
        Environment.URBAN -> R.string.urban
        Environment.COVERED -> R.string.covered
        Environment.TUNNEL -> R.string.tunnel
        Environment.RECOVERY -> R.string.recovery
    }

@Composable
private fun DiagnosticsScreen(state: SceneUiState, vm: SceneViewModel) {
    val context = LocalContext.current
    var picker by remember { mutableStateOf(false) }
    if (picker) TargetPicker(state.targets, vm::target) { picker = false }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Symbol(R.drawable.ic_sensors, modifier = Modifier.size(32.dp))
                Text(
                    stringResource(R.string.probe_only),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.module_setup),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        runCatching {
                                context.startActivity(
                                    Intent()
                                        .setClassName(
                                            Contract.PROBE_PACKAGE,
                                            "${Contract.PROBE_PACKAGE}.ProbeActivity",
                                        )
                                )
                            }
                            .onFailure { vm.message(R.string.probe_missing) }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.open_probe))
                    Spacer(Modifier.width(8.dp))
                    Symbol(R.drawable.ic_open_in_new, modifier = Modifier.size(18.dp))
                }
            }
        }
        SectionTitle(R.string.target_apps)
        Surface(
            onClick = {
                if (!state.session.running) picker = true else vm.message(R.string.edit_stopped)
            },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.choose_apps)) },
                supportingContent = {
                    Text(
                        state.targets.sorted().joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = { Symbol(R.drawable.ic_tune) },
                colors =
                    ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
            )
        }
        SectionTitle(R.string.capabilities)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                listOf(
                        R.string.java_gps,
                        R.string.java_sensors,
                        R.string.system_location,
                        R.string.gms_location,
                        R.string.tencent_location,
                        R.string.amap_location,
                        R.string.baidu_location,
                        R.string.native_sensors,
                        R.string.gnss_status,
                        R.string.raw_gnss,
                    )
                    .forEachIndexed { index, title ->
                        val channel =
                            listOf(
                                "java_gps",
                                "java_sensors",
                                "system_location",
                                "gms_location",
                                "tencent_location",
                                "amap_location",
                                "baidu_location",
                                "native_sensors",
                                "gnss_status",
                                "raw_gnss",
                            )[index]
                        val backend =
                            when (channel) {
                                "java_sensors",
                                "native_sensors" -> "sensors"
                                "gnss_status",
                                "raw_gnss" -> "gnss"
                                else -> "location"
                            }
                        val verified = channel in state.verifiedChannels && backend in state.backend
                        ListItem(
                            headlineContent = { Text(stringResource(title)) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        if (verified) R.string.verified_channel
                                        else R.string.unverified_channel
                                    )
                                )
                            },
                            leadingContent = {
                                Symbol(
                                    if (verified) R.drawable.ic_check_circle else R.drawable.ic_info
                                )
                            },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                        )
                    }
            }
        }
        Text(
            stringResource(R.string.verification_scope),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(
                R.string.version_value,
                state.session.scene.revision,
                state.session.scene.seed,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionTitle(R.string.simulation_model)
        Text(
            stringResource(R.string.simulation_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.rate_value, state.session.scene.outputRates.imuHz),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SettingsScreen(
    state: SceneUiState,
    vm: SceneViewModel,
    onDiagnostics: () -> Unit,
    onLicenses: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionTitle(R.string.appearance)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.follow_system)) },
                trailingContent = {
                    Switch(checked = state.dynamicColor, onCheckedChange = vm::dynamic)
                },
                colors =
                    ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
            )
        }
        SectionTitle(R.string.speed_units)
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.selectableGroup()) {
                SpeedUnit.entries.forEach { unit ->
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = state.speedUnit == unit,
                                onClick = { vm.speedUnit(unit) },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.speedUnit == unit, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            stringResource(unit.label()),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        SectionTitle(R.string.language)
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APP_LOCALE_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Symbol(R.drawable.ic_language)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.language_system))
        }
        SectionTitle(R.string.gnss_signal)
        OutlinedButton(
            onClick = vm::refreshOrbits,
            enabled = !state.session.running && !state.refreshingOrbits,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.refreshingOrbits)
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Symbol(R.drawable.ic_download)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.refresh_orbits))
        }
        SectionTitle(R.string.about)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.version, dev.openlocation.app.BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDiagnostics) { Text(stringResource(R.string.diagnostics)) }
        TextButton(onClick = onLicenses) { Text(stringResource(R.string.open_source_licenses)) }
        SectionTitle(R.string.device_profile)
        Text(
            stringResource(
                if (state.compatible) R.string.matched_profile else R.string.device_mismatch_body
            )
        )
        Text(
            android.os.Build.MODEL + " / " + android.os.Build.DEVICE,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.current_fingerprint),
            style = MaterialTheme.typography.labelLarge,
        )
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(android.os.Build.FINGERPRINT, style = MaterialTheme.typography.bodySmall)
        }
    }
}
