package dev.openlocation.probe

import android.location.Location
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.location.*
import com.google.android.gms.tasks.*
import dev.openlocation.runtime.Contract
import dev.openlocation.scenario.*
import java.util.concurrent.*
import org.junit.Assert.*
import org.junit.Test

/** Exercises the actual Play services SDK, including its broker attribution. */
class GmsLocationTest {
    @Test
    fun fusedContinuousCurrentAndLastFollowSelectedScene() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(
            android.content
                .Intent(context, ProbeActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val resolver = context.contentResolver
        fun call(method: String, value: String? = null) =
            resolver.call(Contract.URI, method, value, null)!!
        val scene = DemoScenes.create(Locomotion.RUN).copy(environmentSegments = emptyList())
        val client = LocationServices.getFusedLocationProviderClient(context)
        val fixes = CopyOnWriteArrayList<Location>()
        val thread = HandlerThread("gms-consumer").apply { start() }
        val callback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fixes.addAll(result.locations.map(::Location))
                }
            }
        fun verify(location: Location) {
            val distance = FloatArray(1)
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                scene.routeWgs84.first().latitude,
                scene.routeWgs84.first().longitude,
                distance,
            )
            assertTrue("GMS returned a different region", distance[0] < 600)
            assertTrue(
                "GMS returned stale data",
                kotlin.math.abs(
                    SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                ) < 10_000_000_000L,
            )
        }
        try {
            call("stop")
            call("configure", SceneCodec.encode(scene))
            call("start")
            SystemClock.sleep(800)
            val request =
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
                    .setMinUpdateIntervalMillis(200)
                    .setMaxUpdateAgeMillis(0)
                    .build()
            Tasks.await(
                client.requestLocationUpdates(request, callback, thread.looper),
                15,
                TimeUnit.SECONDS,
            )
            val deadline = SystemClock.elapsedRealtime() + 18000
            while (fixes.size < 3 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(
                250
            )
            assertTrue("GMS continuous callbacks missing", fixes.size >= 3)
            fixes.forEach(::verify)
            val cancellation = CancellationTokenSource()
            try {
                val requestCurrent =
                    CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(0)
                        .setDurationMillis(10000)
                        .build()
                val current =
                    Tasks.await(
                        client.getCurrentLocation(requestCurrent, cancellation.token),
                        12,
                        TimeUnit.SECONDS,
                    )
                assertNotNull("GMS current fix missing", current)
                verify(current!!)
            } finally {
                cancellation.cancel()
            }
            val last = Tasks.await(client.lastLocation, 5, TimeUnit.SECONDS)
            assertNotNull("GMS cached fix missing", last)
            verify(last!!)
            println(
                "GMS location SDK 21.3.0: continuous=${fixes.size}, current/last/freshness/region PASS"
            )
        } finally {
            runCatching { Tasks.await(client.removeLocationUpdates(callback), 5, TimeUnit.SECONDS) }
            call("stop")
            thread.quitSafely()
        }
    }
}
