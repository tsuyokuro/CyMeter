package com.example.cymeter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cymeter.CruisingViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer

@Composable
fun ChartsScreen(
    viewModel: CruisingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pathPoints by viewModel.pathPoints.collectAsStateWithLifecycle()

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
            ChartsContent(
                speedModelProducer = viewModel.speedChartModelProducer,
                altitudeModelProducer = viewModel.altitudeChartModelProducer
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ChartsContent(
    speedModelProducer: CartesianChartModelProducer,
    altitudeModelProducer: CartesianChartModelProducer
) {
    val scrollState = rememberVicoScrollState()
    val zoomState = rememberVicoZoomState(initialZoom = Zoom.Content)

    val axisTitleComponent = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textSize = MaterialTheme.typography.labelSmall.fontSize
    )

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
                                    fill = LineCartesianLayer.LineFill.single(fill(MaterialTheme.colorScheme.primary))
                                ),
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(fill(MaterialTheme.colorScheme.secondary))
                                )
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            title = "Speed",
                            titleComponent = axisTitleComponent,
                            size = BaseAxis.Size.Fixed(48f)
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            title = "Distance (km)",
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
                                    fill = LineCartesianLayer.LineFill.single(fill(MaterialTheme.colorScheme.tertiary))
                                )
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            title = "Altitude",
                            titleComponent = axisTitleComponent,
                            size = BaseAxis.Size.Fixed(48f)
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            title = "Distance (km)",
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
