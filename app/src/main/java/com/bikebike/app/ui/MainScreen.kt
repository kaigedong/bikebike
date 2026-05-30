package com.bikebike.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikebike.app.ble.ConnectionState
import com.bikebike.app.ui.theme.*
import com.bikebike.app.viewmodel.BikeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: BikeViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val currentResistance by viewModel.currentResistance.collectAsState()
    val logLines by viewModel.logLines.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var selectedResistance by remember(currentResistance) {
        mutableIntStateOf(currentResistance)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BikeBike") },
                actions = {
                    ConnectionIndicator(connectionState)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Connection / Scan section
            ConnectionSection(
                connectionState = connectionState,
                isScanning = isScanning,
                scannedDevices = scannedDevices,
                onScan = { viewModel.startScan() },
                onConnect = { viewModel.connectToDevice(it) },
                onDisconnect = { viewModel.disconnect() },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            HorizontalDivider()

            // Metrics display
            if (connectionState == ConnectionState.CONNECTED) {
                MetricsGrid(
                    speed = metrics.speed,
                    rpm = metrics.rpm,
                    power = metrics.power,
                    resistance = metrics.resistance,
                    calories = metrics.calories,
                    duration = metrics.duration,
                    distance = metrics.distance,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )

                HorizontalDivider()

                // Resistance control
                ResistanceControl(
                    currentLevel = selectedResistance,
                    onLevelChange = { level ->
                        selectedResistance = level
                        viewModel.setResistance(level)
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )

                HorizontalDivider()
            }

            // BLE Log
            LogSection(
                logLines = logLines,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }
}

@Composable
private fun ConnectionIndicator(state: ConnectionState) {
    val (color, text) = when (state) {
        ConnectionState.DISCONNECTED -> DisconnectedRed to "已断开"
        ConnectionState.SCANNING -> CadenceBlue to "扫描中"
        ConnectionState.CONNECTING -> ResistanceOrange to "连接中"
        ConnectionState.CONNECTED -> ConnectedGreen to "已连接"
    }
    AssistChip(
        onClick = {},
        label = { Text(text, color = color) },
        leadingIcon = {
            Icon(
                Icons.Filled.Circle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
        }
    )
}

@Composable
private fun ConnectionSection(
    connectionState: ConnectionState,
    isScanning: Boolean,
    scannedDevices: List<com.bikebike.app.ble.ScannedDevice>,
    onScan: () -> Unit,
    onConnect: (com.bikebike.app.ble.ScannedDevice) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("设备连接", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onScan,
                enabled = connectionState == ConnectionState.DISCONNECTED && !isScanning
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isScanning) "扫描中…" else "扫描设备")
            }

            if (connectionState == ConnectionState.CONNECTED) {
                OutlinedButton(onClick = onDisconnect) {
                    Icon(Icons.Filled.BluetoothDisabled, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("断开")
                }
            }
        }

        if (scannedDevices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("发现设备:", style = MaterialTheme.typography.bodySmall)
            scannedDevices.forEach { device ->
                TextButton(onClick = { onConnect(device) }) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${device.name} (${device.address}) RSSI: ${device.rssi}")
                }
            }
        }
    }
}

@Composable
private fun MetricsGrid(
    speed: Float,
    rpm: Int,
    power: Int,
    resistance: Int,
    calories: Float,
    duration: Int,
    distance: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("骑行数据", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricCard("速度", String.format("%.1f", speed), "km/h", SpeedGreen)
            MetricCard("踏频", "$rpm", "RPM", CadenceBlue)
            MetricCard("功率", "$power", "W", PowerRed)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricCard("档位", "$resistance", "/24", ResistanceOrange)
            MetricCard("卡路里", String.format("%.0f", calories), "kcal", ResistanceOrange)
            MetricCard("距离", "$distance", "m", CadenceBlue)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, color: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = Modifier.size(width = 110.dp, height = 100.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = color
            )
            Text(unit, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun ResistanceControl(
    currentLevel: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("档位控制", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "当前档位: $currentLevel",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = ResistanceOrange
        )

        Slider(
            value = currentLevel.toFloat(),
            onValueChange = { onLevelChange(it.toInt()) },
            valueRange = 1f..24f,
            steps = 22,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                activeTrackColor = ResistanceOrange
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = { if (currentLevel > 1) onLevelChange(currentLevel - 1) },
                enabled = currentLevel > 1
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "降低档位")
                Text("-1")
            }

            FilledTonalButton(
                onClick = { onLevelChange(currentLevel) },
            ) {
                Text("确认 $currentLevel")
            }

            OutlinedButton(
                onClick = { if (currentLevel < 24) onLevelChange(currentLevel + 1) },
                enabled = currentLevel < 24
            ) {
                Icon(Icons.Filled.Add, contentDescription = "增加档位")
                Text("+1")
            }
        }
    }
}

@Composable
private fun LogSection(
    logLines: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("BLE 日志", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(scrollState)
        ) {
            if (logLines.isEmpty()) {
                Text("暂无日志", style = MaterialTheme.typography.bodySmall)
            } else {
                logLines.takeLast(50).forEach { line ->
                    SelectionContainer {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
