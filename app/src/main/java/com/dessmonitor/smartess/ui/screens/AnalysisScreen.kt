package com.dessmonitor.smartess.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
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

private class ModernAnalysisMarker(private val chart: LineChart) : IMarker {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    repository: DeviceRepository = koinInject<DeviceRepository>(),
    onMenuClick: () -> Unit = {}
) {
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

    val activePaletteColors = repository.getActivePalette().map { Color(android.graphics.Color.parseColor(it)) }

    // Pre-calculate line entries and data sets off the main thread / memoized on data change
    val lineData = remember(historyJson, referenceEpoch, selectedSensors, activePaletteColors) {
        if (historyJson == null || selectedSensors.isEmpty()) return@remember null

        val dat = historyJson?.optJSONObject("dat")
        val items = dat?.optJSONArray("data") ?: dat?.optJSONArray("detail") ?: dat?.optJSONArray("list") ?: historyJson?.optJSONArray("dat")
        if (items == null) return@remember null

        val now = LocalDateTime.now()
        val dayAgo = now.minusHours(24)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val dataSets = selectedSensors.mapIndexed { index, sensor ->
            val entries = mutableListOf<Entry>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                val t = obj.optString("title").uppercase()
                
                val isMatch = when(sensor) {
                    "PV Power" -> t.contains("PV POWER") || t.contains("PV1 INPUT POWER") || t.contains("PV PRODUCTION")
                    "Output Power" -> t.contains("OUTPUT POWER") || t.contains("LOAD POWER") || t.contains("AC OUTPUT ACTIVE POWER")
                    "Discharge Current" -> t.contains("DISCHARGE CURRENT") || t.contains("DISCHARGING CURRENT")
                    else -> false
                }
                
                if (isMatch) {
                    val ts = obj.optString("ts").ifEmpty { obj.optString("time") }
                    val unit = obj.optString("unit", "")
                    try {
                        val dt = LocalDateTime.parse(ts, formatter)
                        if (dt.isAfter(dayAgo)) {
                            val epoch = dt.atZone(ZoneId.systemDefault()).toEpochSecond()
                            val floatX = (epoch - referenceEpoch) / 60f
                            entries.add(Entry(floatX, obj.optDouble("val", 0.0).toFloat(), mapOf("ts" to ts, "unit" to unit)))
                        }
                    } catch (_: Exception) {}
                }
            }

            val sensorColor = when {
                sensor.contains("PV", true) -> activePaletteColors[0].toArgb()
                sensor.contains("Output", true) || sensor.contains("Load", true) -> activePaletteColors[1].toArgb()
                sensor.contains("Grid", true) -> activePaletteColors[2].toArgb()
                sensor.contains("Discharge", true) || sensor.contains("Battery", true) -> activePaletteColors[3].toArgb()
                else -> if (activePaletteColors.size > 4) activePaletteColors[4].toArgb() else activePaletteColors[index % activePaletteColors.size].toArgb()
            }

            LineDataSet(entries.sortedBy { it.x }, sensor).apply {
                color = sensorColor
                setDrawCircles(false)
                setDrawValues(false)
                lineWidth = 1.2f 
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = android.graphics.Color.LTGRAY
                setDrawHorizontalHighlightIndicator(false)
                
                setDrawFilled(true)
                fillDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(sensorColor, android.graphics.Color.TRANSPARENT)
                ).apply { alpha = 50 }
            }
        }
        LineData(dataSets)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Analysis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("Last 24 Hours", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                                        legend.isEnabled = true
                                        legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                                        legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.RIGHT
                                        legend.orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.VERTICAL
                                        legend.setDrawInside(true)
                                        setTouchEnabled(true)
                                        setScaleEnabled(true)
                                        
                                        marker = ModernAnalysisMarker(this)
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
                                                dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                            } catch (_: Exception) { "" }
                                        }
                                    }

                                    chart.data = lineData
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
                    "Showing data for the last 24 hours (Syncing...)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }
}
