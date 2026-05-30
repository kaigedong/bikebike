package com.bikebike.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bikebike.app.ble.ConnectionState
import com.bikebike.app.viewmodel.BikeViewModel
import com.bikebike.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: BikeViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    ConnectionIndicator(viewModel)
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.DirectionsBike, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_ride)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_log)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> RideTab(viewModel, modifier = Modifier.padding(padding))
            1 -> LogTab(viewModel, modifier = Modifier.padding(padding))
            2 -> SettingsTab(viewModel, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun ConnectionIndicator(viewModel: BikeViewModel) {
    val state by viewModel.connectionState.collectAsState()
    val (color, textRes) = when (state) {
        ConnectionState.DISCONNECTED -> com.bikebike.app.ui.theme.DisconnectedRed to R.string.state_disconnected
        ConnectionState.SCANNING -> com.bikebike.app.ui.theme.CadenceBlue to R.string.state_scanning
        ConnectionState.CONNECTING -> com.bikebike.app.ui.theme.ResistanceOrange to R.string.state_connecting
        ConnectionState.CONNECTED -> com.bikebike.app.ui.theme.ConnectedGreen to R.string.state_connected
    }
    AssistChip(
        onClick = {},
        label = { Text(stringResource(textRes), color = color) },
        leadingIcon = {
            Icon(Icons.Filled.Circle, contentDescription = null,
                tint = color, modifier = Modifier.size(12.dp))
        }
    )
}
