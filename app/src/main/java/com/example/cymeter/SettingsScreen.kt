package com.example.cymeter

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CruisingViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speedThreshold by viewModel.speedThresholdKmh.collectAsStateWithLifecycle()
    val distanceLabelInterval by viewModel.distanceLabelIntervalKm.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = "General Tracking") {
                ThresholdSetting(
                    label = "Speed Threshold",
                    description = "Minimum speed to consider as moving (km/h)",
                    currentValue = speedThreshold,
                    presets = listOf(3.0f, 5.0f, 8.0f, 10.0f, 15.0f),
                    onValueSelected = { viewModel.updateSpeedThreshold(it) }
                )
            }

            SettingsSection(title = "Map Display") {
                ThresholdSetting(
                    label = "Distance Label Interval",
                    description = "Distance interval for route markers (km)",
                    currentValue = distanceLabelInterval,
                    presets = listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f),
                    onValueSelected = { viewModel.updateDistanceLabelInterval(it) }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
fun ThresholdSetting(
    label: String,
    description: String,
    currentValue: Float,
    presets: List<Float>,
    onValueSelected: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Box {
            OutlinedCard(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = currentValue.toString(), style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.toString()) },
                        onClick = {
                            onValueSelected(preset)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
