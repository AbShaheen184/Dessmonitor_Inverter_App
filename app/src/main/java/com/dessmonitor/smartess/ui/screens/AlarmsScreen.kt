package com.dessmonitor.smartess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(
    repository: DeviceRepository,
    onMenuClick: () -> Unit = {}
) {
    val devices by repository.devices.observeAsState(emptyList())
    val activeDevice = devices.firstOrNull()
    
    // DB backed alarms
    val dbAlarms by (if (activeDevice != null) repository.getAlarmsFlow(activeDevice.serialNumber) else kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeDevice) {
        if (activeDevice != null) {
            isLoading = true
            errorMessage = null
            repository.getAlarms(activeDevice)
                .onFailure { 
                    errorMessage = it.message
                }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarm History") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading && dbAlarms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null && dbAlarms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: $errorMessage", color = MaterialTheme.colorScheme.error)
            }
        } else if (dbAlarms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No alarms recorded")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(dbAlarms, key = { it.id }) { alarm ->
                    val name = alarm.name.ifBlank { alarm.descx?.takeIf { it.isNotBlank() } ?: "Alarm Event" }
                    val status = if (alarm.status == 1) "Active" else "Cleared"
                    
                    Surface(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { 
                                Column {
                                    if (!alarm.descx.isNullOrEmpty() && alarm.descx != name) {
                                        Text(alarm.descx, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    
                                    if (alarm.ts.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Event Time: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                                            Text(alarm.ts, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }

                                    if (!alarm.cts.isNullOrBlank() && alarm.cts != alarm.ts) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Create Time: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                                            Text(alarm.cts, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                    
                                    if (!alarm.gts.isNullOrBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Clear Time: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                                            Text(alarm.gts, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = (if (status == "Active") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer).copy(alpha = 0.6f),
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = status.uppercase(),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (status == "Active") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background((if (status == "Active") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.outlineVariant).copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (status == "Active") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(110.dp)) }
            }
        }
    }
}
