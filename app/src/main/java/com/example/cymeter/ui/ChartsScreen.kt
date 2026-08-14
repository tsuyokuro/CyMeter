package com.example.cymeter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cymeter.CruisingViewModel
import com.example.cymeter.db.LocationPoint
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Place
import com.example.cymeter.formatMovingTime
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import androidx.compose.ui.text.TextStyle

@Composable
fun ChartsScreen(
    viewModel: CruisingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pathPoints by viewModel.pathPoints.collectAsStateWithLifecycle()
    val selectedPoint by viewModel.selectedLocationPoint.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Activity Charts",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (uiState.isViewingHistory) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
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

        if (pathPoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No data available. Start tracking to see speed and altitude charts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            selectedPoint?.let { point ->
                SelectedPointDetails(point = point)
                Spacer(modifier = Modifier.height(16.dp))
            }

            ChartsContent(
                speedModelProducer = viewModel.speedChartModelProducer,
                altitudeModelProducer = viewModel.altitudeChartModelProducer,
                onMarkerShown = { viewModel.selectPointByX(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

object ChartColors {
    val speedLine = Color(red=0.5f, green = 1.0f, blue=0.0f, alpha = 0.4f)
    val speedFill = Color(red=0.5f, green = 1.0f, blue=0.0f, alpha = 0.4f)
    val avgSpeedLine = Color(red=1.0f, green = 0.5f, blue=0.0f, alpha = 0.5f)
    val altitudeLine = Color(red=0.0f, green = 0.5f, blue=1.0f, alpha = 0.3f)
    val altitudeFill = Color(red=0.0f, green = 0.5f, blue=1.0f, alpha = 0.3f)
}

@Composable
fun ChartsContent(
    speedModelProducer: CartesianChartModelProducer,
    altitudeModelProducer: CartesianChartModelProducer,
    onMarkerShown: (Double) -> Unit
) {
    val scrollState = rememberVicoScrollState()
    val zoomState = rememberVicoZoomState(initialZoom = Zoom.Content)

    val markerVisibilityListener = remember(onMarkerShown) {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                targets.firstOrNull()?.let { onMarkerShown(it.x) }
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                targets.firstOrNull()?.let { onMarkerShown(it.x) }
            }
        }
    }

    val axisTitleComponent = rememberTextComponent(
        style = TextStyle(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
    )

    val marker1 = rememberMarker()
    val marker2 = rememberMarker()

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Speed Chart
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Speed (km/h)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(Fill(ChartColors.speedLine)),
                                    areaFill =  LineCartesianLayer.AreaFill.single(
                                        fill = Fill(ChartColors.speedFill)
                                    ),
                                    interpolator = LineCartesianLayer.Interpolator.catmullRom(),
                                ),
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(Fill(ChartColors.avgSpeedLine)),
                                    interpolator = LineCartesianLayer.Interpolator.catmullRom(),
                                )
                            )
                        ),
                        marker = marker1,
                        markerVisibilityListener = markerVisibilityListener,
                        startAxis = VerticalAxis.rememberStart(
                            title = { "Speed" },
                            titleComponent = axisTitleComponent,
                            size = BaseAxis.Size.Fixed(48.dp)
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            title = { "Distance (km)" },
                            titleComponent = axisTitleComponent,
                            itemPlacer = HorizontalAxis.ItemPlacer.aligned(
                                spacing = { 5000 },
                            ),
                        )
                    ),
                    modelProducer = speedModelProducer,
                    scrollState = scrollState,
                    zoomState = zoomState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )

                Legend(
                    items = listOf(
                        LegendItem("Speed", MaterialTheme.colorScheme.primary),
                        LegendItem("Avg Speed", MaterialTheme.colorScheme.secondary)
                    )
                )
            }
        }

        // Altitude Chart
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Altitude (m)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(Fill(ChartColors.altitudeLine)),
                                    areaFill =  LineCartesianLayer.AreaFill.single(
                                        fill = Fill(ChartColors.altitudeFill)
                                    ),
                                    interpolator = LineCartesianLayer.Interpolator.catmullRom(),
                                )
                            )
                        ),
                        marker = marker2,
                        markerVisibilityListener = markerVisibilityListener,
                        startAxis = VerticalAxis.rememberStart(
                            title = { "Altitude" },
                            titleComponent = axisTitleComponent,
                            size = BaseAxis.Size.Fixed(48.dp)
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            title = { "Distance (km)" },
                            titleComponent = axisTitleComponent,
                            itemPlacer = HorizontalAxis.ItemPlacer.aligned(
                                spacing = { 5000 },
                            ),
                        )
                    ),
                    modelProducer = altitudeModelProducer,
                    scrollState = scrollState,
                    zoomState = zoomState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )

                Legend(
                    items = listOf(
                        LegendItem("Altitude", MaterialTheme.colorScheme.tertiary)
                    )
                )
            }
        }
    }
}

@Composable
fun SelectedPointDetails(point: LocationPoint) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Point Details at %.2f km".format(point.totalDistanceMeters / 1000f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    DetailRow(
                        label = "Speed",
                        value = "%.1f".format(point.speed * 3.6f),
                        unit = "km/h",
                        icon = Icons.Rounded.Speed
                    )
                    DetailRow(
                        label = "Avg Speed",
                        value = "%.1f".format(point.avgSpeed * 3.6f),
                        unit = "km/h",
                        icon = Icons.Rounded.History
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    DetailRow(
                        label = "Altitude",
                        value = "%.1f".format(point.altitude),
                        unit = "m",
                        icon = Icons.Rounded.Terrain
                    )
                    DetailRow(
                        label = "Position",
                        value = "%.4f, %.4f".format(point.latitude, point.longitude),
                        unit = "",
                        icon = Icons.Rounded.Place
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Time: ${formatMovingTime(point.movingTimeMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

data class LegendItem(val label: String, val color: Color)

@Composable
fun Legend(items: List<LegendItem>) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = item.color
                ) {}
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
