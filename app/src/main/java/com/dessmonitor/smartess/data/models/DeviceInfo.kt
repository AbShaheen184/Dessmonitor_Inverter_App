package com.dessmonitor.smartess.data.models

data class DeviceInfo(
    val serialNumber: String,
    val alias: String? = null,
    val pn: String? = null,
    val pid: Long? = null,
    val devcode: Int? = null,
    val devaddr: Int? = null,
    val deviceMeta: Map<String, Any>? = null,
    val collectorMeta: Map<String, Any>? = null,
    val dataPoints: List<DataPoint> = emptyList()
) {
    // Helper function to get the model name from devcode
    fun getDisplayName(): String {
        return alias ?: getModelName()
    }

    fun getModelName(): String {
        return when (devcode) {
            2334 -> "EASUN 6.2KW Hybrid Solar Inverter"
            2361 -> "SRNE SR-EOV24-3.5K-5KWh"
            2376 -> "POW-HVM6.2K-48V-LIP"
            2428 -> "Hybrid inverter"
            2449 -> "EASUN 8/11KWA, WKS Evo MAX II 10kVA 48V"
            2451 -> "Axpert MKS IV 5600 VA"
            2452 -> "Axpert (PI18 protocol, rebranded)"
            6422 -> "Must PH19-6048 EXP"
            6515 -> "ANENJI ANJ-HHS-11KW-48V-WIFI"
            6544 -> "ANENJI ANJ-HHS-11KW-48V"
            2507 -> "ANENJI ANJ-6200 W-48PL-WIFI"
            else -> "Unknown Device (devcode $devcode)"
        }
    }
}

data class DataPoint(
    val title: String,
    val value: Any,
    val unit: String? = null,
    val id: String? = null
) {
    // Helper to get sensor type from title
    fun getSensorType(): String? {
        return when (title) {
            "Output Active Power" -> "POWER"
            "Battery Power" -> "BATTERY_POWER"
            "PV Power" -> "SOLAR_POWER"
            "Grid Power" -> "GRID_POWER"
            "State of Charge" -> "SOC"
            // Add more mappings as needed based on SENSOR_TYPES from Python code
            else -> null
        }
    }
}