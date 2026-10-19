package dev.openlocation.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private data class LicenseEntry(val title: String, val license: String, val text: String)

/** License text is kept verbatim as legal source material, separate from translated UI copy. */
@Composable
fun LicensesScreen() {
    val context = LocalContext.current
    val entries by
        produceState<List<LicenseEntry>>(emptyList()) {
            value =
                withContext(Dispatchers.IO) {
                    val list =
                        JSONArray(
                            context.assets.open("licenses.json").bufferedReader().use {
                                it.readText()
                            }
                        )
                    (0 until list.length()).map { i ->
                        list.getJSONObject(i).let {
                            LicenseEntry(
                                it.getString("title"),
                                it.getString("license"),
                                it.getString("text"),
                            )
                        }
                    }
                }
        }
    var expanded by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.title }) { entry ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text(entry.license) },
                        modifier =
                            Modifier.clickable {
                                expanded = if (expanded == entry.title) null else entry.title
                            },
                        colors =
                            ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                    )
                    if (expanded == entry.title)
                        SelectionContainer {
                            Text(
                                entry.text,
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                }
            }
        }
    }
}
