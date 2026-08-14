package com.example.cymeter

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.GeoJson
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.Locale

@Composable
fun MapScreen(
    viewModel: CruisingViewModel,
    modifier: Modifier = Modifier
) {
    val pathPoints by viewModel.pathPoints.collectAsState()
    val cruisingData by viewModel.uiState.collectAsState()
    val distanceLabelInterval by viewModel.distanceLabelIntervalKm.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAutoFollowEnabled by remember { mutableStateOf(true) }

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

    // Smart Camera Follow Listener
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    isAutoFollowEnabled = false
                }
            }
        }
    }

    val lineDataJson = remember(pathPoints) {
        if (pathPoints.size >= 2) {
            val geoPositions = pathPoints.map { Position(longitude = it.longitude, latitude = it.latitude) }
            val lineString = LineString(geoPositions)
            val feature = Feature<LineString, JsonObject?>(lineString, null)
            val collection = FeatureCollection<LineString, JsonObject?>(listOf(feature))
            GeoJson.jsonFormat.encodeToString(collection)
        } else {
            val collection = FeatureCollection<LineString, JsonObject?>(emptyList())
            GeoJson.jsonFormat.encodeToString(collection)
        }
    }

    val labelsDataJson = remember(pathPoints, distanceLabelInterval) {
        val intervalMeters = distanceLabelInterval * 1000f
        val features = mutableListOf<Feature<Point, JsonObject?>>()

        if (intervalMeters > 0) {
            var nextThreshold = intervalMeters
            for (point in pathPoints) {
                if (point.totalDistanceMeters >= nextThreshold) {
                    val distanceKm = String.format(Locale.US, "%.1f", nextThreshold / 1000f)
                    val props = buildJsonObject {
                        put("label", JsonPrimitive("${distanceKm}km"))
                    }
                    val feature = Feature(Point(Position(longitude = point.longitude, latitude = point.latitude)), props)
                    features.add(feature)
                    nextThreshold += intervalMeters
                }
            }
        }
        val collection = FeatureCollection(features)
        GeoJson.jsonFormat.encodeToString(collection)
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(pathPoints, cruisingData.isViewingHistory, isAutoFollowEnabled) {
        if (pathPoints.isNotEmpty()) {
            mapView.getMapAsync { map ->
                if (cruisingData.isViewingHistory) {
                    // Fit bounds for history
                    val bounds = LatLngBounds.Builder()
                        .includes(pathPoints.map { LatLng(it.latitude, it.longitude) })
                        .build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                } else if (isAutoFollowEnabled) {
                    // Follow last point for live
                    val lastPoint = pathPoints.last()
                    val target = LatLng(lastPoint.latitude, lastPoint.longitude)
                    val currentZoom = map.cameraPosition.zoom
                    if (currentZoom < 5.0) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0))
                    } else {
                        map.animateCamera(CameraUpdateFactory.newLatLng(target))
                    }
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
                                setupStyle(style, primaryColor)
                                enableLocationComponent(map, style, context)
                                updateMapData(style, lineDataJson, labelsDataJson)
                            }
                        } else {
                            map.style?.let { updateMapData(it, lineDataJson, labelsDataJson) }
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

            if (!isAutoFollowEnabled && !cruisingData.isViewingHistory) {
                SmallFloatingActionButton(
                    onClick = {
                        isAutoFollowEnabled = true
                        mapView.getMapAsync { map ->
                            pathPoints.lastOrNull()?.let { lastPoint ->
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(lastPoint.latitude, lastPoint.longitude),
                                        15.0
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter")
                }
            }
        }
    }
}

private fun setupStyle(style: Style, primaryColor: Color) {
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

    if (style.getSource("labels-source") == null) {
        style.addSource(GeoJsonSource("labels-source"))
        style.addLayer(
            SymbolLayer("labels-layer", "labels-source").apply {
                setProperties(
                    PropertyFactory.textField(get("label")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(16f),
                    PropertyFactory.textColor(android.graphics.Color.BLACK),
                    PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                    PropertyFactory.textHaloWidth(2f),
                    PropertyFactory.textOffset(arrayOf(0f, -1.2f)),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true)
                )
            }
        )
    }
}

private fun updateMapData(style: Style, lineDataJson: String, labelsDataJson: String) {
    val polylineSource = style.getSourceAs<GeoJsonSource>("polyline-source")
    polylineSource?.setGeoJson(lineDataJson)

    val labelsSource = style.getSourceAs<GeoJsonSource>("labels-source")
    labelsSource?.setGeoJson(labelsDataJson)
}

@SuppressLint("MissingPermission")
private fun enableLocationComponent(map: MapLibreMap, style: Style, context: android.content.Context) {
    val locationComponentOptions = LocationComponentOptions.builder(context)
        .pulseEnabled(false) // 現在位置アイコンのアニメーション
        .build()

    val locationComponent = map.locationComponent
    locationComponent.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(locationComponentOptions)
            .build()
    )
    locationComponent.isLocationComponentEnabled = true
    locationComponent.renderMode = RenderMode.NORMAL // 現在位置アイコンの方向表示
}
