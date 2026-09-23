package com.dessmonitor.smartess.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dessmonitor.smartess.data.models.CustomGauge
import com.dessmonitor.smartess.data.models.GaugeChartType
import com.dessmonitor.smartess.data.models.GaugeColorMode
import com.dessmonitor.smartess.data.models.GaugeValueSource
import kotlin.math.*

fun parseHexColor(hex: String, fallback: Color = Color(0xFF2979FF)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Visualizes a single CustomGauge inside an elevated Material 3 card.
 */
@Composable
fun GaugeCard(
    gauge: CustomGauge,
    currentValue: Double,
    paletteColors: List<Color>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTrendsClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    // Resolve colors based on theme's active palette
    val effectiveColors = if (paletteColors.isEmpty()) {
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    } else paletteColors

    val activeColor = when (gauge.colorMode) {
        GaugeColorMode.PALETTE_COLOR -> {
            val idx = gauge.paletteColorIndex.coerceIn(0, effectiveColors.size - 1)
            effectiveColors[idx]
        }
        GaugeColorMode.PALETTE_GRADIENT -> effectiveColors.first()
    }

    val gradientBrush = remember(gauge, effectiveColors) {
        if (effectiveColors.size > 1) {
            Brush.sweepGradient(
                colors = listOf(
                    effectiveColors[0],
                    effectiveColors[min(1, effectiveColors.size - 1)],
                    effectiveColors[min(2, effectiveColors.size - 1)],
                    effectiveColors[min(3, effectiveColors.size - 1)],
                    effectiveColors.last()
                )
            )
        } else {
            Brush.sweepGradient(listOf(activeColor, activeColor))
        }
    }

    val fraction = remember(currentValue, gauge.minValue, gauge.maxValue) {
        val range = (gauge.maxValue - gauge.minValue).coerceAtLeast(0.001)
        ((currentValue - gauge.minValue) / range).coerceIn(0.0, 1.0).toFloat()
    }

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "gauge_progress_${gauge.id}"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title & Badges & Action Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = gauge.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (gauge.valueSource == GaugeValueSource.INVERTER_SENSOR)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (gauge.valueSource == GaugeValueSource.INVERTER_SENSOR) "Inverter" else "Manual",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                color = if (gauge.valueSource == GaugeValueSource.INVERTER_SENSOR)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = if (gauge.valueSource == GaugeValueSource.INVERTER_SENSOR) gauge.sensorTitle else "Fixed Entry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Gauge Options",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Gauge") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        if (gauge.valueSource == GaugeValueSource.INVERTER_SENSOR && onTrendsClick != null) {
                            DropdownMenuItem(
                                text = { Text("View in Trends") },
                                leadingIcon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onTrendsClick(gauge.sensorTitle)
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Rendering
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                if (gauge.chartType == GaugeChartType.HALF_PIE) {
                    HalfPieCanvas(
                        progressFraction = animatedFraction,
                        colorMode = gauge.colorMode,
                        singleColor = activeColor,
                        gradientColors = effectiveColors
                    )
                } else {
                    RadialSpeedometerCanvas(
                        progressFraction = animatedFraction,
                        colorMode = gauge.colorMode,
                        singleColor = activeColor,
                        gradientColors = effectiveColors
                    )
                }

                // Centered readout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = if (gauge.chartType == GaugeChartType.HALF_PIE) 14.dp else 18.dp)
                ) {
                    val formattedVal = if (abs(currentValue - currentValue.roundToLong()) < 0.05) {
                        currentValue.roundToLong().toString()
                    } else {
                        String.format(java.util.Locale.US, "%.1f", currentValue)
                    }
                    Text(
                        text = "$formattedVal ${gauge.unit}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = activeColor
                    )
                }
            }

            // Min and Max range indicators at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val minStr = if (gauge.minValue % 1.0 == 0.0) "${gauge.minValue.toLong()}" else "${gauge.minValue}"
                val maxStr = if (gauge.maxValue % 1.0 == 0.0) "${gauge.maxValue.toLong()}" else "${gauge.maxValue}"

                Text(
                    text = "Min: $minStr ${gauge.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
                Text(
                    text = "Max: $maxStr ${gauge.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Canvas drawing for the Half-Pie (semi-circle) arc gauge.
 */
@Composable
fun HalfPieCanvas(
    progressFraction: Float,
    colorMode: GaugeColorMode,
    singleColor: Color,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    val trackBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Canvas(modifier = modifier.size(170.dp, 120.dp)) {
        val strokeWidth = 16.dp.toPx()
        val diameter = size.width - strokeWidth
        val arcSize = Size(diameter, diameter)
        val arcTopLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

        // Semi-circle starts at 180 degrees (left) and sweeps 180 degrees (to right)
        val startAngle = 180f
        val totalSweep = 180f
        val activeSweep = (totalSweep * progressFraction).coerceAtLeast(1f)

        // 1. Draw Background Track (full 180 degree semi-circle)
        drawArc(
            color = trackBgColor,
            startAngle = startAngle,
            sweepAngle = totalSweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 2. Draw Active Progress Arc
        if (colorMode == GaugeColorMode.PALETTE_GRADIENT && gradientColors.size > 1) {
            val sweepBrush = Brush.sweepGradient(
                colors = gradientColors,
                center = Offset(size.width / 2f, size.height)
            )
            drawArc(
                brush = sweepBrush,
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        } else {
            drawArc(
                color = singleColor,
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // 3. Draw a glowing dot indicator at current position
        val currentRad = Math.toRadians((startAngle + activeSweep).toDouble())
        val radius = diameter / 2f
        val centerX = size.width / 2f
        val centerY = strokeWidth / 2f + radius
        val dotX = centerX + (radius * cos(currentRad)).toFloat()
        val dotY = centerY + (radius * sin(currentRad)).toFloat()

        drawCircle(
            color = Color.White,
            radius = strokeWidth * 0.35f,
            center = Offset(dotX, dotY)
        )
    }
}

/**
 * Canvas drawing for the Radial Gauge with Speedometer dial, tick marks, and needle.
 */
@Composable
fun RadialSpeedometerCanvas(
    progressFraction: Float,
    colorMode: GaugeColorMode,
    singleColor: Color,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    val trackBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Canvas(modifier = modifier.size(170.dp, 130.dp)) {
        val strokeWidth = 10.dp.toPx()
        val diameter = size.width - strokeWidth - 12.dp.toPx()
        val arcSize = Size(diameter, diameter)
        val arcTopLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f + 4.dp.toPx())

        // 240 degree sweep arc: from 150 degrees to 390 degrees
        val startAngle = 150f
        val totalSweep = 240f
        val activeSweep = (totalSweep * progressFraction).coerceAtLeast(1f)

        // 1. Draw outer background arc
        drawArc(
            color = trackBgColor,
            startAngle = startAngle,
            sweepAngle = totalSweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 2. Draw colored progress arc
        if (colorMode == GaugeColorMode.PALETTE_GRADIENT && gradientColors.size > 1) {
            val sweepBrush = Brush.sweepGradient(
                colors = gradientColors,
                center = Offset(size.width / 2f, arcTopLeft.y + diameter / 2f)
            )
            drawArc(
                brush = sweepBrush,
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        } else {
            drawArc(
                color = singleColor,
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        val centerX = size.width / 2f
        val centerY = arcTopLeft.y + diameter / 2f
        val outerRadius = diameter / 2f

        // 3. Draw tick marks around the perimeter
        val tickCount = 12
        for (i in 0..tickCount) {
            val tickFraction = i.toFloat() / tickCount
            val tickAngle = startAngle + (totalSweep * tickFraction)
            val rad = Math.toRadians(tickAngle.toDouble())

            val isMajor = i % 3 == 0
            val tickLen = if (isMajor) 7.dp.toPx() else 4.dp.toPx()
            val tickRadiusInner = outerRadius - strokeWidth / 2f - 4.dp.toPx()
            val tickRadiusOuter = tickRadiusInner - tickLen

            val startX = centerX + (tickRadiusInner * cos(rad)).toFloat()
            val startY = centerY + (tickRadiusInner * sin(rad)).toFloat()
            val endX = centerX + (tickRadiusOuter * cos(rad)).toFloat()
            val endY = centerY + (tickRadiusOuter * sin(rad)).toFloat()

            drawLine(
                color = tickColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 4. Draw Needle pointer pointing at the current angle
        val needleAngle = startAngle + activeSweep
        val needleRad = Math.toRadians(needleAngle.toDouble())
        val needleLength = outerRadius - 12.dp.toPx()
        val needleEndX = centerX + (needleLength * cos(needleRad)).toFloat()
        val needleEndY = centerY + (needleLength * sin(needleRad)).toFloat()

        // Needle line
        drawLine(
            color = singleColor,
            start = Offset(centerX, centerY),
            end = Offset(needleEndX, needleEndY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 5. Draw center pivot hub
        drawCircle(
            color = singleColor,
            radius = 7.dp.toPx(),
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * Dialog to create or edit a custom Gauge / Half-Pie chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditGaugeDialog(
    initialGauge: CustomGauge? = null,
    availableSensors: List<String>,
    paletteColors: List<Color>,
    onDismiss: () -> Unit,
    onSave: (CustomGauge) -> Unit
) {
    var title by remember { mutableStateOf(initialGauge?.title ?: "Solar Generation") }
    var chartType by remember { mutableStateOf(initialGauge?.chartType ?: GaugeChartType.HALF_PIE) }
    var valueSource by remember { mutableStateOf(initialGauge?.valueSource ?: GaugeValueSource.INVERTER_SENSOR) }
    var sensorTitle by remember { mutableStateOf(initialGauge?.sensorTitle ?: "PV Power") }
    var manualValueText by remember { mutableStateOf(initialGauge?.manualValue?.toString() ?: "2500") }
    var unit by remember { mutableStateOf(initialGauge?.unit ?: "W") }
    var minText by remember { mutableStateOf(initialGauge?.minValue?.toString() ?: "0") }
    var maxText by remember { mutableStateOf(initialGauge?.maxValue?.toString() ?: "5000") }
    var colorMode by remember { mutableStateOf(initialGauge?.colorMode ?: GaugeColorMode.PALETTE_GRADIENT) }
    var paletteColorIndex by remember { mutableStateOf(initialGauge?.paletteColorIndex ?: 0) }

    var expandedSensorDropdown by remember { mutableStateOf(false) }

    // Quick presets handler
    fun applyPreset(presetSensor: String, presetUnit: String, presetMin: Double, presetMax: Double) {
        sensorTitle = presetSensor
        title = when (presetSensor) {
            "PV Power", "PV1 Input Power" -> "Solar Yield"
            "Output Power", "Load Power" -> "AC Load"
            "SOC", "Battery Capacity" -> "Battery SOC"
            "Grid Voltage" -> "Grid Voltage"
            "Battery Voltage" -> "Battery Voltage"
            "Load Percentage" -> "Load Percent"
            "Battery Charge Current" -> "Charging Current"
            else -> presetSensor
        }
        unit = presetUnit
        minText = if (presetMin % 1.0 == 0.0) presetMin.toLong().toString() else presetMin.toString()
        maxText = if (presetMax % 1.0 == 0.0) presetMax.toLong().toString() else presetMax.toString()
    }

    val previewValue = if (valueSource == GaugeValueSource.MANUAL_ENTRY) {
        manualValueText.toDoubleOrNull() ?: 0.0
    } else {
        // Sample preview value at ~65% of max
        val mn = minText.toDoubleOrNull() ?: 0.0
        val mx = maxText.toDoubleOrNull() ?: 100.0
        mn + (mx - mn) * 0.65
    }

    val previewGauge = CustomGauge(
        id = initialGauge?.id ?: "preview",
        title = title.ifBlank { "Sample Gauge" },
        chartType = chartType,
        valueSource = valueSource,
        sensorTitle = sensorTitle,
        manualValue = previewValue,
        unit = unit,
        minValue = minText.toDoubleOrNull() ?: 0.0,
        maxValue = (maxText.toDoubleOrNull() ?: 100.0).coerceAtLeast((minText.toDoubleOrNull() ?: 0.0) + 0.1),
        colorMode = colorMode,
        paletteColorIndex = paletteColorIndex
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialGauge == null) "Add Custom Chart / Gauge" else "Edit Gauge",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Live Visual Preview
                Text(
                    "Live Preview (Theme Palette Colors)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))

                GaugeCard(
                    gauge = previewGauge,
                    currentValue = previewValue,
                    paletteColors = paletteColors,
                    onEdit = {},
                    onDelete = {},
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chart Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                // Chart Type Selection
                Text("Chart Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = chartType == GaugeChartType.HALF_PIE,
                        onClick = { chartType = GaugeChartType.HALF_PIE },
                        label = { Text("Half-Pie Arc") },
                        leadingIcon = { Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = chartType == GaugeChartType.RADIAL_GAUGE,
                        onClick = { chartType = GaugeChartType.RADIAL_GAUGE },
                        label = { Text("Radial Gauge") },
                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Value Source Selection
                Text("Value Source", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = valueSource == GaugeValueSource.INVERTER_SENSOR,
                        onClick = { valueSource = GaugeValueSource.INVERTER_SENSOR },
                        label = { Text("Inverter Sensor") },
                        leadingIcon = { Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = valueSource == GaugeValueSource.MANUAL_ENTRY,
                        onClick = { valueSource = GaugeValueSource.MANUAL_ENTRY },
                        label = { Text("Manual Entry") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (valueSource == GaugeValueSource.INVERTER_SENSOR) {
                    // Sensor Picker
                    ExposedDropdownMenuBox(
                        expanded = expandedSensorDropdown,
                        onExpandedChange = { expandedSensorDropdown = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = sensorTitle,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Inverter Telemetry") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSensorDropdown) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSensorDropdown,
                            onDismissRequest = { expandedSensorDropdown = false }
                        ) {
                            val sensors = (listOf(
                                "PV Power",
                                "Output Power",
                                "SOC",
                                "Grid Voltage",
                                "Battery Voltage",
                                "Load Percentage",
                                "Battery Charge Current",
                                "Battery Discharge Current"
                            ) + availableSensors).distinct()

                            sensors.forEach { sensor ->
                                DropdownMenuItem(
                                    text = { Text(sensor) },
                                    onClick = {
                                        sensorTitle = sensor
                                        expandedSensorDropdown = false
                                        // Auto-adjust unit & default max
                                        when {
                                            sensor.contains("SOC", true) || sensor.contains("Percent", true) -> {
                                                unit = "%"
                                                minText = "0"
                                                maxText = "100"
                                            }
                                            sensor.contains("Power", true) -> {
                                                unit = "W"
                                                minText = "0"
                                                maxText = "5000"
                                            }
                                            sensor.contains("Volt", true) -> {
                                                unit = "V"
                                                if (sensor.contains("Grid", true)) {
                                                    minText = "180"
                                                    maxText = "260"
                                                } else {
                                                    minText = "40"
                                                    maxText = "60"
                                                }
                                            }
                                            sensor.contains("Current", true) -> {
                                                unit = "A"
                                                minText = "0"
                                                maxText = "60"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Manual Value Entry
                    OutlinedTextField(
                        value = manualValueText,
                        onValueChange = { manualValueText = it },
                        label = { Text("Manual Value") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Min and Max Limits
                Text("Min & Max Range", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Min Value") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("Max Value") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        modifier = Modifier.width(72.dp),
                        singleLine = true
                    )
                }

                // Quick Inverter Presets
                Spacer(Modifier.height(8.dp))
                Text("Quick Inverter Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { applyPreset("PV Power", "W", 0.0, 5000.0) },
                        label = { Text("Solar 5kW", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = { applyPreset("SOC", "%", 0.0, 100.0) },
                        label = { Text("SOC 100%", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = { applyPreset("Output Power", "W", 0.0, 6000.0) },
                        label = { Text("Load 6kW", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = { applyPreset("Grid Voltage", "V", 180.0, 260.0) },
                        label = { Text("Grid 260V", fontSize = 11.sp) }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Theme Palette Color Settings
                Text("Theme Palette Colors", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Colors automatically sync with your selected theme palette in Themes settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = colorMode == GaugeColorMode.PALETTE_GRADIENT,
                        onClick = { colorMode = GaugeColorMode.PALETTE_GRADIENT },
                        label = { Text("Gradient Arc") },
                        leadingIcon = { Icon(Icons.Default.Gradient, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = colorMode == GaugeColorMode.PALETTE_COLOR,
                        onClick = { colorMode = GaugeColorMode.PALETTE_COLOR },
                        label = { Text("Single Accent") },
                        leadingIcon = { Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (colorMode == GaugeColorMode.PALETTE_COLOR) {
                    Spacer(Modifier.height(10.dp))
                    Text("Select Color from Active Palette:", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        paletteColors.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { paletteColorIndex = index },
                                contentAlignment = Alignment.Center
                            ) {
                                if (paletteColorIndex == index) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalMin = minText.toDoubleOrNull() ?: 0.0
                    val finalMax = (maxText.toDoubleOrNull() ?: 100.0).coerceAtLeast(finalMin + 0.1)
                    val finalManualVal = manualValueText.toDoubleOrNull() ?: 0.0

                    val newGauge = CustomGauge(
                        id = initialGauge?.id ?: java.util.UUID.randomUUID().toString(),
                        title = title.ifBlank { "Gauge Chart" },
                        chartType = chartType,
                        valueSource = valueSource,
                        sensorTitle = sensorTitle,
                        manualValue = finalManualVal,
                        unit = unit.ifBlank { "" },
                        minValue = finalMin,
                        maxValue = finalMax,
                        colorMode = colorMode,
                        paletteColorIndex = paletteColorIndex
                    )
                    onSave(newGauge)
                }
            ) {
                Text("Save Gauge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
