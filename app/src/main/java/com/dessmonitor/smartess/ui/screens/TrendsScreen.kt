package com.dessmonitor.smartess.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
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
import com.github.mikephil.charting.components.IMarker
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import org.json.JSONObject
import org.koin.compose.koinInject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private class ModernTrendsMarker(private val chart: LineChart) : IMarker {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        alpha = 220
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    private var contentText = ""
    private val padding = 20f

    override fun getOffset(): MPPointF = MPPointF(0f, 0f)

    override fun getOffsetForDrawingAtPoint(posX: Float, posY: Float): MPPointF {
        val width = calculateWidth()
        val height = calculateHeight()
        return MPPointF(-width / 2f, -height - 30f)
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        val data = chart.data ?: return
        val sb = StringBuilder()
        
        val entryData = e?.data as? Map<*, *>
        val ts = entryData?.get("ts") as? String ?: ""
        sb.append("TIME: ").append(ts.ifEmpty { "N/A" }).append("\n\n")

        data.dataSets.forEach { dataSet ->
            var closestEntry: Entry? = null
            var minDiff = Float.MAX_VALUE
            for (i in 0 until dataSet.entryCount) {
                val entry = dataSet.getEntryForIndex(i)
                val diff = Math.abs(entry.x - (e?.x ?: 0f))
                if (diff < minDiff) {
                    minDiff = diff
                    closestEntry = entry
                }
            }
            if (closestEntry != null && minDiff <= 30.0f) {
                val unit = (closestEntry.data as? Map<*, *>)?.get("unit") as? String ?: ""
                sb.append("${dataSet.label}: ${String.format(Locale.US, "%.2f", closestEntry.y)} $unit\n")
            }
        }
        contentText = sb.toString().trim()
    }

    override fun draw(canvas: Canvas?, posX: Float, posY: Float) {
        if (canvas == null || contentText.isEmpty()) return
        
        val width = calculateWidth()
        val height = calculateHeight()
        val offset = getOffsetForDrawingAtPoint(posX, posY)
        
        val drawX = posX + offset.x
        val drawY = posY + offset.y
        
        val rect = RectF(drawX, drawY, drawX + width, drawY + height)
        canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
        
        val lines = contentText.split("\n")
        var currentY = drawY + padding + textPaint.textSize - 5f
        lines.forEach { line ->
            canvas.drawText(line, drawX + padding, currentY, textPaint)
            currentY += textPaint.textSize + 12f
        }
    }

    private fun calculateWidth(): Float {
        val lines = contentText.split("\n")
        return (lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f) + (padding * 2)
    }

    private fun calculateHeight(): Float {
        val lines = contentText.split("\n")
        if (lines.isEmpty()) return 0f
        return lines.size * (textPaint.textSize + 12f) + (padding * 2) - 10f
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrendsScreen(
    repository: DeviceRepository = koinInject(),
    onMenuClick: () -> Unit = {}
) {
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
        Color(android.graphics.Color.parseColor("#2979FF")), // PV - Vivid Yellow
        Color(android.graphics.Color.parseColor("#00E676")), // Load - Vivid Green
        Color(android.graphics.Color.parseColor("#FFD600")), // Grid - Vivid Blue
        Color(android.graphics.Color.parseColor("#FF1744")), // Battery - Vivid Red
        Color(0xFFD500F9), // Purple
        Color(0xFF00E5FF), // Cyan
        Color.Gray,
        Color(0xFF9C27B0),
        Color(0xFFFF9800)
    )

    val referenceEpoch = remember(historyJson) {
        val dat = historyJson?.optJSONObject("dat")
        val items = dat?.optJSONArray("data") ?: dat?.optJSONArray("detail") ?: dat?.optJSONArray("list") ?: historyJson?.optJSONArray("dat")
        var minE = Long.MAX_VALUE
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        if (items != null) {
            for (i in 0 until items.length()) {
                val ts = items.getJSONObject(i).optString("ts").ifEmpty { items.getJSONObject(i).optString("time") }
                try {
                    val epoch = LocalDateTime.parse(ts, formatter).atZone(ZoneId.systemDefault()).toEpochSecond()
                    if (epoch < minE) minE = epoch
                } catch (_: Exception) {}
            }
        }
        if (minE == Long.MAX_VALUE) 0L else minE
    }

    fun getEntriesForSensor(sensorName: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val dat = historyJson?.optJSONObject("dat")
        val items = dat?.optJSONArray("data") ?: dat?.optJSONArray("detail") ?: dat?.optJSONArray("list") ?: historyJson?.optJSONArray("dat")
        
        if (items != null) {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                if (obj.optString("title").equals(sensorName, true)) {
                    val ts = obj.optString("ts").ifEmpty { obj.optString("time") }
                    val unit = obj.optString("unit", "")
                    try {
                        val epoch = LocalDateTime.parse(ts, formatter).atZone(ZoneId.systemDefault()).toEpochSecond()
                        val floatX = (epoch - referenceEpoch) / 60f
                        entries.add(Entry(floatX, obj.optDouble("val", 0.0).toFloat(), mapOf("ts" to ts, "unit" to unit)))
                    } catch (_: Exception) {}
                }
            }
        }
        return entries.sortedBy { it.x }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Trends") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
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
                        val colorInt = when {
                            sensor.contains("PV", true) -> android.graphics.Color.parseColor("#FFD600")
                            sensor.contains("Output", true) || sensor.contains("Load", true) -> android.graphics.Color.parseColor("#00E676")
                            sensor.contains("Grid", true) -> android.graphics.Color.parseColor("#2979FF")
                            sensor.contains("Discharge", true) || sensor.contains("Battery", true) -> android.graphics.Color.parseColor("#FF1744")
                            else -> colors[index % colors.size].toArgb()
                        }
                        val color = Color(colorInt)
                        
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

                val chartAxisColor = MaterialTheme.colorScheme.onSurface.toArgb()
                val chartGridColor = MaterialTheme.colorScheme.outlineVariant.toArgb()

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
                                        setDrawGridBackground(false)
                                        
                                        // Professional styling
                                        xAxis.apply {
                                            position = XAxis.XAxisPosition.BOTTOM
                                            setDrawGridLines(true)
                                            gridColor = chartGridColor
                                            textColor = chartAxisColor
                                            axisLineColor = chartGridColor
                                            setDrawLabels(true)
                                            granularity = 1f
                                            valueFormatter = object : ValueFormatter() {
                                                override fun getFormattedValue(value: Float): String = ""
                                            }
                                        }
                                        
                                        axisLeft.apply {
                                            setDrawGridLines(true)
                                            gridColor = chartGridColor
                                            textColor = chartAxisColor
                                            axisLineColor = chartGridColor
                                        }
                                        
                                        axisRight.isEnabled = false
                                        legend.isEnabled = false // Custom legend above
                                        setTouchEnabled(true)
                                        setScaleEnabled(true)
                                        
                                        marker = ModernTrendsMarker(this)
                                    }
                                },
                                update = { chart ->
                                    chart.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    
                                    // Update formatter with correct reference point
                                    chart.xAxis.valueFormatter = object : ValueFormatter() {
                                        override fun getFormattedValue(value: Float): String {
                                            if (referenceEpoch <= 0) return ""
                                            return try {
                                                val epoch = referenceEpoch + (value * 60).toLong()
                                                val dt = LocalDateTime.ofInstant(
                                                    Instant.ofEpochSecond(epoch),
                                                    ZoneId.systemDefault()
                                                )
                                                dt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                                            } catch (_: Exception) { "" }
                                        }
                                    }

                                    val sortedSelected = selectedSensors.toList().sorted()
                                    val dataSets = sortedSelected.mapIndexed { index, sensor ->
                                        val sensorColor = when {
                                            sensor.contains("PV", true) -> android.graphics.Color.parseColor("#FFD600")
                                            sensor.contains("Output", true) || sensor.contains("Load", true) -> android.graphics.Color.parseColor("#00E676")
                                            sensor.contains("Grid", true) -> android.graphics.Color.parseColor("#2979FF")
                                            sensor.contains("Discharge", true) || sensor.contains("Battery", true) -> android.graphics.Color.parseColor("#FF1744")
                                            else -> colors[index % colors.size].toArgb()
                                        }
                                        LineDataSet(getEntriesForSensor(sensor), sensor).apply {
                                            color = sensorColor
                                            setDrawCircles(false)
                                            setDrawValues(false)
                                            lineWidth = 1.0f 
                                            mode = LineDataSet.Mode.CUBIC_BEZIER
                                            highLightColor = android.graphics.Color.LTGRAY
                                            setDrawHorizontalHighlightIndicator(false)
                                            
                                            // Modern gradient fill
                                            setDrawFilled(true)
                                            fillDrawable = GradientDrawable(
                                                GradientDrawable.Orientation.TOP_BOTTOM,
                                                intArrayOf(sensorColor, android.graphics.Color.TRANSPARENT)
                                            ).apply { alpha = 65 }
                                        }
                                    }
                                    chart.data = LineData(dataSets)
                                    chart.notifyDataSetChanged()
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

                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }
}
