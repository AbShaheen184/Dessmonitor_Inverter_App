package com.dessmonitor.smartess.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InverterHomeScreen(
    repository: DeviceRepository,
    onSettingsClick: () -> Unit,
    onTrendsClick: (String) -> Unit
) {
    val devices by repository.devices.observeAsState(emptyList())
    val selectedStats by repository.selectedStats.observeAsState(emptyList())
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var showStatsDialog by remember { mutableStateOf(false) }

    // Use the first device's data for the main view
    val activeDevice = devices.firstOrNull()
    
    // Helper to find specific data point value
    fun getValue(vararg titles: String): String {
        for (title in titles) {
            val dp = activeDevice?.dataPoints?.find { it.title.trim().equals(title, ignoreCase = true) || it.title.trim().contains(title, ignoreCase = true) }
            if (dp != null) return "${dp.value} ${dp.unit ?: ""}"
        }
        return "0"
    }

    fun getNumeric(vararg titles: String): Double {
        for (title in titles) {
            val dp = activeDevice?.dataPoints?.find { it.title.trim().equals(title, ignoreCase = true) || it.title.trim().contains(title, ignoreCase = true) }
            if (dp != null) return dp.value.toString().toDoubleOrNull() ?: 0.0
        }
        return 0.0
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = activeDevice?.getDisplayName() ?: "DessMonitor",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (activeDevice != null) {
                            Text(
                                text = "SN: ${activeDevice.serialNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Settings, contentDescription = "Parameters", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    IconButton(onClick = { repository.logout() }) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (activeDevice == null) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
                    Text("Connecting to server...", modifier = Modifier.padding(top = 16.dp))
                    
                    LaunchedEffect(Unit) {
                        repository.loadDevices().onFailure { syncError = it.message }
                    }
                } else {
                    // Energy Flow Dashboard
                    val pvPower = getNumeric("PV Power", "PV1 Input Power", "Solar Power")
                    val loadPower = getNumeric("Output Power", "Load Power", "AC output active power")
                    val gridVoltage = getNumeric("Grid Voltage", "AC Output Rating Voltage")
                    val batteryCharge = getNumeric("Battery Charge Current", "Battery Charging Current")
                    val batteryDischarge = getNumeric("Battery Discharge Current", "Battery Discharging Current")
                    val workMode = getValue("Operating mode", "work state", "Inverter Mode")

                    EnergyFlowSection(
                        pvPowerValue = pvPower,
                        gridPowerValue = if (workMode.contains("Line", true)) loadPower else 0.0,
                        batteryChargeValue = batteryCharge,
                        batteryDischargeValue = batteryDischarge,
                        loadPowerValue = loadPower,
                        pvPower = "$pvPower W",
                        gridPower = if (gridVoltage > 50) "$gridVoltage V" else "0 V",
                        batterySoc = getValue("SOC", "Battery Capacity"),
                        batteryVoltage = getValue("Battery Voltage", "BMS battery voltage"),
                        loadPower = "$loadPower W",
                        workMode = workMode,
                        onNodeClick = onTrendsClick
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Detailed Statistics Title with Edit button
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Telemetry",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text("Live device data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            FilledTonalIconButton(
                                onClick = { showStatsDialog = true },
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Customize", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    
                    // Reorderable Stats Grid
                    // Since it's inside a verticalScroll Column, we can't use LazyVerticalGrid easily without fixed height.
                    // We'll stick to Row/Column and add move buttons in the "Customize" dialog for simpler reordering.
                    val statChunks = selectedStats.chunked(2)
                    statChunks.forEach { chunk ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            chunk.forEach { statTitle ->
                                StatusItem(
                                    modifier = Modifier.weight(1f),
                                    icon = when {
                                        statTitle.contains("Yield", true) || statTitle.contains("Generation", true) || statTitle.contains("Energy", true) -> Icons.Default.SolarPower
                                        statTitle.contains("Voltage", true) || statTitle.contains("Volt", true) -> Icons.Default.ElectricBolt
                                        statTitle.contains("Temp", true) -> Icons.Default.DeviceThermostat
                                        statTitle.contains("Power", true) -> Icons.Default.Bolt
                                        statTitle.contains("Current", true) || statTitle.contains("Amp", true) -> Icons.Default.ElectricMeter
                                        statTitle.contains("SOC", true) || statTitle.contains("Capacity", true) -> Icons.Default.BatteryStd
                                        else -> Icons.Default.Info
                                    },
                                    label = statTitle,
                                    value = getValue(statTitle)
                                )
                            }
                            if (chunk.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (selectedStats.isEmpty()) {
                        Text("No stats selected. Tap Customize to add.", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = {
                            isRefreshing = true
                            syncError = null
                            scope.launch {
                                repository.loadDevices().onFailure { syncError = it.message }
                                isRefreshing = false
                            }
                        },
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 2.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(if (isRefreshing) "Syncing..." else "Refresh Live Data", fontWeight = FontWeight.Bold)
                    }

                    syncError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    
                    Text(
                        "Serial: ${activeDevice.serialNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    
                    Text(
                        "Protocol: PI18 (Devcode ${activeDevice.devcode ?: "Unknown"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (showStatsDialog && activeDevice != null) {
            val allAvailableTitles = activeDevice.dataPoints.map { it.title }.distinct().sorted()
            
            AlertDialog(
                onDismissRequest = { showStatsDialog = false },
                title = { Text("Customize Tiles") },
                text = {
                    Box(modifier = Modifier.heightIn(max = 450.dp)) {
                        Column {
                            Text("Current Order (Drag to reorder functionality coming, use up/down for now)", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(8.dp))
                            
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(selectedStats.size) { index ->
                                    val title = selectedStats[index]
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        IconButton(onClick = {
                                            if (index > 0) {
                                                val newList = selectedStats.toMutableList()
                                                val item = newList.removeAt(index)
                                                newList.add(index - 1, item)
                                                repository.setSelectedStats(newList)
                                            }
                                        }, enabled = index > 0) {
                                            Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = {
                                            if (index < selectedStats.size - 1) {
                                                val newList = selectedStats.toMutableList()
                                                val item = newList.removeAt(index)
                                                newList.add(index + 1, item)
                                                repository.setSelectedStats(newList)
                                            }
                                        }, enabled = index < selectedStats.size - 1) {
                                            Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = {
                                            repository.setSelectedStats(selectedStats.filter { it != title })
                                        }) {
                                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = Color.Red)
                                        }
                                    }
                                }
                                
                                item {
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    Text("Add Parameters", style = MaterialTheme.typography.labelSmall)
                                }
                                
                                val remainingTitles = allAvailableTitles.filter { !selectedStats.contains(it) }
                                items(remainingTitles) { title ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { repository.setSelectedStats(selectedStats + title) }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(title, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStatsDialog = false }) { Text("Done") }
                }
            )
        }
    }
}

@Composable
fun EnergyFlowSection(
    pvPowerValue: Double,
    gridPowerValue: Double,
    batteryChargeValue: Double,
    batteryDischargeValue: Double,
    loadPowerValue: Double,
    pvPower: String,
    gridPower: String,
    batterySoc: String,
    batteryVoltage: String,
    loadPower: String,
    workMode: String,
    onNodeClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Animated Flow Dots
        val infiniteTransition = rememberInfiniteTransition()
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )

        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val strokeWidth = 3.dp.toPx()
            val dotRadius = 4.dp.toPx()
            val lineColor = Color.Gray.copy(alpha = 0.2f)

            // 1. PV (Top) -> Center
            val pvPos = Offset(size.width / 2, 0f)
            drawLine(lineColor, pvPos, center, strokeWidth)
            if (pvPowerValue > 10) {
                drawCircle(Color(0xFFFFB100), dotRadius, pvPos + (center - pvPos) * progress)
            }

            // 2. Grid (Left) -> Center
            val gridPos = Offset(0f, size.height / 2)
            drawLine(lineColor, gridPos, center, strokeWidth)
            if (gridPowerValue > 10) {
                drawCircle(Color(0xFF2196F3), dotRadius, gridPos + (center - gridPos) * progress)
            }

            // 3. Center -> Load (Right)
            val loadPos = Offset(size.width, size.height / 2)
            drawLine(lineColor, center, loadPos, strokeWidth)
            if (loadPowerValue > 10) {
                drawCircle(Color(0xFF4CAF50), dotRadius, center + (loadPos - center) * progress)
            }

            // 4. Battery (Bottom) <-> Center
            val battPos = Offset(size.width / 2, size.height)
            drawLine(lineColor, battPos, center, strokeWidth)
            if (batteryDischargeValue > 0.1) {
                drawCircle(Color(0xFFF44336), dotRadius, battPos + (center - battPos) * progress)
            } else if (batteryChargeValue > 0.1) {
                drawCircle(Color(0xFFF44336), dotRadius, center + (battPos - center) * progress)
            }
        }

        // 1. PV (Top)
        EnergyNode(
            modifier = Modifier.align(Alignment.TopCenter).clickable { onNodeClick("PV Power") },
            icon = Icons.Default.WbSunny,
            label = "PV",
            value = pvPower,
            color = Color(0xFFFFB100)
        )

        // 2. Grid (Left)
        EnergyNode(
            modifier = Modifier.align(Alignment.CenterStart).clickable { onNodeClick("Grid Power") },
            icon = Icons.Default.Bolt,
            label = "Grid",
            value = gridPower,
            color = Color(0xFF2196F3)
        )

        // 3. Load (Right)
        EnergyNode(
            modifier = Modifier.align(Alignment.CenterEnd).clickable { onNodeClick("Output Power") },
            icon = Icons.Default.Home,
            label = "Load",
            value = loadPower,
            color = Color(0xFF4CAF50)
        )

        // 4. Battery (Bottom)
        EnergyNode(
            modifier = Modifier.align(Alignment.BottomCenter).clickable { onNodeClick("SOC") },
            icon = Icons.Default.BatteryStd,
            label = "Battery",
            value = "$batterySoc / $batteryVoltage",
            color = Color(0xFFF44336)
        )

        // 5. Mode (Center)
        val displayMode = when {
            workMode.contains("Battery", true) -> "Battery Mode"
            workMode.contains("Line", true) || workMode.contains("Grid", true) -> "Line Mode"
            workMode.contains("PV", true) || pvPowerValue > 100 -> "PV Mode"
            workMode == "0" -> "Battery Mode"
            else -> if (workMode == "--") "Standby" else workMode
        }

        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(
                        "SYSTEM MODE", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayMode,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EnergyNode(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier.width(110.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun StatusItem(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String, subValue: String? = null) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            if (!subValue.isNullOrEmpty() && subValue != "0") {
                Text(subValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
