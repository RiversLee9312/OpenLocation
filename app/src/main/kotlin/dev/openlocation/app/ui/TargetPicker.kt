@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.openlocation.app.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.openlocation.app.R
import dev.openlocation.runtime.Contract
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(val pkg: String, val label: String, val uid: Int)

/** Installed packages include system apps; list and icon IO never run on the UI thread. */
@Composable
fun TargetPicker(
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val applications by
        produceState<List<InstalledApp>?>(null) {
            value =
                withContext(Dispatchers.IO) {
                    val collator = Collator.getInstance()
                    context.packageManager
                        .getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                        .filter {
                            it.packageName != context.packageName && it.packageName != "android"
                        }
                        .map {
                            InstalledApp(
                                it.packageName,
                                it.loadLabel(context.packageManager).toString(),
                                it.uid,
                            )
                        }
                        .sortedWith { a, b ->
                            val priority = (b.pkg in selected).compareTo(a.pkg in selected)
                            if (priority != 0) priority else collator.compare(a.label, b.label)
                        }
                }
        }
    val filtered =
        remember(applications, query) {
            applications?.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
            Text(
                stringResource(R.string.target_apps),
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                query,
                { query = it },
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                label = { Text(stringResource(R.string.search_apps)) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Text(
                stringResource(R.string.module_setup),
                Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filtered == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(filtered, key = { it.pkg }) { app ->
                        val checked = app.pkg in selected
                        val icon by
                            produceState<ImageBitmap?>(null, app.pkg) {
                                value =
                                    withContext(Dispatchers.IO) {
                                        runCatching {
                                                context.packageManager
                                                    .getApplicationIcon(app.pkg)
                                                    .toBitmap(96, 96)
                                                    .asImageBitmap()
                                            }
                                            .getOrNull()
                                    }
                            }
                        ListItem(
                            modifier =
                                Modifier.toggleable(
                                    checked,
                                    enabled = app.pkg != Contract.PROBE_PACKAGE && app.uid >= 10000,
                                    role = Role.Switch,
                                ) {
                                    onToggle(app.pkg, it)
                                },
                            headlineContent = { Text(app.label) },
                            supportingContent = {
                                Text(
                                    if (app.uid < 10000) stringResource(R.string.system_service_app)
                                    else app.pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = {
                                icon?.let { Image(it, null, Modifier.size(40.dp)) }
                                    ?: Spacer(Modifier.size(40.dp))
                            },
                            trailingContent = {
                                Switch(
                                    checked,
                                    onCheckedChange = null,
                                    enabled = app.pkg != Contract.PROBE_PACKAGE && app.uid >= 10000,
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
    }
}
