package com.bikebike.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikebike.app.ble.ConnectionState
import com.bikebike.app.ble.ScannedDevice
import com.bikebike.app.ui.theme.*
import com.bikebike.app.viewmodel.BikeViewModel
import com.bikebike.app.R
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideTab(viewModel: BikeViewModel, modifier: Modifier = Modifier) {
    val connectionState by viewModel.connectionState.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val currentResistance by viewModel.currentResistance.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val identityLoaded by viewModel.identityLoaded.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Connection Section
        ConnectionSection(
            connectionState = connectionState,
            isScanning = isScanning,
            scannedDevices = scannedDevices,
            identityLoaded = identityLoaded,
            onScan = { viewModel.startScan() },
            onConnect = { viewModel.connectToDevice(it) },
            onDisconnect = { viewModel.disconnect() },
        )

        if (connectionState == ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(8.dp))
            MetricsGrid(metrics)
            Spacer(modifier = Modifier.height(8.dp))
            ResistanceControl(
                currentLevel = currentResistance,
                onLevelChange = { viewModel.setResistance(it) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectionSection(
    connectionState: ConnectionState,
    isScanning: Boolean,
    scannedDevices: List<ScannedDevice>,
    identityLoaded: Boolean,
    onScan: () -> Unit,
    onConnect: (ScannedDevice) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            stringResource(R.string.device_connection),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Identity warning
        if (!identityLoaded) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.no_handshake_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Scan / Disconnect buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onScan,
                enabled = connectionState == ConnectionState.DISCONNECTED && !isScanning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) stringResource(R.string.scanning)
                     else stringResource(R.string.scan_devices))
            }
            if (connectionState == ConnectionState.CONNECTED) {
                OutlinedButton(onClick = onDisconnect) {
                    Icon(Icons.Filled.BluetoothDisabled, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.disconnect))
                }
            }
        }

        // Device list
        if (scannedDevices.isNotEmpty() && connectionState == ConnectionState.DISCONNECTED) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.discovered_devices),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            scannedDevices.forEach { device ->
                Card(
                    onClick = { onConnect(device) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.bodyLarge)
                            Text(device.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text("${device.rssi} dBm",
                                style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricsGrid(metrics: com.bikebike.app.ble.BikeMetricsData) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.ride_data),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Row 1: Speed, Cadence, Power
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                label = stringResource(R.string.speed),
                value = String.format("%.1f", metrics.speed),
                unit = "km/h",
                color = SpeedGreen,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = stringResource(R.string.cadence),
                value = "${metrics.rpm}",
                unit = "RPM",
                color = CadenceBlue,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = stringResource(R.string.power),
                value = "${metrics.power}",
                unit = "W",
                color = PowerRed,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Resistance, Calories, Duration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                label = stringResource(R.string.resistance),
                value = "${metrics.resistance}",
                unit = "/24",
                color = ResistanceOrange,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = stringResource(R.string.calories),
                value = String.format("%.0f", metrics.calories),
                unit = "kcal",
                color = ResistanceOrange,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = stringResource(R.string.duration),
                value = formatDuration(metrics.duration),
                unit = "",
                color = CadenceBlue,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = color
            )
            if (unit.isNotEmpty()) {
                Text(unit, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
private fun ResistanceControl(
    currentLevel: Int,
    onLevelChange: (Int) -> Unit
) {
    var sliderValue by remember(currentLevel) { mutableIntStateOf(currentLevel) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.resistance_control),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.current_resistance, sliderValue),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = ResistanceOrange
        )

        Slider(
            value = sliderValue.toFloat(),
            onValueChange = { sliderValue = it.toInt() },
            onValueChangeFinished = { onLevelChange(sliderValue) },
            valueRange = 1f..24f,
            steps = 22,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(activeTrackColor = ResistanceOrange)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = {
                    if (sliderValue > 1) {
                        sliderValue--
                        onLevelChange(sliderValue)
                    }
                },
                enabled = sliderValue > 1
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null)
                Text("-1")
            }
            FilledTonalButton(onClick = { onLevelChange(sliderValue) }) {
                Text(stringResource(R.string.confirm, sliderValue))
            }
            OutlinedButton(
                onClick = {
                    if (sliderValue < 24) {
                        sliderValue++
                        onLevelChange(sliderValue)
                    }
                },
                enabled = sliderValue < 24
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("+1")
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
