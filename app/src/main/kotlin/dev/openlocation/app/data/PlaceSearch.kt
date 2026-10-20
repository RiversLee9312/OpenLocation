package dev.openlocation.app.data

import android.content.Context
import android.location.Geocoder
import dev.openlocation.scenario.GeoPoint
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

data class PlaceResult(val title: String, val point: GeoPoint)

/** Uses the configured Android geocoding provider. Queries are submitted only by a user action. */
class PlaceSearch(private val context: Context) {
    suspend fun search(query: String): List<PlaceResult> {
        val text = query.trim().take(200)
        require(text.isNotBlank())
        val pair = text.split(Regex("[,，\\s]+"))
        if (pair.size == 2) {
            val point =
                runCatching {
                        GeoPoint(
                            dev.openlocation.runtime.CoordinateNumbers.parse(pair[0]),
                            dev.openlocation.runtime.CoordinateNumbers.parse(pair[1]),
                        )
                    }
                    .getOrNull()
            if (point != null) return listOf(PlaceResult(text, point))
        }
        check(Geocoder.isPresent())
        return withTimeout(12000) {
            suspendCancellableCoroutine { continuation ->
                Geocoder(context, Locale.getDefault())
                    .getFromLocationName(
                        text,
                        8,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(
                                addresses: MutableList<android.location.Address>
                            ) {
                                if (!continuation.isActive) return
                                continuation.resume(
                                    addresses.mapNotNull { address ->
                                        runCatching {
                                                PlaceResult(
                                                    address.getAddressLine(0)
                                                        ?: address.featureName
                                                        ?: text,
                                                    GeoPoint(address.latitude, address.longitude),
                                                )
                                            }
                                            .getOrNull()
                                    }
                                )
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive)
                                    continuation.resumeWithException(
                                        IllegalStateException("Geocoder unavailable")
                                    )
                            }
                        },
                    )
            }
        }
    }
}
