package dev.openlocation.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.openlocation.app.R
import dev.openlocation.app.data.recording.TrackSummary
import java.text.DateFormat
import java.util.Date

@Composable
fun trackSummary(track: TrackSummary) =
    stringResource(
        R.string.track_summary,
        stringResource(R.string.distance_value, track.distance / 1000),
        pluralStringResource(R.plurals.route_points, track.points, track.points),
    )

fun TrackSummary.displayName() = name.ifBlank {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(started))
}

fun TrackSummary.sourceLabel() =
    when (kind) {
        "imported" -> R.string.track_source_imported
        "saved" -> R.string.track_source_saved
        else -> R.string.track_source_recorded
    }
