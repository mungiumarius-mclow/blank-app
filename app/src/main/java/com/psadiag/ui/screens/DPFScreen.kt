package com.psadiag.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psadiag.ui.viewmodel.DiagViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DPFScreen(
    viewModel: DiagViewModel,
    onBack: () -> Unit
) {
    val dpfData by viewModel.dpfData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.readDPFData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filtru Particule (DPF/FAP)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Inapoi")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.readDPFData() }) {
                        Icon(Icons.Default.Refresh, "Reincarca")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Soot loading — main gauge
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Incarcare Funingine",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "%.2f".format(dpfData.sootLoading),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            dpfData.sootLoading > 35 -> MaterialTheme.colorScheme.error
                            dpfData.sootLoading > 25 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )
                    Text(
                        "g/l",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (dpfData.sootLoading / 45.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = when {
                            dpfData.sootLoading > 35 -> MaterialTheme.colorScheme.error
                            dpfData.sootLoading > 25 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )
                    Text(
                        "Limita regenerare: ~35 g/l",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Temperatures
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DPFValueCard(
                    modifier = Modifier.weight(1f),
                    label = "Temp. Intrare",
                    value = "%.1f".format(dpfData.tempInlet),
                    unit = "°C"
                )
                DPFValueCard(
                    modifier = Modifier.weight(1f),
                    label = "Temp. Iesire",
                    value = "%.1f".format(dpfData.tempOutlet),
                    unit = "°C"
                )
            }

            // Differential pressure
            DPFValueCard(
                modifier = Modifier.fillMaxWidth(),
                label = "Presiune Diferentiala",
                value = "%.2f".format(dpfData.differentialPressure),
                unit = "kPa"
            )

            // Regeneration info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Regenerari",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    DPFInfoRow("Numar total regenerari", "${dpfData.regenCount}")
                    DPFInfoRow("Km de la ultima regenerare", "%.0f km".format(dpfData.kmSinceRegen))
                    DPFInfoRow("Status regenerare", dpfData.regenStatus)
                }
            }
        }
    }
}

@Composable
private fun DPFValueCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DPFInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
