package com.dessmonitor.smartess.data.models

enum class GaugeChartType {
    HALF_PIE,
    RADIAL_GAUGE
}

enum class GaugeValueSource {
    INVERTER_SENSOR,
    MANUAL_ENTRY
}

enum class GaugeColorMode {
    PALETTE_GRADIENT,
    PALETTE_COLOR
}

data class CustomGauge(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val chartType: GaugeChartType = GaugeChartType.HALF_PIE,
    val valueSource: GaugeValueSource = GaugeValueSource.INVERTER_SENSOR,
    val sensorTitle: String = "PV Power",
    val manualValue: Double = 0.0,
    val unit: String = "W",
    val minValue: Double = 0.0,
    val maxValue: Double = 5000.0,
    val colorMode: GaugeColorMode = GaugeColorMode.PALETTE_GRADIENT,
    val paletteColorIndex: Int = 0
)
