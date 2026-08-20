package com.dessmonitor.smartess.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dessmonitor.smartess.data.models.DataPoint
import com.dessmonitor.smartess.data.models.DeviceInfo
import com.dessmonitor.smartess.ui.viewmodels.DeviceDetailViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun DeviceDetailScreen(
    serialNumber: String,
    viewModel: DeviceDetailViewModel = koinViewModel()
) {
    LaunchedEffect(serialNumber) {
        viewModel.loadDevice(serialNumber)
    }
    
    val device by viewModel.device.observeAsState()
    
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "Device Details",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            device?.let { d ->
                DeviceDetailsContent(device = d)
            } ?: run {
                Text("Loading device details...")
            }
        }
    }
}

@Composable
fun DeviceDetailsContent(device: DeviceInfo) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            elevation = CardDefaults.elevatedCardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = device.getModelName(),
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Serial Number: ${device.serialNumber}")
                    if (device.devcode != null) {
                        Text(
                            text = "DevCode: ${device.devcode}",
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                
                // Display all data points
                if (device.dataPoints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sensor Readings",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    device.dataPoints.forEach { dataPoint ->
                        SensorReadingCard(dataPoint = dataPoint)
                    }
                }
            }
        }
    }
}

@Composable
fun SensorReadingCard(dataPoint: DataPoint) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = dataPoint.title,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dataPoint.title,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "${dataPoint.value} ${if (dataPoint.unit != null) dataPoint.unit else ""}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}