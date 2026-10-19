package dev.openlocation.probe

import androidx.test.platform.app.InstrumentationRegistry
import dev.openlocation.runtime.Contract
import dev.openlocation.scenario.*
import org.junit.Assume.assumeNotNull
import org.junit.Test

/**
 * Explicit developer tool for foreground, external-app observations; excluded without -e scenario.
 */
class ScenarioControlTest {
    @Test
    fun selectScenario() {
        val name = InstrumentationRegistry.getArguments().getString("scenario")
        assumeNotNull(name)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        InstrumentationRegistry.getArguments().getString("target")?.let { pkg ->
            resolver.call(
                Contract.URI,
                "target",
                pkg,
                android.os.Bundle().apply {
                    putBoolean(
                        "enabled",
                        InstrumentationRegistry.getArguments().getString("selected") != "false",
                    )
                },
            )
        }
        resolver.call(Contract.URI, "stop", null, null)
        if (name == "stop") return
        val mode =
            when (name) {
                "drive" -> Locomotion.CAR
                "run" -> Locomotion.RUN
                else -> Locomotion.WALK
            }
        var scene =
            DemoScenes.create(mode)
                .copy(
                    environmentSegments = emptyList(),
                    routeDeviationMeters = 1.5,
                    speedVariationMetersPerSecond = if (mode == Locomotion.CAR) .5 else .12,
                    strideVariationMeters = .04,
                    armSwingVariationRadians = .04,
                )
        if (InstrumentationRegistry.getArguments().getString("route") == "sf")
            scene =
                scene.copy(
                    routeWgs84 =
                        listOf(
                            GeoPoint(37.79380, -122.39675),
                            GeoPoint(37.79223, -122.39875),
                            GeoPoint(37.79065, -122.40077),
                            GeoPoint(37.78903, -122.40285),
                            GeoPoint(37.78669, -122.40584),
                            GeoPoint(37.78343, -122.40999),
                        )
                )
        resolver.call(Contract.URI, "configure", SceneCodec.encode(scene), null)
        resolver.call(Contract.URI, "start", null, null)
    }
}
