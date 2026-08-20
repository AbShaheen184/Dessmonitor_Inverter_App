package com.dessmonitor.smartess.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dessmonitor.smartess.data.models.DeviceInfo
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.IMarker
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import org.json.JSONObject
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChartTooltip(context: Context) : MarkerView(context, android.R.layout.simple_list_item_1) {
    private val tvContent: TextView = findViewById(android.R.id.text1)

    init {
        tvContent.textSize = 10f
        tvContent.setPadding(8, 4, 8, 4)
        tvContent.setBackgroundColor(android.graphics.Color.DKGRAY)
        tvContent.setTextColor(android.graphics.Color.WHITE)
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        tvContent.text = String.format(java.util.Locale.US, "%.2f", e?.y)
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(repository: DeviceRepository = koinInject<DeviceRepository>()) {
    val devices by repository.devices.observeAsState(emptyList<DeviceInfo>())
    val activeDevice = devices.firstOrNull()
    var historyJson by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Multi-select state (Persistent)
    val selectedSensors by repository.analysisSensors.observeAsState(setOf<String>("PV Power", "Output Power"))

    LaunchedEffect(activeDevice) {
        if (activeDevice != null) {
            isLoading = true
            // Fetch 24h history
            repository.getHistory24h(activeDevice)
                .onSuccess { 
                    historyJson = it
                    isLoading = false
                }
                .onFailure { 
                    isLoading = false
                }
        }
    }

    // Process available parameters for selection (filtered to exactly 3)
    val availableSensors = remember(historyJson) {
        val sensors = mutableSetOf<String>()
        val dat = historyJson?.optJSONObject("dat")
        val items = dat?.optJSONArray("data") ?: dat?.optJSONArray("detail") ?: dat?.optJSONArray("list") ?: historyJson?.optJSONArray("dat")
        
        if (items != null) {
            for (i in 0 until items.length()) {
                val t = items.getJSONObject(i).optString("title")
                if (t.isNotEmpty()) {
                    // Normalize and check against the 3 required types
                    val normalized = t.uppercase()
                    if (normalized.contains("PV POWER") || normalized.contains("PV1 INPUT POWER") || normalized.contains("PV PRODUCTION")) {
                        sensors.add("PV Power")
                    } else if (normalized.contains("OUTPUT POWER") || normalized.contains("LOAD POWER") || normalized.contains("AC OUTPUT ACTIVE POWER")) {
                        sensors.add("Output Power")
                    } else if (normalized.contains("DISCHARGE CURRENT") || normalized.contains("DISCHARGING CURRENT")) {
                        sensors.add("Discharge Current")
                    }
                }
            }
        }
        // Force the list to show even if cloud data hasn't arrived yet
        val list = sensors.toMutableList()
        if (!list.contains("PV Power")) list.add("PV Power")
        if (!list.contains("Output Power")) list.add("Output Power")
        if (!list.contains("Discharge Current")) list.add("Discharge Current")
        list.sorted()
    }

    // Helper to get entries for a specific sensor
    fun getEntriesForSensor(sensorName: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val dat = historyJson?.optJSONObject("dat")
        val items = dat?.optJSONArray("data") ?: dat?.optJSONArray("detail") ?: dat?.optJSONArray("list") ?: historyJson?.optJSONArray("dat")
        
        if (items != null) {
            val sensorItems = mutableListOf<JSONObject>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                val t = obj.optString("title").uppercase()
                
                // Match the normalized requested name back to various raw titles
                val isMatch = when(sensorName) {
                    "PV Power" -> t.contains("PV POWER") || t.contains("PV1 INPUT POWER") || t.contains("PV PRODUCTION")
                    "Output Power" -> t.contains("OUTPUT POWER") || t.contains("LOAD POWER") || t.contains("AC OUTPUT ACTIVE POWER")
                    "Discharge Current" -> t.contains("DISCHARGE CURRENT") || t.contains("DISCHARGING CURRENT")
                    else -> false
                }
                
                if (isMatch) {
                    sensorItems.add(obj)
                }
            }
            
            val now = LocalDateTime.now()
            val dayAgo = now.minusHours(24)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            
            val filtered = sensorItems.filter { 
                try {
                    val ts = it.optString("ts").ifEmpty { it.optString("time") }
                    val dt = LocalDateTime.parse(ts, formatter)
                    dt.isAfter(dayAgo)
                } catch (_: Exception) { true }
            }.sortedBy { it.optString("ts").ifEmpty { it.optString("time") } }
            
            filtered.forEachIndexed { index, json ->
                entries.add(Entry(index.toFloat(), json.optDouble("val", 0.0).toFloat()))
            }
        }
        return entries
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Analysis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("Last 24 Hours", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Compare Parameters",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableSensors) { sensor ->
                                FilterChip(
                                    selected = selectedSensors.contains(sensor),
                                    onClick = {
                                        val newList = if (selectedSensors.contains(sensor)) {
                                            selectedSensors - sensor
                                        } else {
                                            selectedSensors + sensor
                                        }
                                        repository.setAnalysisSensors(newList)
                                    },
                                    label = { Text(sensor, fontSize = 12.sp) },
                                    leadingIcon = if (selectedSensors.contains(sensor)) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val colors = listOf(
                    Color(0xFFFFB100), // PV - Yellow
                    Color(0xFF4CAF50), // Load - Green
                    Color(0xFF2196F3), // Grid - Blue
                    Color(0xFFF44336), // Battery - Red
                    Color.Magenta, 
                    Color.Cyan
                )
                
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
                                        legend.isEnabled = true
                                        legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                                        legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.RIGHT
                                        legend.orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.VERTICAL
                                        legend.setDrawInside(true)
                                        setTouchEnabled(true)
                                        setScaleEnabled(true)
                                        
                                        // Set the tooltip
                                        marker = ChartTooltip(context)
                                    }
                                },
                                update = { chart ->
                                    val dataSets = selectedSensors.mapIndexed { index, sensor ->
                                        LineDataSet(getEntriesForSensor(sensor), sensor).apply {
                                            color = colors[index % colors.size].toArgb()
                                            setDrawCircles(false)
                                            setDrawValues(false)
                                            lineWidth = 2.5f
                                            mode = LineDataSet.Mode.CUBIC_BEZIER
                                            highLightColor = Color.LightGray.toArgb()
                                            setDrawHorizontalHighlightIndicator(false)
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
                    "Showing data for the last 24 hours (Syncing...)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
