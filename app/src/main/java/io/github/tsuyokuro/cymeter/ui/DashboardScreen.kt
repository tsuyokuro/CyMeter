package io.github.tsuyokuro.cymeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tsuyokuro.cymeter.CruisingService
import io.github.tsuyokuro.cymeter.CruisingViewModel
import io.github.tsuyokuro.cymeter.R
import io.github.tsuyokuro.cymeter.ui.theme.CyMeterTheme

@Composable
fun DashboardScreen(
    viewModel: CruisingViewModel,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onResetData: () -> Unit,
    onViewCharts: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cruisingData : CruisingService.CruisingState
        by viewModel.uiState.collectAsStateWithLifecycle()
    
    DashboardContent(
        cruisingData,
        isServiceRunning,
        onStartService,
        onStopService,
        onResetData,
        onExitHistory = { viewModel.exitHistoryMode() },
        onOpenSettings = onOpenSettings,
        onViewCharts = onViewCharts,
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
    onExitHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onViewCharts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

//            Row {
//                IconButton(onClick = onViewCharts) {
//                    Icon(Icons.Rounded.BarChart, contentDescription = "View Charts")
//                }
//                IconButton(onClick = onOpenSettings) {
//                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
//                }
//            }
        }

        if (cruisingData.isViewingHistory) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_viewing_history_session),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    TextButton(onClick = onExitHistory) {
                        Text("Back to Live")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (!cruisingData.isViewingHistory) {
                item {
                    StatCard(
                        title = stringResource(R.string.dashboard_current_speed),
                        value = "%.1f".format(cruisingData.currentSpeed * 3.6),
                        unit = "km/h",
                        icon = Icons.Rounded.Speed,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item {
                StatCard(
                    title = stringResource(R.string.dashboard_avg_speed),
                    value = "%.1f".format(cruisingData.avgCruisingSpeed * 3.6),
                    unit = "km/h",
                    icon = Icons.Rounded.History,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            item {
                StatCard(
                    title = stringResource(R.string.dashboard_max_speed),
                    value = "%.1f".format(cruisingData.maxSpeed * 3.6),
                    unit = "km/h",
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                StatCard(
                    title = stringResource(R.string.dashboard_moving_time),
                    value = formatMovingTime(cruisingData.movingTimeMillis),
                    unit = "",
                    icon = Icons.Rounded.Timer,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (!cruisingData.isViewingHistory) {
                item {
                    val statusColor = if (cruisingData.isMoving) Color(0xFF4CAF50) else Color(0xFFFFC107)
                    StatCard(
                        title = stringResource(R.string.dashboard_status),
                        value = if (cruisingData.isMoving) "Moving" else "Stopped",
                        unit = "",
                        icon = if (cruisingData.isMoving) Icons.AutoMirrored.Rounded.DirectionsBike else Icons.Rounded.PauseCircle,
                        color = statusColor
                    )
                }
            }
            item {
                StatCard(
                    title = stringResource(R.string.dashboard_total_distance),
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
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dashboard_start_tracking))
                }
            } else {
                Button(
                    onClick = onStopService,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dashboard_stop_tracking))
                }
            }

            OutlinedButton(
                onClick = onResetData,
                modifier = Modifier.height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dashboard_reset))
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
            onResetData = {},
            onExitHistory = {},
            onOpenSettings = {},
            onViewCharts = {}
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
            onResetData = {},
            onExitHistory = {},
            onOpenSettings = {},
            onViewCharts = {}
        )
    }
}
