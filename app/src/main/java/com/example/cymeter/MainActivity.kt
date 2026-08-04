package com.example.cymeter

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.cymeter.db.AppDatabase
import com.example.cymeter.ui.theme.CyMeterTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute : NavKey

@Serializable
data object MapRoute : NavKey

class MainActivity : ComponentActivity() {

    private var cruisingService by mutableStateOf<CruisingService?>(null)
    private var isBound by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as CruisingService.LocalBinder
            cruisingService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            cruisingService = null
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyMeterTheme {
                val permissionsState = rememberMultiplePermissionsState(
                    permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )

                if (permissionsState.allPermissionsGranted) {
                    val backStack = remember { mutableStateListOf<Any>(DashboardRoute) }
                    val locationDao = remember { AppDatabase.getDatabase(applicationContext).locationDao() }

                    val onDashboardClick = dropUnlessResumed {
                        if (backStack.lastOrNull() !is DashboardRoute) {
                            backStack.clear()
                            backStack.add(DashboardRoute)
                        }
                    }

                    val onMapClick = dropUnlessResumed {
                        if (backStack.lastOrNull() !is MapRoute) {
                            backStack.clear()
                            backStack.add(MapRoute)
                        }
                    }

                    NavigationSuiteScaffold(
                        navigationSuiteItems = {
                            item(
                                selected = backStack.lastOrNull() is DashboardRoute,
                                onClick = onDashboardClick,
                                icon = { Icon(Icons.Rounded.Dashboard, contentDescription = null) },
                                label = { Text("Dashboard") }
                            )
                            item(
                                selected = backStack.lastOrNull() is MapRoute,
                                onClick = onMapClick,
                                icon = { Icon(Icons.Rounded.Map, contentDescription = null) },
                                label = { Text("Map") }
                            )
                        }
                    ) {
                        NavDisplay(
                            backStack = backStack,
                            onBack = { backStack.removeLastOrNull() },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator()
                            ),
                            entryProvider = { key ->
                                when (key) {
                                    is DashboardRoute -> NavEntry(key) {
                                        val viewModel: CruisingViewModel = viewModel {
                                            CruisingViewModel(locationDao)
                                        }

                                        LaunchedEffect(cruisingService) {
                                            cruisingService?.cruisingData?.collect { data ->
                                                viewModel.updateState(data)
                                            }
                                        }

                                        DashboardScreen(
                                            viewModel = viewModel,
                                            isServiceRunning = isBound,
                                            onStartService = { startCruisingService() },
                                            onStopService = { stopCruisingService() },
                                            onResetData = { viewModel.resetData(cruisingService) }
                                        )
                                    }
                                    is MapRoute -> NavEntry(key) {
                                        val viewModel: CruisingViewModel = viewModel {
                                            CruisingViewModel(locationDao)
                                        }

                                        LaunchedEffect(cruisingService) {
                                            cruisingService?.cruisingData?.collect { data ->
                                                viewModel.updateState(data)
                                            }
                                        }

                                        MapScreen(viewModel = viewModel)
                                    }
                                    else -> error("Unknown route $key")
                                }
                            }
                        )
                    }
                } else {
                    PermissionScreen(
                        onGrantPermissions = { permissionsState.launchMultiplePermissionRequest() }
                    )
                }
            }
        }
    }

    private fun startCruisingService() {
        val intent = Intent(this, CruisingService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun stopCruisingService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        val intent = Intent(this, CruisingService::class.java)
        stopService(intent)
        cruisingService = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier,
    onGrantPermissions: () -> Unit
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Permissions are required to track speed and acceleration.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onGrantPermissions) {
                Text("Grant Permissions")
            }
        }
    }
}
