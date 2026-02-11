package com.psadiag.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.psadiag.bluetooth.ELM327Manager
import com.psadiag.ui.viewmodel.DiagViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DiagViewModel,
    onNavigateToLiveData: () -> Unit,
    onNavigateToDTC: () -> Unit,
    onNavigateToDPF: () -> Unit,
    onNavigateToInjectors: () -> Unit,
    onNavigateToECUInfo: () -> Unit,
    onNavigateToBluetooth: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val detectedECUs by viewModel.detectedECUs.collectAsState()
    val selectedECU by viewModel.selectedECU.collectAsState()
    val connectedDevice by viewModel.connectedDeviceName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PSADiag") },
                actions = {
                    IconButton(onClick = {
                        if (connectionState == ELM327Manager.ConnectionState.READY) {
                            viewModel.disconnect()
                        }
                        onNavigateToBluetooth()
                    }) {
                        Icon(
                            imageVector = if (connectionState == ELM327Manager.ConnectionState.READY)
                                Icons.Default.BluetoothConnected
                            else
                                Icons.Default.BluetoothDisabled,
                            contentDescription = "Bluetooth"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Connection status
            item {
                ConnectionStatusCard(connectionState, connectedDevice, selectedECU)
            }

            // ECU selector
            if (detectedECUs.isNotEmpty()) {
                item {
                    Text(
                        "ECU-uri Detectate",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(detectedECUs) { ecu ->
                    ECUCard(
                        ecu = ecu,
                        isSelected = ecu.code == selectedECU?.code,
                        onClick = { viewModel.selectECU(ecu) }
                    )
                }
            }

            // Menu items
            if (connectionState == ELM327Manager.ConnectionState.READY) {
                item {
                    Text(
                        "Diagnostic",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    MenuCard(
                        title = "Date Live Motor",
                        subtitle = "RPM, Temperatura, Turbo, Viteza",
                        icon = Icons.Default.Speed,
                        onClick = onNavigateToLiveData
                    )
                }
                item {
                    MenuCard(
                        title = "Coduri Eroare (DTC)",
                        subtitle = "Citire si stergere coduri eroare",
                        icon = Icons.Default.Warning,
                        onClick = onNavigateToDTC
                    )
                }
                item {
                    MenuCard(
                        title = "Filtru Particule (DPF/FAP)",
                        subtitle = "Funingine, temperatura, regenerari",
                        icon = Icons.Default.FilterAlt,
                        onClick = onNavigateToDPF
                    )
                }
                item {
                    MenuCard(
                        title = "Injectoare",
                        subtitle = "Corectii injectoare pe cilindru",
                        icon = Icons.Default.Tune,
                        onClick = onNavigateToInjectors
                    )
                }
                item {
                    MenuCard(
                        title = "Informatii ECU",
                        subtitle = "Part number, calibrare, protocol",
                        icon = Icons.Default.Info,
                        onClick = onNavigateToECUInfo
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    state: ELM327Manager.ConnectionState,
    deviceName: String,
    selectedECU: ELM327Manager.ECUInfo?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                ELM327Manager.ConnectionState.READY -> MaterialTheme.colorScheme.secondaryContainer
                ELM327Manager.ConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
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
                imageVector = when (state) {
                    ELM327Manager.ConnectionState.READY -> Icons.Default.CheckCircle
                    ELM327Manager.ConnectionState.ERROR -> Icons.Default.Error
                    else -> Icons.Default.BluetoothDisabled
                },
                contentDescription = null,
                tint = when (state) {
                    ELM327Manager.ConnectionState.READY -> MaterialTheme.colorScheme.secondary
                    ELM327Manager.ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = when (state) {
                        ELM327Manager.ConnectionState.READY -> "Conectat"
                        ELM327Manager.ConnectionState.DISCONNECTED -> "Deconectat"
                        else -> state.name
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                if (deviceName.isNotEmpty()) {
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                selectedECU?.let {
                    Text(
                        text = "ECU: ${it.code} (${it.name})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ECUCard(
    ecu: ELM327Manager.ECUInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Memory,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(ecu.code, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${ecu.name} (TX=${ecu.txAddress} RX=${ecu.rxAddress})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
