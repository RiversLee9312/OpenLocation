package dev.openlocation.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.openlocation.app.R
import dev.openlocation.scenario.GeoPoint
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.*
import org.maplibre.android.maps.*
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*

data class MapFocus(val point: GeoPoint, val request: Int)

/**
 * Rendering is separate from route planning. No geolocation permission or online routing needed.
 */
@Composable
fun RouteMap(
    points: List<GeoPoint>,
    position: GeoPoint?,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    deviationMeters: Double = 0.0,
    fitToPoints: Boolean = true,
    followPosition: Boolean = false,
    controlPoints: List<GeoPoint> = emptyList(),
    selectedIndex: Int? = null,
    focus: MapFocus? = null,
    fitRequest: Int = 0,
    onVertex: (Int) -> Unit = {},
    onCenter: (GeoPoint) -> Unit = {},
    onPoint: (GeoPoint) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var failed by remember { mutableStateOf(false) }
    val latestCallback by rememberUpdatedState(onPoint)
    val latestEditable by rememberUpdatedState(editable)
    val latestVertices by rememberUpdatedState(controlPoints)
    val latestVertexCallback by rememberUpdatedState(onVertex)
    val latestCenterCallback by rememberUpdatedState(onCenter)
    val primary = MaterialTheme.colorScheme.primary
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { failed = true }
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = true
                isCompassEnabled = false
            }
            m.addOnMapClickListener { p ->
                val tap = m.projection.toScreenLocation(p)
                val radius = 28 * context.resources.displayMetrics.density
                val nearest =
                    latestVertices.indices.minByOrNull { index ->
                        val v = latestVertices[index]
                        val screen = m.projection.toScreenLocation(LatLng(v.latitude, v.longitude))
                        kotlin.math.hypot(
                            (screen.x - tap.x).toDouble(),
                            (screen.y - tap.y).toDouble(),
                        )
                    }
                val hit = nearest?.takeIf {
                    val v = latestVertices[it]
                    val screen = m.projection.toScreenLocation(LatLng(v.latitude, v.longitude))
                    kotlin.math.hypot(
                        (screen.x - tap.x).toDouble(),
                        (screen.y - tap.y).toDouble(),
                    ) <= radius
                }
                if (hit != null) {
                    latestVertexCallback(hit)
                    true
                } else if (latestEditable) {
                    latestCallback(GeoPoint(p.latitude, p.longitude))
                    true
                } else false
            }
            m.addOnCameraIdleListener {
                val target = m.cameraPosition.target
                if (target != null && target.latitude in -85.0..85.0)
                    latestCenterCallback(GeoPoint(target.latitude, target.longitude))
            }
            m.addOnCameraMoveListener {
                val target = m.cameraPosition.target
                if (target != null && target.latitude in -85.0..85.0)
                    latestCenterCallback(GeoPoint(target.latitude, target.longitude))
            }
            val json =
                """{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"attribution":"© OpenStreetMap contributors"}},"layers":[{"id":"map","type":"raster","source":"osm","paint":{"raster-saturation":-0.8,"raster-opacity":0.7}}]}"""
            m.setStyle(Style.Builder().fromJson(json)) { s ->
                s.addSource(GeoJsonSource("route"))
                s.addSource(GeoJsonSource("cursor"))
                s.addSource(GeoJsonSource("start"))
                s.addSource(GeoJsonSource("vertices"))
                s.addSource(GeoJsonSource("selection"))
                s.addLayer(
                    LineLayer("route-corridor", "route")
                        .withProperties(
                            lineColor(android.graphics.Color.rgb(85, 81, 154)),
                            lineOpacity(.14f),
                            lineCap(Property.LINE_CAP_ROUND),
                            lineJoin(Property.LINE_JOIN_ROUND),
                        )
                )
                s.addLayer(
                    LineLayer("route-stroke", "route")
                        .withProperties(
                            lineColor(android.graphics.Color.rgb(85, 81, 154)),
                            lineWidth(5f),
                            lineCap(Property.LINE_CAP_ROUND),
                            lineJoin(Property.LINE_JOIN_ROUND),
                        )
                )
                s.addLayer(
                    CircleLayer("route-start", "start")
                        .withProperties(
                            circleColor(android.graphics.Color.WHITE),
                            circleRadius(5f),
                            circleStrokeColor(android.graphics.Color.rgb(85, 81, 154)),
                            circleStrokeWidth(3f),
                        )
                )
                s.addLayer(
                    CircleLayer("route-vertices", "vertices")
                        .withProperties(
                            circleColor(android.graphics.Color.WHITE),
                            circleRadius(10f),
                            circleStrokeColor(android.graphics.Color.rgb(85, 81, 154)),
                            circleStrokeWidth(2f),
                        )
                )
                s.addLayer(
                    CircleLayer("route-selection", "selection")
                        .withProperties(
                            circleColor(android.graphics.Color.rgb(85, 81, 154)),
                            circleRadius(16f),
                            circleStrokeColor(android.graphics.Color.WHITE),
                            circleStrokeWidth(3f),
                        )
                )
                s.addLayer(
                    CircleLayer("route-cursor", "cursor")
                        .withProperties(
                            circleColor(android.graphics.Color.rgb(85, 81, 154)),
                            circleRadius(8f),
                            circleStrokeColor(android.graphics.Color.WHITE),
                            circleStrokeWidth(3f),
                        )
                )
                style = s
            }
        }
    }
    LaunchedEffect(style, points, deviationMeters, primary) {
        val metersPerPixelAtZero =
            156543.03392 * kotlin.math.cos(Math.toRadians(points.firstOrNull()?.latitude ?: 0.0))
        val widthAtZero = (2 * deviationMeters / metersPerPixelAtZero).toFloat()
        style
            ?.getLayerAs<LineLayer>("route-corridor")
            ?.setProperties(
                lineWidth(
                    Expression.interpolate(
                        Expression.exponential(2),
                        Expression.zoom(),
                        Expression.stop(0, widthAtZero),
                        Expression.stop(22, widthAtZero * 4194304),
                    )
                ),
                lineColor(
                    android.graphics.Color.rgb(
                        (primary.red * 255).toInt(),
                        (primary.green * 255).toInt(),
                        (primary.blue * 255).toInt(),
                    )
                ),
            )
    }
    LaunchedEffect(style, points) {
        style
            ?.getSourceAs<GeoJsonSource>("route")
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    if (points.size >= 2)
                        listOf(
                            Feature.fromGeometry(
                                LineString.fromLngLats(
                                    points.map { Point.fromLngLat(it.longitude, it.latitude) }
                                )
                            )
                        )
                    else emptyList()
                )
            )
        style
            ?.getSourceAs<GeoJsonSource>("start")
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    listOfNotNull(
                        points.firstOrNull()?.let {
                            Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))
                        }
                    )
                )
            )
        if (
            fitToPoints &&
                points.size >= 2 &&
                points.any {
                    kotlin.math.abs(it.latitude - points.first().latitude) +
                        kotlin.math.abs(it.longitude - points.first().longitude) > 0.0001
                }
        )
            map?.moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder()
                        .includes(points.map { LatLng(it.latitude, it.longitude) })
                        .build(),
                    48,
                )
            )
        else if (fitToPoints && points.isNotEmpty())
            map?.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(points.first().latitude, points.first().longitude),
                    16.0,
                )
            )
    }
    LaunchedEffect(style, controlPoints, selectedIndex, primary) {
        val features = controlPoints.mapIndexed { index, point ->
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                addStringProperty("number", (index + 1).toString())
            }
        }
        style
            ?.getSourceAs<GeoJsonSource>("vertices")
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
        style
            ?.getSourceAs<GeoJsonSource>("selection")
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    listOfNotNull(selectedIndex?.let { features.getOrNull(it) })
                )
            )
    }
    LaunchedEffect(style, focus) {
        focus?.let {
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(it.point.latitude, it.point.longitude),
                    map?.cameraPosition?.zoom?.coerceAtLeast(16.0) ?: 16.0,
                )
            )
        }
    }
    LaunchedEffect(style, fitRequest) {
        if (style != null && fitRequest > 0 && points.isNotEmpty()) {
            if (points.size > 1 && points.distinct().size > 1)
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder()
                            .includes(points.map { LatLng(it.latitude, it.longitude) })
                            .build(),
                        64,
                    )
                )
            else
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(points.first().latitude, points.first().longitude),
                        16.0,
                    )
                )
        }
    }
    LaunchedEffect(style, position, primary, followPosition) {
        val color =
            android.graphics.Color.argb(
                255,
                (primary.red * 255).toInt(),
                (primary.green * 255).toInt(),
                (primary.blue * 255).toInt(),
            )
        style?.getLayerAs<LineLayer>("route-stroke")?.setProperties(lineColor(color))
        style?.getLayerAs<CircleLayer>("route-cursor")?.setProperties(circleColor(color))
        style?.getLayerAs<CircleLayer>("route-start")?.setProperties(circleStrokeColor(color))
        style?.getLayerAs<CircleLayer>("route-vertices")?.setProperties(circleStrokeColor(color))
        style?.getLayerAs<CircleLayer>("route-selection")?.setProperties(circleColor(color))
        if (position == null)
            style
                ?.getSourceAs<GeoJsonSource>("cursor")
                ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
        position?.let {
            if (followPosition)
                map?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(it.latitude, it.longitude),
                        map?.cameraPosition?.zoom?.coerceAtLeast(16.0) ?: 16.0,
                    )
                )
            style
                ?.getSourceAs<GeoJsonSource>("cursor")
                ?.setGeoJson(Point.fromLngLat(it.longitude, it.latitude))
        }
    }
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (failed)
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    stringResource(R.string.map_unavailable),
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
    }
}
