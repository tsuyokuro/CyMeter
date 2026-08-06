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
    val cruisingData : CruisingService.CruisingState
        by viewModel.uiState.collectAsStateWithLifecycle()
    val speedThreshold by viewModel.speedThresholdKmh.collectAsStateWithLifecycle()
    
    var showSettings by remember { mutableStateOf(false) }

    DashboardContent(
        cruisingData,
        isServiceRunning,
        onStartService,
        onStopService,
        onResetData,
        onExitHistory = { viewModel.exitHistoryMode() },
        onOpenSettings = { showSettings = true },
        modifier
    )
    
    if (showSettings) {
        ThresholdSettingsDialog(
            currentThreshold = speedThreshold,
            onDismiss = { showSettings = false },
            onConfirm = { 
                viewModel.updateSpeedThreshold(it)
                showSettings = false
            }
        )
    }
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
                text = "Cruising Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
        }

        if (cruisingData.isViewingHistory) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth()
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
                        title = "Current Speed",
                        value = "%.1f".format(cruisingData.currentSpeed * 3.6),
                        unit = "km/h",
                        icon = Icons.Rounded.Speed,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
                    title = "Max Speed",
                    value = "%.1f".format(cruisingData.maxSpeed * 3.6),
                    unit = "km/h",
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    color = MaterialTheme.colorScheme.primary
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
            if (!cruisingData.isViewingHistory) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdSettingsDialog(
    currentThreshold: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var textValue by remember { mutableStateOf(currentThreshold.toString()) }
    var expanded by remember { mutableStateOf(false) }
    val presets = listOf("3.0", "5.0", "8.0", "10.0", "15.0")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speed Threshold") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Threshold to consider as moving (km/h)")
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        label = { Text("Threshold") },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true),
                        placeholder = { Text("Enter or select...") }
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset) },
                                onClick = {
                                    textValue = preset
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = textValue.toFloatOrNull() ?: currentThreshold
                    onConfirm(value)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
            onOpenSettings = {}
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
            onOpenSettings = {}
        )
    }
}
