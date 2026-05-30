package com.bikebike.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikebike.app.viewmodel.BikeViewModel
import com.bikebike.app.R

@Composable
fun LogTab(viewModel: BikeViewModel, modifier: Modifier = Modifier) {
    val logLines by viewModel.logLines.collectAsState()
    val logEnabled by viewModel.logEnabled.collectAsState()
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.ble_log),
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                if (logEnabled && logLines.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearLog() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear")
                    }
                }
            }
        }

        // Log toggle
        Row(
            modifier = Modifier.padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.enable_log),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = logEnabled,
                onCheckedChange = { viewModel.setLogEnabled(it) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (!logEnabled) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.LogoDev,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "日志已关闭",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (logLines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_log),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Auto-scroll to bottom
            LaunchedEffect(logLines.size) {
                if (logLines.isNotEmpty()) {
                    listState.animateScrollToItem(logLines.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(logLines) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        color = if (line.contains("TX ->"))
                            MaterialTheme.colorScheme.primary
                        else if (line.contains("RX <-"))
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
