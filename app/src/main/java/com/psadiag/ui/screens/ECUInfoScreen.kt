package com.psadiag.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.psadiag.ui.viewmodel.DiagViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ECUInfoScreen(
    viewModel: DiagViewModel,
    onBack: () -> Unit
) {
    val ecuId by viewModel.ecuIdentification.collectAsState()
    val selectedECU by viewModel.selectedECU.collectAsState()
    val detectedECUs by viewModel.detectedECUs.collectAsState()
    val didGroups by viewModel.didGroups.collectAsState()
    val logMessages by viewModel.logMessages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showLogs by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.identifyECU()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Informatii ECU") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Inapoi")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.identifyECU() }) {
                        Icon(Icons.Default.Refresh, "Reincarca")
                    }
                    IconButton(onClick = { showLogs = !showLogs }) {
                        Icon(
                            Icons.Default.Terminal,
                            "Log",
                            tint = if (showLogs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            if (isLoading) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // Current ECU info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "ECU Selectat: ${selectedECU?.code ?: "N/A"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        InfoRow("Part Number", ecuId.partNumber)
                        InfoRow("Calibrare", ecuId.calibration)
                        InfoRow("Hardware", ecuId.hardwareNumber)
                        InfoRow("Protocol", ecuId.protocolType)
                        selectedECU?.let {
                            InfoRow("Adresa TX", it.txAddress)
                            InfoRow("Adresa RX", it.rxAddress)
                        }
                    }
                }
            }

            // Detected ECUs
            item {
                Text(
                    "ECU-uri Detectate pe CAN Bus",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(detectedECUs) { ecu ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${ecu.code} — ${ecu.name}", style = MaterialTheme.typography.titleSmall)
                            Text("TX=${ecu.txAddress} RX=${ecu.rxAddress}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // DID Group scan button
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.scanDIDGroups() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scanare Grupuri DID")
                }
            }

            // DID Groups results
            if (didGroups.isNotEmpty()) {
                item {
                    Text(
                        "Grupuri DID",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(didGroups.filter { it.isActive }) { group ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "${group.groupPrefix}xx",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    group.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Debug logs
            if (showLogs) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Log Comunicare",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.clearLogs() }) {
                            Text("Sterge")
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = logMessages.takeLast(50).joinToString("\n"),
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
