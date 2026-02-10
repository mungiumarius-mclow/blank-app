package com.psadiag.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.psadiag.bluetooth.ELM327Manager
import com.psadiag.ui.viewmodel.DiagViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(
    viewModel: DiagViewModel,
    onConnected: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    LaunchedEffect(Unit) {
        pairedDevices = viewModel.getPairedDevices()
    }

    // Navigate to dashboard when connected
    LaunchedEffect(connectionState) {
        if (connectionState == ELM327Manager.ConnectionState.READY) {
            onConnected()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conectare Bluetooth") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Connection status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (connectionState) {
                        ELM327Manager.ConnectionState.READY -> MaterialTheme.colorScheme.secondaryContainer
                        ELM327Manager.ConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                        ELM327Manager.ConnectionState.CONNECTING,
                        ELM327Manager.ConnectionState.INITIALIZING -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (connectionState) {
                            ELM327Manager.ConnectionState.READY -> Icons.Default.BluetoothConnected
                            ELM327Manager.ConnectionState.ERROR -> Icons.Default.BluetoothDisabled
                            ELM327Manager.ConnectionState.CONNECTING,
                            ELM327Manager.ConnectionState.INITIALIZING -> Icons.Default.BluetoothSearching
                            else -> Icons.Default.Bluetooth
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when (connectionState) {
                                ELM327Manager.ConnectionState.DISCONNECTED -> "Deconectat"
                                ELM327Manager.ConnectionState.CONNECTING -> "Se conecteaza..."
                                ELM327Manager.ConnectionState.CONNECTED -> "Conectat"
                                ELM327Manager.ConnectionState.INITIALIZING -> "Initializare ELM327..."
                                ELM327Manager.ConnectionState.READY -> "Conectat - Gata"
                                ELM327Manager.ConnectionState.ERROR -> "Eroare"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (connectionState == ELM327Manager.ConnectionState.INITIALIZING) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // Error message
            errorMessage?.let { error ->
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Dispozitive Bluetooth Asociate",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            if (pairedDevices.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "Nu exista dispozitive Bluetooth asociate.\nAsociati adaptorul ELM327 din Setari Android.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(pairedDevices) { device ->
                        BluetoothDeviceItem(
                            device = device,
                            isConnecting = isLoading,
                            onClick = {
                                viewModel.connect(device)
                            }
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun BluetoothDeviceItem(
    device: BluetoothDevice,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Dispozitiv Necunoscut",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}
