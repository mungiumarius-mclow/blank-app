package com.psadiag.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psadiag.ui.viewmodel.DiagViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDataScreen(
    viewModel: DiagViewModel,
    onBack: () -> Unit
) {
    val engineData by viewModel.engineData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var isLive by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLiveData()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Date Live Motor") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopLiveData()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Inapoi")
                    }
                },
                actions = {
                    // Live toggle
                    IconButton(onClick = {
                        isLive = !isLive
                        if (isLive) {
                            viewModel.startLiveData()
                        } else {
                            viewModel.stopLiveData()
                        }
                    }) {
                        Icon(
                            imageVector = if (isLive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isLive) "Opreste" else "Porneste",
                            tint = if (isLive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
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
                .padding(8.dp)
        ) {
            // Status bar
            if (isLive) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // Gauge grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GaugeCard(
                        label = "RPM",
                        value = "%.0f".format(engineData.rpm),
                        unit = "rpm",
                        color = if (engineData.rpm > 4500) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    GaugeCard(
                        label = "Viteza",
                        value = "%.0f".format(engineData.vehicleSpeed),
                        unit = "km/h",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    GaugeCard(
                        label = "Temp. Apa",
                        value = "%.1f".format(engineData.coolantTemp),
                        unit = "°C",
                        color = when {
                            engineData.coolantTemp > 100 -> MaterialTheme.colorScheme.error
                            engineData.coolantTemp > 90 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )
                }
                item {
                    GaugeCard(
                        label = "Temp. Ulei",
                        value = "%.1f".format(engineData.oilTemp),
                        unit = "°C",
                        color = when {
                            engineData.oilTemp > 120 -> MaterialTheme.colorScheme.error
                            engineData.oilTemp > 100 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )
                }
                item {
                    GaugeCard(
                        label = "Presiune Turbo",
                        value = "%.1f".format(engineData.boostPressure),
                        unit = "kPa",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    GaugeCard(
                        label = "Sarcina Motor",
                        value = "%.1f".format(engineData.engineLoad),
                        unit = "%",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    GaugeCard(
                        label = "Acceleratie",
                        value = "%.1f".format(engineData.throttlePosition),
                        unit = "%",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    GaugeCard(
                        label = "Aer Admisie",
                        value = "%.1f".format(engineData.intakeAirTemp),
                        unit = "°C",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    GaugeCard(
                        label = "Tensiune Bat.",
                        value = "%.2f".format(engineData.batteryVoltage),
                        unit = "V",
                        color = when {
                            engineData.batteryVoltage < 12.0 -> MaterialTheme.colorScheme.error
                            engineData.batteryVoltage < 12.6 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )
                }
                item {
                    GaugeCard(
                        label = "Pres. Rampa",
                        value = "%.1f".format(engineData.fuelRailPressure),
                        unit = "bar",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun GaugeCard(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
