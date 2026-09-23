package com.dessmonitor.smartess.data.models

import java.io.Serializable

data class GaugeConfig(
    val title: String,
    val minVal: Double = 0.0,
    val maxVal: Double = 100.0,
    val unit: String = "%",
    val isGauge: Boolean = true
) : Serializable
