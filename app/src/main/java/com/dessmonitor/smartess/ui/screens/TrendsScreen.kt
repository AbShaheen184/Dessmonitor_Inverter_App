package com.dessmonitor.smartess.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import org.json.JSONObject
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrendsScreen(repository: DeviceRepository = koinInject()) {
    val devices by repository.devices.observeAsState(emptyList())
    val activeDevice = devices.firstOrNull()
    var historyJson by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Persistent Settings
    val selectedSensors by repository.trendsSensors.observeAsState(setOf("PV Power", "Output Power", "Grid Power"))
    val trendsDays by repository.trendsDays.observeAsState(3)

    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(activeDevice, trendsDays) {
        if (activeDevice != null) {
            isLoading = true
            repository.getHistoryRange(activeDevice, trendsDays)
                .onSuccess { 
                    historyJson = it
                    isLoading = false
                }
                .onFailure { 
                    isLoading = false
                }
        }
    }

    val availableSensors = remember(historyJson) {
        val sensors = mutableSetOf<String>()
        val dat = historyJson?.optJSONObject("dat")
        val items = dat?.optJSONArray("data") ?: dat?.optJSONArray("detail") ?: dat?.optJSONArray("list") ?: historyJson?.optJSONArray("dat")
        
        if (items != null) {
            for (i in 0 until items.length()) {
                val t = items.getJSONObject(i).optString("title")
                if (t.isNotEmpty() && t.lowercase() != "id" && !t.contains("time", true)) {
                    sensors.add(t)
                }
            }
        }
        sensors.toList().sorted()
    }

    val colors = listOf(
        Color(0xFFFFB100), // PV - Yellow
        Color(0xFF4CAF50), // Load - Green
        Color(0xFF2196F3), // Grid - Blue
        Color(0xFFF44336), // Battery - Red
        Color.Magenta, 
        Color.Cyan,
        Color.Gray,
        Color(0xFF9C27B0),
        Color(0xFFFF9800)
    )

    fun getEntriesForSensor(sensorName: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val dat = historyJson?.optJSONObject("dat")
        val items = dat?.optJSONArray("data") ?: dat?.optJSONArray("detail") ?: dat?.optJSONArray("list") ?: historyJson?.optJSONArray("dat")
        
        if (items != null) {
            val sensorItems = mutableListOf<JSONObject>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                if (obj.optString("title").equals(sensorName, true)) {
                    sensorItems.add(obj)
                }
            }
            
            // Sort by full timestamp for multi-day view
            sensorItems.sortedBy { it.optString("ts").ifEmpty { it.optString("time") } }.forEachIndexed { index, json ->
                entries.add(Entry(index.toFloat(), json.optDouble("val", 0.0).toFloat()))
            }
        }
        return entries
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Performance Trends") })
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Day Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Range", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row {
                        listOf(1, 3, 7).forEach { day ->
                            FilterChip(
                                modifier = Modifier.padding(start = 8.dp),
                                selected = trendsDays == day,
                                onClick = { repository.setTrendsDays(day) },
                                label = { Text("${day}d") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dropdown for parameters
                Text("Select Parameters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "Choose parameters...",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableSensors.forEach { sensor ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = selectedSensors.contains(sensor),
                                            onCheckedChange = null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(sensor)
                                    }
                                },
                                onClick = {
                                    val newList = if (selectedSensors.contains(sensor)) {
                                        selectedSensors - sensor
                                    } else {
                                        selectedSensors + sensor
                                    }
                                    repository.setTrendsSensors(newList)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Selected sensors with color markers
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sortedSelected = selectedSensors.toList().sorted()
                    sortedSelected.forEachIndexed { index, sensor ->
                        val color = colors[index % colors.size]
                        
                        val latestValue = remember(historyJson, sensor) {
                            val dat = historyJson?.optJSONObject("dat")
                            val items = dat?.optJSONArray("data") ?: dat?.optJSONArray("detail") ?: dat?.optJSONArray("list") ?: historyJson?.optJSONArray("dat")
                            var last: Double? = null
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val obj = items.getJSONObject(i)
                                    if (obj.optString("title").equals(sensor, true)) {
                                        val v = obj.optDouble("val")
                                        if (!v.isNaN()) last = v
                                    }
                                }
                            }
                            last
                        }

                        Surface(
                            modifier = Modifier.padding(vertical = 4.dp),
                            shape = CircleShape,
                            color = color.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, color)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (latestValue != null) "$sensor: $latestValue" else sensor,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Delete, 
                                    null, 
                                    modifier = Modifier.size(12.dp).clickable { 
                                        repository.setTrendsSensors(selectedSensors - sensor) 
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        if (selectedSensors.isEmpty()) {
                            Text("No parameters selected", modifier = Modifier.align(Alignment.Center))
                        } else {
                            AndroidView(
                                factory = { context ->
                                    LineChart(context).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        description.isEnabled = false
                                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                                        xAxis.setDrawLabels(false)
                                        axisRight.isEnabled = false
                                        legend.isEnabled = false // Custom legend above
                                        setTouchEnabled(true)
                                        setScaleEnabled(true)
                                    }
                                },
                                update = { chart ->
                                    val sortedSelected = selectedSensors.toList().sorted()
                                    val dataSets = sortedSelected.mapIndexed { index, sensor ->
                                        LineDataSet(getEntriesForSensor(sensor), sensor).apply {
                                            color = colors[index % colors.size].toArgb()
                                            setDrawCircles(false)
                                            setDrawValues(false)
                                            lineWidth = 2f
                                            mode = LineDataSet.Mode.CUBIC_BEZIER
                                        }
                                    }
                                    chart.data = LineData(dataSets)
                                    chart.invalidate()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Comparing ${selectedSensors.size} parameters over $trendsDays days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
