package io.github.tsuyokuro.cymeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tsuyokuro.cymeter.CruisingViewModel
import io.github.tsuyokuro.cymeter.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CruisingViewModel,
    onBack: () -> Unit,
    onExportDatabase: () -> Unit,
    onImportDatabase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speedThreshold by viewModel.speedThresholdKmh.collectAsStateWithLifecycle()
    val distanceLabelInterval by viewModel.distanceLabelIntervalKm.collectAsStateWithLifecycle()

    var showImportConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
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
            SettingsSection(
                title = stringResource(
                    R.string.settings_general_tracking_section_title
                )
            ) {
                ThresholdSetting(
                    label = stringResource(R.string.settings_speed_threshold_label),
                    description = stringResource(R.string.settings_speed_threshold_desc),
                    currentValue = speedThreshold,
                    presets = listOf(3.0f, 5.0f, 8.0f, 10.0f, 15.0f),
                    onValueSelected = { viewModel.updateSpeedThreshold(it) }
                )
            }

            SettingsSection(
                title = stringResource(
                    R.string.settings_map_display_section_title
                )
            ) {
                ThresholdSetting(
                    label = stringResource(R.string.settings_distance_label_interval_label),
                    description = stringResource(R.string.settings_distance_label_interval_desc),
                    currentValue = distanceLabelInterval,
                    presets = listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f),
                    onValueSelected = { viewModel.updateDistanceLabelInterval(it) }
                )
            }

            SettingsSection(
                title = stringResource(
                    R.string.settings_data_management_section_title
                )
            ) {
                Button(
                    onClick = onExportDatabase,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_backup_button_text))
                }

                OutlinedButton(
                    onClick = { showImportConfirmation = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_restore_button_text))
                }
            }
        }
    }

    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            title = { Text("Restore Database") },
            text = { Text("This will overwrite all current data. Are you sure you want to proceed?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmation = false
                        onImportDatabase()
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                    Text(
                        text = " Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
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
