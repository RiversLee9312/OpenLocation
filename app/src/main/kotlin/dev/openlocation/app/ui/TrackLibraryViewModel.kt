package dev.openlocation.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openlocation.app.R
import dev.openlocation.app.data.recording.*
import dev.openlocation.runtime.RecordedFix
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TrackLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TrackRepository.get(application)
    private val mutable = MutableStateFlow<List<TrackSummary>>(emptyList())
    val tracks = mutable.asStateFlow()
    private val mutableMessage = MutableStateFlow<Int?>(null)
    val message = mutableMessage.asStateFlow()

    fun clearMessage() {
        mutableMessage.value = null
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { repository.list() }
                    .onSuccess { mutable.value = it }
                    .onFailure { mutableMessage.value = R.string.import_failed }
                delay(1000)
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { TrackDocuments.import(getApplication(), uri) }
                .onSuccess { mutableMessage.value = R.string.route_saved }
                .onFailure { mutableMessage.value = R.string.import_failed }
        }
    }

    suspend fun preview(track: TrackSummary): List<RecordedFix> =
        withContext(Dispatchers.IO) {
            runCatching { repository.preview(track.id, track.points) }.getOrDefault(emptyList())
        }

    fun rename(id: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.rename(id, name) }
                .onFailure { mutableMessage.value = R.string.invalid_track_name }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.delete(id) }
                .onFailure { mutableMessage.value = R.string.export_failed }
        }
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
}
