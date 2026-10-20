@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.openlocation.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.openlocation.app.R
import dev.openlocation.app.data.PlaceResult
import dev.openlocation.app.data.PlaceSearch
import dev.openlocation.runtime.RouteDraft
import dev.openlocation.scenario.GeoPoint
import kotlinx.coroutines.launch

private enum class EditMode {
    SELECT,
    ADD,
    MOVE,
}

/** Full-screen transaction: map gestures never mutate the active route. */
@Composable
fun RouteEditor(initial: List<GeoPoint>, vm: SceneViewModel, onDismiss: () -> Unit) {
    val draft = remember { RouteDraft(initial) }
    var version by remember { mutableIntStateOf(0) }
    val points = remember(version) { draft.points }
    var selected by remember { mutableStateOf<Int?>(null) }
    var mode by remember { mutableStateOf(EditMode.SELECT) }
    var center by remember { mutableStateOf(initial.first()) }
    var fit by remember { mutableIntStateOf(1) }
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    var focusId by remember { mutableIntStateOf(0) }
    var showPoints by remember { mutableStateOf(false) }
    var coordinate by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PlaceResult>?>(null) }
    var searchFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val search = remember { PlaceSearch(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    fun changed(block: RouteDraft.() -> Unit) {
        draft.block()
        version++
        invalid = false
        selected = selected?.takeIf { it in draft.points.indices }
    }
    fun close() {
        if (!saving) {
            if (points != initial) discard = true else onDismiss()
        }
    }
    fun searchNow() {
        if (query.isBlank() || searching) return
        keyboard?.hide()
        searching = true
        searchFailed = false
        results = null
        val submitted = query
        scope.launch {
            runCatching { search.search(submitted) }
                .onSuccess { if (query == submitted) results = it }
                .onFailure { if (query == submitted) searchFailed = true }
            searching = false
        }
    }
    fun select(index: Int) {
        selected = index
        focus = MapFocus(points[index], ++focusId)
        mode = EditMode.SELECT
    }
    fun remove(index: Int) {
        val previous = selected
        changed { delete(index) }
        selected = previous?.let { if (it == index) null else if (it > index) it - 1 else it }
    }
    Dialog(
        onDismissRequest = ::close,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler { close() }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.edit_route)) },
                    navigationIcon = {
                        IconButton(onClick = ::close, enabled = !saving) {
                            Symbol(R.drawable.ic_close, stringResource(R.string.close_editor))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                saving = true
                                invalid = false
                                vm.replaceRoute(points) { ok ->
                                    saving = false
                                    if (ok) onDismiss() else invalid = true
                                }
                            },
                            enabled = points.size >= 2 && !saving,
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    },
                )
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        Modifier.navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                selected?.let {
                                    stringResource(R.string.selected_point, it + 1, points.size)
                                }
                                    ?: pluralStringResource(
                                        R.plurals.route_points,
                                        points.size,
                                        points.size,
                                    ),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                            )
                            Row {
                                IconButton(
                                    onClick = { changed { undo() } },
                                    enabled = draft.canUndo && !saving,
                                ) {
                                    Symbol(R.drawable.ic_undo, stringResource(R.string.undo))
                                }
                                IconButton(
                                    onClick = { changed { redo() } },
                                    enabled = draft.canRedo && !saving,
                                ) {
                                    Symbol(R.drawable.ic_redo, stringResource(R.string.redo))
                                }
                                IconButton(
                                    onClick = { showPoints = true },
                                    enabled = points.isNotEmpty(),
                                ) {
                                    Symbol(
                                        R.drawable.ic_list_alt,
                                        stringResource(R.string.route_point_list),
                                    )
                                }
                            }
                        }
                        if (invalid)
                            Text(
                                stringResource(R.string.route_limit),
                                color = MaterialTheme.colorScheme.error,
                            )
                        if (mode == EditMode.SELECT) {
                            Text(
                                stringResource(R.string.select_point_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { mode = EditMode.ADD },
                                    enabled = points.size < 10000 && !saving,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Symbol(R.drawable.ic_add)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.add_point))
                                }
                                OutlinedButton(
                                    onClick = {
                                        selected?.let {
                                            center = points[it]
                                            focus = MapFocus(points[it], ++focusId)
                                            mode = EditMode.MOVE
                                        }
                                    },
                                    enabled = selected != null && !saving,
                                ) {
                                    Text(stringResource(R.string.move_point))
                                }
                                IconButton(
                                    onClick = { selected?.let(::remove) },
                                    enabled = selected != null && !saving,
                                ) {
                                    Symbol(
                                        R.drawable.ic_delete,
                                        stringResource(R.string.delete_point),
                                    )
                                }
                            }
                            if (selected != null)
                                TextButton(onClick = { coordinate = true }, enabled = !saving) {
                                    Text(stringResource(R.string.edit_coordinates))
                                }
                        } else {
                            Text(
                                stringResource(R.string.map_center_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(
                                    R.string.coordinate_pair,
                                    center.latitude,
                                    center.longitude,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { mode = EditMode.SELECT }) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Button(
                                    onClick = {
                                        if (mode == EditMode.ADD)
                                            changed { selected = insert(selected, center) }
                                        else
                                            selected?.let { index ->
                                                changed {
                                                    move(
                                                        index,
                                                        center.copy(
                                                            altitude = points[index].altitude
                                                        ),
                                                    )
                                                }
                                            }
                                        mode = EditMode.SELECT
                                    },
                                    Modifier.weight(1f),
                                ) {
                                    Text(
                                        stringResource(
                                            if (mode == EditMode.ADD) R.string.add_here
                                            else R.string.move_here
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                RouteMap(
                    points,
                    null,
                    Modifier.fillMaxSize(),
                    fitToPoints = false,
                    controlPoints = points,
                    selectedIndex = selected,
                    focus = focus,
                    fitRequest = fit,
                    onVertex = { if (mode == EditMode.SELECT) selected = it },
                    onCenter = { center = it },
                )
                if (mode != EditMode.SELECT)
                    Surface(
                        Modifier.align(Alignment.Center).size(48.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 3.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Symbol(
                                R.drawable.ic_add_location_alt,
                                stringResource(R.string.map_center),
                                Modifier.size(28.dp),
                            )
                        }
                    }
                Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        query,
                        {
                            query = it
                            results = null
                            searchFailed = false
                        },
                        Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search_places)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchNow() }),
                        trailingIcon = {
                            if (searching)
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else
                                IconButton(onClick = ::searchNow, enabled = query.isNotBlank()) {
                                    Symbol(R.drawable.ic_search, stringResource(R.string.search))
                                }
                        },
                    )
                    if (searchFailed || results != null)
                        Surface(shape = MaterialTheme.shapes.extraLarge, shadowElevation = 3.dp) {
                            Column {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    IconButton(
                                        onClick = {
                                            results = null
                                            searchFailed = false
                                        }
                                    ) {
                                        Symbol(
                                            R.drawable.ic_close,
                                            stringResource(R.string.close_results),
                                        )
                                    }
                                }
                                if (searchFailed || results?.isEmpty() == true)
                                    Text(
                                        stringResource(
                                            if (searchFailed) R.string.place_search_failed
                                            else R.string.no_places
                                        ),
                                        Modifier.padding(16.dp),
                                    )
                                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                                    itemsIndexed(results.orEmpty()) { _, result ->
                                        Surface(
                                            onClick = {
                                                focus = MapFocus(result.point, ++focusId)
                                                center = result.point
                                                results = null
                                                keyboard?.hide()
                                            }
                                        ) {
                                            ListItem(
                                                headlineContent = {
                                                    Text(result.title, maxLines = 3)
                                                },
                                                leadingContent = {
                                                    Symbol(R.drawable.ic_my_location)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
                FilledTonalIconButton(
                    onClick = { fit++ },
                    Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp),
                    enabled = points.isNotEmpty(),
                ) {
                    Symbol(R.drawable.ic_center_focus_strong, stringResource(R.string.fit_route))
                }
            }
        }
        if (showPoints)
            ModalBottomSheet(onDismissRequest = { showPoints = false }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.route_point_list),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(
                        onClick = {
                            changed { clear() }
                            selected = null
                            showPoints = false
                        },
                        enabled = !saving,
                    ) {
                        Text(stringResource(R.string.new_route))
                    }
                }
                LazyColumn(
                    Modifier.fillMaxWidth().fillMaxHeight(.65f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(points) { index, point ->
                        Surface(
                            onClick = {
                                select(index)
                                showPoints = false
                            },
                            color =
                                if (index == selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface,
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(
                                            R.string.point_value,
                                            index + 1,
                                            point.latitude,
                                            point.longitude,
                                        )
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { remove(index) }, enabled = !saving) {
                                        Symbol(
                                            R.drawable.ic_delete,
                                            stringResource(R.string.delete_point),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        if (coordinate && selected != null)
            CoordinateDialog(points[selected!!], onDismiss = { coordinate = false }) { point ->
                changed { move(selected!!, point) }
                focus = MapFocus(point, ++focusId)
                coordinate = false
            }
        if (discard)
            AlertDialog(
                onDismissRequest = { discard = false },
                title = { Text(stringResource(R.string.discard_route_title)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { discard = false }) {
                        Text(stringResource(R.string.keep_editing))
                    }
                },
            )
    }
}
