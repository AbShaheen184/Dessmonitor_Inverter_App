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
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InverterHomeScreen(
    repository: DeviceRepository,
    onSettingsClick: () -> Unit,
    onTrendsClick: (String) -> Unit,
    onMenuClick: () -> Unit = {}
) {
    val devices by repository.devices.observeAsState(emptyList())
    val selectedStats by repository.selectedStats.observeAsState(emptyList())
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showAutomationDialog by remember { mutableStateOf(false) }

    // Use the first device's data for the main view
    val activeDevice = devices.firstOrNull()
    
    // Helper to find specific data point value
    fun getValue(vararg titles: String): String {
        for (title in titles) {
            val synonyms = when (title.lowercase().trim()) {
                "battery charge current", "battery charging current" -> listOf("Battery Charge Current", "Battery Charging Current", "Charge Current", "Chg Current", "Bat Charge Current")
                "battery discharge current", "battery discharging current" -> listOf("Battery Discharge Current", "Battery Discharging Current", "Discharge Current", "Dischg Current", "Bat Discharge Current")
                "output power", "load power" -> listOf("Output Power", "Load Power", "AC Output Power", "AC Output Active Power", "Out Power")
                "load percentage", "load percent", "load ratio" -> listOf("Load Percentage", "Load Percent", "Load %", "Load Ratio", "Output Load Percent")
                "pv power", "pv active power", "solar power" -> listOf("PV Power", "PV Active power", "PV1 Input Power", "Solar Power", "PV Production")
                "grid voltage", "ac voltage" -> listOf("Grid Voltage", "AC Voltage", "Grid Volt", "Line Voltage", "AC Output Rating Voltage")
                else -> listOf(title)
            }
            for (syn in synonyms) {
                val dp = activeDevice?.dataPoints?.find {
                    it.title.trim().equals(syn, ignoreCase = true) || it.title.trim().contains(syn, ignoreCase = true)
                }
                if (dp != null) return "${dp.value} ${dp.unit ?: ""}"
            }
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
    
    // Pull to Refresh State
    val pullToRefreshState = rememberPullToRefreshState()
    val lastUpdate by repository.lastUpdateTime.observeAsState(0L)
    val timeFormatter = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
    val lastUpdateText = if (lastUpdate > 0) "Last update: ${timeFormatter.format(java.util.Date(lastUpdate))}" else "Never updated"

    val pullOffset by animateDpAsState(
        targetValue = when {
            isRefreshing -> 80.dp
            pullToRefreshState.distanceFraction > 0f -> (80.dp * pullToRefreshState.distanceFraction).coerceAtMost(120.dp)
            else -> 0.dp
        },
        label = "PullOffset"
    )

    // Refresh logic moved to pullToRefresh modifier below

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
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { showAutomationDialog = true }) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoMode, contentDescription = "Automations", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
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
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullToRefresh(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            syncError = null
                            repository.loadDevices().onFailure { syncError = it.message }
                            isRefreshing = false
                        }
                    }
                )
        ) {
            // Pull to Refresh UI (Behind content, visible when pushed down)
            if (pullToRefreshState.distanceFraction > 0f || isRefreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pullOffset)
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    } else {
                        val rotation by animateFloatAsState(if (pullToRefreshState.distanceFraction >= 1f) 180f else 0f)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(28.dp)
                                    .rotate(rotation),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (pullToRefreshState.distanceFraction >= 1f) "Release to refresh" else "Pull down to refresh",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = lastUpdateText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = pullOffset)
                    .padding(horizontal = 16.dp)
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

                Spacer(modifier = Modifier.height(110.dp))
            }
        }
        }

        if (showStatsDialog && activeDevice != null) {
            val defaultTitles = listOf(
                "Battery Charge Current",
                "Battery Discharge Current",
                "Output Power",
                "Load Percentage",
                "PV Power",
                "Grid Voltage"
            )
            val deviceTitles = activeDevice.dataPoints.map { it.title }.distinct()
            val allAvailableTitles = (defaultTitles + deviceTitles).distinct().sorted()
            
            AlertDialog(
                onDismissRequest = { showStatsDialog = false },
                title = { Text("Customize Tiles") },
                text = {
                    Box(modifier = Modifier.heightIn(max = 450.dp)) {
                        Column {
                            Text("Current Order", style = MaterialTheme.typography.labelSmall)
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

        if (showAutomationDialog) {
            AutomationsDialog(
                repository = repository,
                activeDevice = activeDevice,
                onDismiss = { showAutomationDialog = false }
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
            .height(500.dp)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "EnergyFlow")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 100f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "FlowPhase"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val strokeWidth = 2.dp.toPx()
            val dashLength = 6.dp.toPx()
            val gapLength = 6.dp.toPx()
            val baseLineColor = Color.Gray.copy(alpha = 0.15f)
            val nodeDist = 130.dp.toPx()
            val centerRadius = 65.dp.toPx()
            val nodeRadius = 38.dp.toPx()

            fun drawFlowLine(start: Offset, end: Offset, color: Color, isActive: Boolean, reverse: Boolean = false) {
                drawLine(baseLineColor, start, end, strokeWidth)
                if (isActive) {
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        lineTo(end.x, end.y)
                    }
                    val effect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(dashLength, gapLength),
                        phase = if (reverse) phase * 2 else -phase * 2
                    )
                    drawPath(path = path, color = color.copy(alpha = 0.6f), style = Stroke(width = strokeWidth * 1.5f, pathEffect = effect))
                    drawPath(path = path, color = color.copy(alpha = 0.2f), style = Stroke(width = strokeWidth * 4f, pathEffect = effect))
                }
            }

            drawFlowLine(center + Offset(0f, -nodeDist + nodeRadius), center + Offset(0f, -centerRadius), Color(0xFFFFB100), pvPowerValue > 10)
            drawFlowLine(center + Offset(-nodeDist + nodeRadius, 0f), center + Offset(-centerRadius, 0f), Color(0xFF2196F3), gridPowerValue > 10)
            drawFlowLine(center + Offset(centerRadius, 0f), center + Offset(nodeDist - nodeRadius, 0f), Color(0xFF4CAF50), loadPowerValue > 10)

            val battStart = center + Offset(0f, centerRadius)
            val battEnd = center + Offset(0f, nodeDist - nodeRadius)
            if (batteryDischargeValue > 0.1) drawFlowLine(battEnd, battStart, Color(0xFFF44336), true)
            else if (batteryChargeValue > 0.1) drawFlowLine(battStart, battEnd, Color(0xFFF44336), true)
            else drawLine(baseLineColor, battStart, battEnd, strokeWidth)
        }

        val nodeDist = 130.dp
        EnergyNode(modifier = Modifier.align(Alignment.Center).offset(y = -nodeDist).clickable { onNodeClick("PV Power") }, icon = Icons.Default.WbSunny, label = "PV", value = pvPower, color = Color(0xFFFFB100), isFlowing = pvPowerValue > 10, labelPosition = LabelPosition.TOP)
        EnergyNode(modifier = Modifier.align(Alignment.Center).offset(x = -nodeDist).clickable { onNodeClick("Grid Power") }, icon = Icons.Default.ElectricBolt, label = "Grid", value = gridPower, color = Color(0xFF2196F3), isFlowing = gridPowerValue > 10, labelPosition = LabelPosition.BOTTOM)
        EnergyNode(modifier = Modifier.align(Alignment.Center).offset(x = nodeDist).clickable { onNodeClick("Output Power") }, icon = Icons.Default.Home, label = "Load", value = loadPower, color = Color(0xFF4CAF50), isFlowing = loadPowerValue > 10, labelPosition = LabelPosition.BOTTOM)
        EnergyNode(modifier = Modifier.align(Alignment.Center).offset(y = nodeDist).clickable { onNodeClick("SOC") }, icon = Icons.Default.BatteryStd, label = "Battery", value = "$batterySoc / $batteryVoltage", color = Color(0xFFF44336), isFlowing = batteryChargeValue > 0.1 || batteryDischargeValue > 0.1, labelPosition = LabelPosition.BOTTOM)

        val displayMode = when {
            workMode.contains("Battery", true) -> "Battery Mode"
            workMode.contains("Line", true) || workMode.contains("Grid", true) -> "Line Mode"
            workMode.contains("PV", true) || pvPowerValue > 100 -> "PV Mode"
            workMode == "0" -> "Battery Mode"
            else -> if (workMode == "--") "Standby" else workMode
        }

        Surface(
            modifier = Modifier.size(130.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(2.dp, Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)))
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val centerRotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(animation = tween(10000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "CenterRotation")
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    rotate(centerRotation) {
                        drawCircle(brush = Brush.sweepGradient(0f to Color.Transparent, 0.5f to primaryColor.copy(alpha = 0.2f), 1f to Color.Transparent), radius = size.width / 2 - 4.dp.toPx(), style = Stroke(width = 2.dp.toPx()), alpha = 0.5f)
                    }
                }
                Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                    Text("SYSTEM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(text = displayMode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp)
                }
            }
        }
    }
}

enum class LabelPosition { TOP, BOTTOM, LEFT, RIGHT }

@Composable
fun EnergyNode(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    isFlowing: Boolean = false,
    labelPosition: LabelPosition = LabelPosition.BOTTOM
) {
    val infiniteTransition = rememberInfiniteTransition(label = "NodeAnim")
    val rotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "RingRotation")
    val scale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.05f, animationSpec = infiniteRepeatable(animation = tween(1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "PulseScale")

    Box(modifier = modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            if (isFlowing) {
                Canvas(modifier = Modifier.size(76.dp)) {
                    drawCircle(color = color.copy(alpha = 0.3f), radius = (size.width / 2) * scale, style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), rotation * 2)))
                }
            }
            Surface(modifier = Modifier.size(68.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.5f))) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
                }
            }
        }
        val labelModifier = when(labelPosition) {
            LabelPosition.TOP -> Modifier.align(Alignment.TopCenter).padding(top = 0.dp)
            LabelPosition.BOTTOM -> Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp)
            LabelPosition.LEFT -> Modifier.align(Alignment.CenterStart).padding(start = 0.dp)
            LabelPosition.RIGHT -> Modifier.align(Alignment.CenterEnd).padding(end = 0.dp)
        }
        Column(modifier = labelModifier.width(115.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun StatusItem(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String, subValue: String? = null) {
    Card(modifier = modifier, shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun EnergyFlowModernPreview() {
    MaterialTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(20.dp)) {
            EnergyFlowSection(pvPowerValue = 500.0, gridPowerValue = 0.0, batteryChargeValue = 200.0, batteryDischargeValue = 0.0, loadPowerValue = 300.0, pvPower = "500 W", gridPower = "230 V", batterySoc = "85%", batteryVoltage = "52.4 V", loadPower = "300 W", workMode = "PV Mode", onNodeClick = {})
        }
    }
}
