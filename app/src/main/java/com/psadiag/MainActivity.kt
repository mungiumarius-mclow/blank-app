package com.psadiag

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.psadiag.ui.screens.*
import com.psadiag.ui.theme.PSADiagTheme
import com.psadiag.ui.viewmodel.DiagViewModel

class MainActivity : ComponentActivity() {

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — UI will handle state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions if not granted
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }

        setContent {
            PSADiagTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PSADiagNavigation()
                }
            }
        }
    }
}

@Composable
fun PSADiagNavigation() {
    val navController = rememberNavController()
    val viewModel: DiagViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "bluetooth"
    ) {
        composable("bluetooth") {
            BluetoothScreen(
                viewModel = viewModel,
                onConnected = {
                    navController.navigate("dashboard") {
                        popUpTo("bluetooth") { inclusive = true }
                    }
                }
            )
        }

        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToLiveData = { navController.navigate("livedata") },
                onNavigateToDTC = { navController.navigate("dtc") },
                onNavigateToDPF = { navController.navigate("dpf") },
                onNavigateToInjectors = { navController.navigate("injectors") },
                onNavigateToECUInfo = { navController.navigate("ecuinfo") },
                onNavigateToBluetooth = {
                    navController.navigate("bluetooth") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        composable("livedata") {
            LiveDataScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("dtc") {
            DTCScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("dpf") {
            DPFScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("injectors") {
            InjectorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("ecuinfo") {
            ECUInfoScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
