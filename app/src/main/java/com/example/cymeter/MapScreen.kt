package com.example.cymeter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cymeter.db.LocationPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.GeoJson
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

@Composable
fun MapScreen(
    viewModel: CruisingViewModel,
    modifier: Modifier = Modifier
) {
    val pathPoints by viewModel.pathPoints.collectAsState()
    val cruisingData by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre
    remember(context) {
        MapLibre.getInstance(context)
    }

    // Create MapView
    val mapView = remember {
        MapView(context)
    }

    // Handle Lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val density = LocalDensity.current
    val locationPainter = rememberVectorPainter(Icons.Filled.LocationOn)
    val locationBitmap = remember(locationPainter, density) {
        val size = with(density) { 32.dp.toPx() }
        val bitmap = ImageBitmap(size.toInt(), size.toInt())
        val canvas = Canvas(bitmap)
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(size, size)
        ) {
            with(locationPainter) {
                draw(size = Size(size, size))
            }
        }
        bitmap.asAndroidBitmap()
    }

    val lineDataJson = remember(pathPoints) {
        if (pathPoints.size >= 2) {
            val geoPositions = pathPoints.map { Position(longitude = it.longitude, latitude = it.latitude) }
            val lineString = LineString(geoPositions)
            val feature = Feature<LineString, JsonObject?>(lineString, null)
            val collection = FeatureCollection<LineString, JsonObject?>(listOf(feature))
            GeoJson.jsonFormat.encodeToString(collection)
        } else {
            val collection = FeatureCollection<Geometry, JsonObject?>(emptyList())
            GeoJson.jsonFormat.encodeToString(collection)
        }
    }

    val markerDataJson = remember(pathPoints) {
        if (pathPoints.isNotEmpty()) {
            val lastPoint = pathPoints.last()
            val position = Position(longitude = lastPoint.longitude, latitude = lastPoint.latitude)
            val point = Point(position)
            val feature = Feature<Point, JsonObject?>(point, null)
            val collection = FeatureCollection<Point, JsonObject?>(listOf(feature))
            GeoJson.jsonFormat.encodeToString(collection)
        } else {
            val collection = FeatureCollection<Geometry, JsonObject?>(emptyList())
            GeoJson.jsonFormat.encodeToString(collection)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(pathPoints, cruisingData.isViewingHistory) {
        if (pathPoints.isNotEmpty()) {
            mapView.getMapAsync { map ->
                if (cruisingData.isViewingHistory) {
                    // Fit bounds for history
                    val bounds = LatLngBounds.Builder()
                        .includes(pathPoints.map { LatLng(it.latitude, it.longitude) })
                        .build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                } else {
                    // Follow last point for live
                    val lastPoint = pathPoints.last()
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(lastPoint.latitude, lastPoint.longitude),
                            15.0
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { _ ->
                    mapView.getMapAsync { map ->
                        val styleUri = "https://tiles.openfreemap.org/styles/liberty"
                        if (map.style?.uri != styleUri) {
                            map.setStyle(styleUri) { style ->
                                setupStyle(style, primaryColor, locationBitmap)
                                updateMapData(style, lineDataJson, markerDataJson)
                            }
                        } else {
                            map.style?.let { updateMapData(it, lineDataJson, markerDataJson) }
                        }
                    }
                }
            )

            if (cruisingData.isViewingHistory) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(16.dp).fillMaxWidth().align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Viewing History Session",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        TextButton(onClick = { viewModel.exitHistoryMode() }) {
                            Text("Back to Live")
                        }
                    }
                }
            }
        }
    }
}

private fun setupStyle(style: Style, primaryColor: Color, locationBitmap: android.graphics.Bitmap) {
    if (style.getSource("polyline-source") == null) {
        style.addSource(GeoJsonSource("polyline-source"))
        style.addLayer(
            LineLayer("polyline-layer", "polyline-source").apply {
                setProperties(
                    PropertyFactory.lineColor(primaryColor.toArgb()),
                    PropertyFactory.lineWidth(4f)
                )
            }
        )
    }

    if (style.getSource("marker-source") == null) {
        style.addImage("marker-icon", locationBitmap, true)
        style.addSource(GeoJsonSource("marker-source"))
        style.addLayer(
            SymbolLayer("marker-layer", "marker-source").apply {
                setProperties(
                    PropertyFactory.iconImage("marker-icon"),
                    PropertyFactory.iconColor(Color.Red.toArgb()),
                    PropertyFactory.iconSize(1.5f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            }
        )
    }
}

private fun updateMapData(style: Style, lineDataJson: String, markerDataJson: String) {
    val polylineSource = style.getSourceAs<GeoJsonSource>("polyline-source")
    polylineSource?.setGeoJson(lineDataJson)

    val markerSource = style.getSourceAs<GeoJsonSource>("marker-source")
    markerSource?.setGeoJson(markerDataJson)
}
