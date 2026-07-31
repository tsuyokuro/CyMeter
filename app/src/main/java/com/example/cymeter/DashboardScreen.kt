package com.example.cymeter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cymeter.ui.theme.CyMeterTheme

@Composable
fun DashboardScreen(
    viewModel: CruisingViewModel,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onResetData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cruisingData by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        cruisingData,
        isServiceRunning,
        onStartService,
        onStopService,
        onResetData,
        modifier
    )
}

@Composable
fun DashboardContent(
    cruisingData: CruisingService.CruisingState,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onResetData: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Cruising Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                StatCard(
                    title = "Current Speed",
                    value = "%.1f".format(cruisingData.currentSpeed * 3.6),
                    unit = "km/h",
                    icon = Icons.Rounded.Speed,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                StatCard(
                    title = "Avg Speed",
                    value = "%.1f".format(cruisingData.avgCruisingSpeed * 3.6),
                    unit = "km/h",
                    icon = Icons.Rounded.History,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            item {
                StatCard(
                    title = "Moving Time",
                    value = formatMovingTime(cruisingData.movingTimeMillis),
                    unit = "",
                    icon = Icons.Rounded.Timer,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            item {
                val statusColor = if (cruisingData.isMoving) Color(0xFF4CAF50) else Color(0xFFFFC107)
                StatCard(
                    title = "Status",
                    value = if (cruisingData.isMoving) "Moving" else "Stopped",
                    unit = "",
                    icon = if (cruisingData.isMoving) Icons.AutoMirrored.Rounded.DirectionsBike else Icons.Rounded.PauseCircle,
                    color = statusColor
                )
            }
            item {
                StatCard(
                    title = "Acceleration (LPF)",
                    value = "%.2f".format(cruisingData.accelerationMagnitude),
                    unit = "m/s²",
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            item {
                StatCard(
                    title = "Total Distance",
                    value = "%.2f".format(cruisingData.distanceKm),
                    unit = "km",
                    icon = Icons.Rounded.Route,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isServiceRunning) {
                Button(
                    onClick = onStartService,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Tracking")
                }
            } else {
                Button(
                    onClick = onStopService,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Tracking")
                }
            }

            OutlinedButton(
                onClick = onResetData,
                modifier = Modifier.height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reset")
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun formatMovingTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun DashboardPreview() {
    CyMeterTheme {
        DashboardContent(
            cruisingData = CruisingService.CruisingState(),
            isServiceRunning = false,
            onStartService = {},
            onStopService = {},
            onResetData = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun DashboardTabletPreview() {
    CyMeterTheme {
        DashboardContent(
            cruisingData = CruisingService.CruisingState(),
            isServiceRunning = true,
            onStartService = {},
            onStopService = {},
            onResetData = {}
        )
    }
}
