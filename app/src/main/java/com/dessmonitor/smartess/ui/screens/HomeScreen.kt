package com.dessmonitor.smartess.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.util.Log
import com.dessmonitor.smartess.data.models.DeviceInfo
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    repository: DeviceRepository = koinInject(),
    onDeviceClick: (String) -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    val devices by repository.devices.observeAsState(emptyList())
    
    // Load devices when screen is composed
    LaunchedEffect(Unit) {
        repository.loadDevices().onSuccess {
            isLoading = false
        }.onFailure { error ->
            Log.e("HomeScreen", "Failed to load devices", error)
            isLoading = false
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            Text(
                text = "DessMonitor SmartESS",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            if (isLoading) {
                Text("Loading device data...")
            } else {
                devices.forEach { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device.serialNumber) }
                    )
                }
            }
        }
    }
}