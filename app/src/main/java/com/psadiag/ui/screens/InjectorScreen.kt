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
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InjectorScreen(
    viewModel: DiagViewModel,
    onBack: () -> Unit
) {
    val injectorData by viewModel.injectorData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.readInjectorData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Corectii Injectoare") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Inapoi")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.readInjectorData() }) {
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

            Text(
                "DID D482 — InjectorCorrection",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (injectorData.corrections.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "Nicio valoare citita.\nApasati Refresh pentru a citi corectiile injectoarelor.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // Individual injector cards
                injectorData.corrections.forEachIndexed { index, correction ->
                    InjectorCard(
                        cylinderNumber = index + 1,
                        correction = correction
                    )
                }

                // Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Informatii",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Corectiile normale sunt intre -2.00 si +2.00 mm³.\n" +
                                    "Valori mai mari indica uzura injectoarelor.\n" +
                                    "Diferente mari intre cilindri pot cauza vibratie la ralanti.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InjectorCard(
    cylinderNumber: Int,
    correction: Double
) {
    val absCorrection = abs(correction)
    val color = when {
        absCorrection > 3.0 -> MaterialTheme.colorScheme.error
        absCorrection > 2.0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cylinder number
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "#$cylinderNumber",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = color
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Cilindru $cylinderNumber",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    when {
                        absCorrection > 3.0 -> "Corectie excesiva — verificati injectorul"
                        absCorrection > 2.0 -> "Corectie ridicata"
                        else -> "Normal"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }

            // Value
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%+.2f".format(correction),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    "mm³",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
