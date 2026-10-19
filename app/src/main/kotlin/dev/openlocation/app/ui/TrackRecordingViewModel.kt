package dev.openlocation.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openlocation.app.R
import dev.openlocation.app.data.recording.*
import dev.openlocation.runtime.RecordedFix
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RecordingUiState(
    val tracks: List<TrackSummary> = emptyList(),
    val selected: Long = 0,
    val points: List<RecordedFix> = emptyList(),
    val now: Long = System.currentTimeMillis(),
) {
    val active
        get() = tracks.firstOrNull { it.active }

    val current
        get() = active ?: tracks.firstOrNull { it.id == selected } ?: tracks.firstOrNull()
}

/** Recorder presentation owns no synthetic scene state and performs all storage work off main. */
class TrackRecordingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TrackRepository.get(application)
    private val mutable = MutableStateFlow(RecordingUiState())
    val ui = mutable.asStateFlow()
    private val mutableMessage = MutableStateFlow<Int?>(null)
    val message = mutableMessage.asStateFlow()

    fun clearMessage() {
        mutableMessage.value = null
    }

    @Volatile private var selected = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            var previewKey = 0L to -1
            var points = emptyList<RecordedFix>()
            while (isActive) {
                runCatching {
                        val tracks = repository.list().filter { it.kind == "recording" }
                        val current =
                            tracks.firstOrNull { it.active }
                                ?: tracks.firstOrNull { it.id == selected }
                                ?: tracks.firstOrNull()
                        val key = (current?.id ?: 0L) to (current?.points ?: 0)
                        if (key != previewKey) {
                            points =
                                current?.let { repository.preview(it.id, it.points) } ?: emptyList()
                            previewKey = key
                        }
                        mutable.value =
                            RecordingUiState(tracks, selected, points, System.currentTimeMillis())
                    }
                    .onFailure { mutableMessage.value = R.string.recording_failed }
                delay(1000)
            }
        }
    }

    fun select(id: Long) {
        selected = id
    }

    fun start() {
        runCatching {
                getApplication<Application>()
                    .startForegroundService(
                        Intent(getApplication(), TrackRecordingService::class.java)
                    )
            }
            .onFailure { mutableMessage.value = R.string.recording_failed }
    }

    fun stop() {
        getApplication<Application>()
            .startService(
                Intent(getApplication(), TrackRecordingService::class.java).setAction("stop")
            )
    }

    fun export(id: Long, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                    getApplication<Application>()
                        .contentResolver
                        .openOutputStream(uri, "wt")!!
                        .use { repository.export(id, it) }
                }
                .onSuccess { mutableMessage.value = R.string.route_saved }
                .onFailure { mutableMessage.value = R.string.export_failed }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.delete(id) }
                .onFailure { mutableMessage.value = R.string.recording_failed }
        }
    }
}
