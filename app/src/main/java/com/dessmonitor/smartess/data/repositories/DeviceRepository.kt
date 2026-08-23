package com.dessmonitor.smartess.data.repositories

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dessmonitor.smartess.data.api.DessMonitorAPI
import com.dessmonitor.smartess.data.db.AlarmDao
import com.dessmonitor.smartess.data.db.AlarmEntity
import com.dessmonitor.smartess.data.models.DataPoint
import com.dessmonitor.smartess.data.models.DeviceInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.json.JSONObject

import kotlin.time.Duration.Companion.minutes

class DeviceRepository(private val context: Context, private val alarmDao: AlarmDao) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smartess_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    val api = DessMonitorAPI()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _devices = MutableLiveData<List<DeviceInfo>>(emptyList())
    val devices: LiveData<List<DeviceInfo>> = _devices

    private val _lastUpdateTime = MutableLiveData<Long>(0L)
    val lastUpdateTime: LiveData<Long> = _lastUpdateTime

    // Theme Settings
    private val _useSystemTheme = MutableLiveData<Boolean>(prefs.getBoolean("use_system_theme", true))
    val useSystemTheme: LiveData<Boolean> = _useSystemTheme

    private val _isDarkModeManual = MutableLiveData<Boolean>(prefs.getBoolean("is_dark_mode_manual", false))
    val isDarkModeManual: LiveData<Boolean> = _isDarkModeManual

    // Graph Palettes
    val predefinedPalettes = listOf(
        listOf("#FFD600", "#00E676", "#2979FF", "#FF1744", "#D500F9"), // Vivid
        listOf("#00ACC1", "#00796B", "#0288D1", "#3949AB", "#5E35B1"), // Ocean
        listOf("#43A047", "#7CB342", "#C0CA33", "#FDD835", "#FB8C00"), // Nature
        listOf("#F48FB1", "#CE93D8", "#90CAF9", "#80CBC4", "#FFF59D"), // Pastel
        listOf("#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3")  // Retro
    )

    private val _selectedPaletteIndex = MutableLiveData<Int>(prefs.getInt("selected_palette_index", 0))
    val selectedPaletteIndex: LiveData<Int> = _selectedPaletteIndex

    private val _customPalette = MutableLiveData<List<String>>(
        gson.fromJson(prefs.getString("custom_palette", null), object : TypeToken<List<String>>() {}.type) 
        ?: listOf("#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF")
    )
    val customPalette: LiveData<List<String>> = _customPalette

    private val _isLoggedIn = MutableLiveData<Boolean>(false)
    val isLoggedIn: LiveData<Boolean> = _isLoggedIn

    private val _selectedDate = MutableLiveData<java.time.LocalDate>(java.time.LocalDate.now())
    val selectedDate: LiveData<java.time.LocalDate> = _selectedDate

    private val _selectedStats = MutableLiveData<List<String>>(listOf(
        "Battery Charge Current",
        "Battery Discharge Current",
        "Output Power",
        "Load Percentage",
        "PV Power",
        "Grid Voltage"
    ))
    val selectedStats: LiveData<List<String>> = _selectedStats

    // Persistent Chart Settings
    private val _analysisSensors = MutableLiveData<Set<String>>(setOf("PV Power", "Output Power"))
    val analysisSensors: LiveData<Set<String>> = _analysisSensors

    private val _trendsSensors = MutableLiveData<Set<String>>(setOf("PV Power", "Output Power", "Grid Power"))
    val trendsSensors: LiveData<Set<String>> = _trendsSensors

    private val _trendsDays = MutableLiveData<Int>(3)
    val trendsDays: LiveData<Int> = _trendsDays

    // History Cache (Date -> JSON String)
    private val historyCache = mutableMapOf<String, String>()
    
    // Session Settings Cache (FieldId -> ValueLabel)
    private val settingsSessionCache = mutableMapOf<String, String>()

    private val _automationRules = MutableLiveData<List<com.dessmonitor.smartess.data.models.AutomationRule>>(emptyList())
    val automationRules: LiveData<List<com.dessmonitor.smartess.data.models.AutomationRule>> = _automationRules

    fun setAutomationRules(rules: List<com.dessmonitor.smartess.data.models.AutomationRule>) {
        _automationRules.value = rules
        prefs.edit().putString("automation_rules", gson.toJson(rules)).apply()
    }
    
    // Categories synced in this session
    private val syncedCategories = mutableSetOf<String>()

    fun markCategorySynced(category: String) {
        syncedCategories.add(category)
    }

    fun isCategorySynced(category: String): Boolean = syncedCategories.contains(category)

    fun updateSettingsCache(fieldId: String, value: String) {
        settingsSessionCache[fieldId] = value
        prefs.edit().putString("settings_cache", gson.toJson(settingsSessionCache)).apply()
    }

    fun getCachedSettingsValue(fieldId: String): String? = settingsSessionCache[fieldId]

    fun setSelectedDate(date: java.time.LocalDate) {
        _selectedDate.value = date
    }

    fun setSelectedStats(stats: List<String>) {
        _selectedStats.value = stats
        prefs.edit().putString("selected_stats", gson.toJson(stats)).apply()
    }

    fun setAnalysisSensors(sensors: Set<String>) {
        _analysisSensors.value = sensors
        prefs.edit().putString("analysis_sensors", gson.toJson(sensors)).apply()
    }

    fun setTrendsSensors(sensors: Set<String>) {
        _trendsSensors.value = sensors
        prefs.edit().putString("trends_sensors", gson.toJson(sensors)).apply()
    }

    fun setTrendsDays(days: Int) {
        _trendsDays.value = days
        prefs.edit().putInt("trends_days", days).apply()
    }

    fun showSensorInTrends(sensor: String, days: Int = 1) {
        val trendName = when {
            sensor.contains("PV", true) -> "PV Power"
            sensor.contains("Load", true) || sensor.contains("Output", true) -> "Output Power"
            sensor.contains("Grid", true) -> "Grid Power"
            sensor.contains("SOC", true) || sensor.contains("Battery", true) -> "SOC"
            else -> sensor
        }
        setTrendsSensors(setOf(trendName))
        setTrendsDays(days)
    }

    fun setUseSystemTheme(use: Boolean) {
        _useSystemTheme.value = use
        prefs.edit().putBoolean("use_system_theme", use).apply()
    }

    fun setIsDarkModeManual(isDark: Boolean) {
        _isDarkModeManual.value = isDark
        prefs.edit().putBoolean("is_dark_mode_manual", isDark).apply()
    }

    fun setSelectedPaletteIndex(index: Int) {
        _selectedPaletteIndex.value = index
        prefs.edit().putInt("selected_palette_index", index).apply()
    }

    fun setCustomPalette(colors: List<String>) {
        _customPalette.value = colors
        prefs.edit().putString("custom_palette", gson.toJson(colors)).apply()
    }

    fun getActivePalette(): List<String> {
        val index = selectedPaletteIndex.value ?: 0
        return if (index >= 0 && index < predefinedPalettes.size) {
            predefinedPalettes[index]
        } else {
            customPalette.value ?: predefinedPalettes[0]
        }
    }

    init {
        val savedUsername = prefs.getString("username", null)
        val savedPassword = prefs.getString("password", null)
        val savedCompanyKey = prefs.getString("company_key", "bnrl_frRFjEz8Mkn") ?: "bnrl_frRFjEz8Mkn"

        if (!savedUsername.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
            api.username = savedUsername
            api.password = savedPassword
            api.companyKey = savedCompanyKey
            _isLoggedIn.value = true
        }

        // Load cached devices
        val cachedDevicesJson = prefs.getString("cached_devices", null)
        if (cachedDevicesJson != null) {
            try {
                val type = object : TypeToken<List<DeviceInfo>>() {}.type
                val cached: List<DeviceInfo> = gson.fromJson(cachedDevicesJson, type)
                _devices.value = cached
            } catch (_: Exception) {}
        }

        // Load settings
        _trendsDays.value = prefs.getInt("trends_days", 3)
        
        val statsJson = prefs.getString("selected_stats", null)
        if (statsJson != null) try { _selectedStats.value = gson.fromJson(statsJson, object : TypeToken<List<String>>() {}.type) } catch (_: Exception) {}

        val anaJson = prefs.getString("analysis_sensors", null)
        if (anaJson != null) try { _analysisSensors.value = gson.fromJson(anaJson, object : TypeToken<Set<String>>() {}.type) } catch (_: Exception) {}

        val trendSensJson = prefs.getString("trends_sensors", null)
        if (trendSensJson != null) try { _trendsSensors.value = gson.fromJson(trendSensJson, object : TypeToken<Set<String>>() {}.type) } catch (_: Exception) {}

        val autoJson = prefs.getString("automation_rules", null)
        if (autoJson != null) try { _automationRules.value = gson.fromJson(autoJson, object : TypeToken<List<com.dessmonitor.smartess.data.models.AutomationRule>>() {}.type) } catch (_: Exception) {}

        // Load history cache
        val savedHistory = prefs.getString("history_cache", null)
        if (savedHistory != null) try { historyCache.putAll(gson.fromJson(savedHistory, object : TypeToken<Map<String, String>>() {}.type)) } catch (_: Exception) {}

        // Load settings cache
        val savedSettings = prefs.getString("settings_cache", null)
        if (savedSettings != null) try { settingsSessionCache.putAll(gson.fromJson(savedSettings, object : TypeToken<Map<String, String>>() {}.type)) } catch (_: Exception) {}

        // Periodic Sync (Every 5 minutes)
        scope.launch {
            while (true) {
                if (isLoggedIn.value == true) {
                    Log.d("DeviceRepository", "Performing periodic sync...")
                    loadDevices()
                }
                delay(5.minutes)
            }
        }
    }

    suspend fun login(username: String, password: String, companyKey: String = "bnrl_frRFjEz8Mkn"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            api.username = username
            api.password = password
            api.companyKey = companyKey
            val success = api.authenticate()
            if (success) {
                prefs.edit().putString("username", username).putString("password", password).putString("company_key", companyKey).apply()
                _isLoggedIn.postValue(true)
                loadDevices()
                Result.success(true)
            } else {
                Result.failure(Exception("Invalid credentials"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    fun logout() {
        prefs.edit().clear().apply()
        api.token = null
        api.secret = null
        _devices.postValue(emptyList())
        _isLoggedIn.postValue(false)
        historyCache.clear()
    }

    fun mapSensorTitle(devcode: Int?, originalTitle: String): String {
        if (devcode == 2452) {
            return when (originalTitle.trim()) {
                "AC Output Frequency" -> "Output Frequency"
                "AC output active power" -> "Output Power"
                "AC output apparent power" -> "Output Apparent Power"
                "AC output voltage" -> "Output Voltage"
                "Battery Capacity" -> "SOC"
                "Battery Discharging Current" -> "Battery Discharge Current"
                "Battery Charging Current" -> "Battery Charge Current"
                "Grid voltage" -> "Grid Voltage"
                "Output load percent" -> "Load Percent"
                "PV1 Input Power" -> "PV Power"
                "PV1 Input voltage" -> "PV Voltage"
                "PV2 Input Power" -> "PV2 Power"
                "Today generation", "energyToday" -> "Daily Yield"
                "Total generation", "energyTotal" -> "Total Yield"
                "Month generation" -> "Month Yield"
                "Year generation" -> "Year Yield"
                "Second AC output frequency" -> "Second Output Frequency"
                "Second AC output voltage" -> "Second Output Voltage"
                else -> originalTitle
            }
        }
        return originalTitle
    }

    private fun transformValue(devcode: Int?, title: String, value: Any): Any {
        if (devcode == 2452) {
            val mappedTitle = mapSensorTitle(devcode, title)
            if (mappedTitle in listOf("Daily Yield", "Month Yield", "Year Yield")) {
                try {
                    val num = value.toString().toDouble()
                    if (title.contains("generation", ignoreCase = true)) return num / 1000.0
                } catch (_: Exception) {}
            }
        }
        return value
    }

    suspend fun loadDevices(): Result<List<DeviceInfo>> = withContext(Dispatchers.IO) {
        try {
            val plants = api.queryPlants()
            val allDevices = mutableListOf<DeviceInfo>()
            for (plant in plants) {
                val pid = plant.optLong("pid")
                val collectors = api.queryCollectorsForProject(pid)
                for (collector in collectors) {
                    val pn = collector.optString("pn")
                    val devResponse = api.queryCollectorDevices(pn)
                    for (devJson in devResponse) {
                        val sn = devJson.optString("sn")
                        val devcode = devJson.optInt("devcode", 0)
                        val devaddr = devJson.optInt("devaddr", 0)
                        val lastData = async { try { api.queryDeviceLastData(pn, devcode, devaddr, sn) } catch (_: Exception) { emptyList<DataPoint>() } }.await()
                        val params = async { try { api.queryDeviceParameters(pn, devcode, devaddr, sn) } catch (_: Exception) { emptyList<DataPoint>() } }.await()
                        val summary = async { try { api.webQueryDeviceEs(pid) } catch (_: Exception) { emptyList<DataPoint>() } }.await()
                        val mergedData = (lastData + params + summary)
                            .filter { it.title.isNotEmpty() }
                            .distinctBy { it.title }
                            .map { it.copy(value = transformValue(devcode, it.title, it.value), title = mapSensorTitle(devcode, it.title)) }
                        
                        val device = DeviceInfo(serialNumber = sn, alias = devJson.optString("alias"), pn = pn, pid = pid, devcode = if (devcode != 0) devcode else null, devaddr = devaddr, dataPoints = mergedData)
                        allDevices.add(device)
                        
                        // Automatically update alarms for this device
                        launch { getAlarms(device) }
                    }
                }
            }
            _devices.postValue(allDevices)
            _lastUpdateTime.postValue(System.currentTimeMillis())
            prefs.edit().putString("cached_devices", gson.toJson(allDevices)).apply()
            Result.success(allDevices)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getHistory(device: DeviceInfo, date: String, forceSync: Boolean = false): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (!forceSync && historyCache.containsKey(date)) {
            if (date == java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)) {
                scope.launch { fetchAndCacheHistory(device, date) }
            }
            return@withContext Result.success(JSONObject(historyCache[date]!!))
        }
        fetchAndCacheHistory(device, date)
    }

    private suspend fun fetchAndCacheHistory(device: DeviceInfo, date: String): Result<JSONObject> = coroutineScope {
        val pn = device.pn ?: return@coroutineScope Result.failure(Exception("Missing PN"))
        val devcode = device.devcode ?: return@coroutineScope Result.failure(Exception("Missing devcode"))
        try {
            var resultJson: JSONObject? = null
            val generalActions = listOf("queryDeviceDataOneDay", "queryDeviceDataOneDayPaging")
            for (action in generalActions) {
                try {
                    val res = api.queryDeviceHistoryData(pn, devcode, device.devaddr ?: 1, device.serialNumber, date)
                    if ((res.optJSONObject("dat")?.optJSONArray("row")?.length() ?: 0) > 0) {
                        resultJson = res
                        break
                    }
                } catch (_: Exception) {}
            }

            if (resultJson == null) {
                val paramsToTry = listOf("PV_OUTPUT_POWER", "AC_OUTPUT_POWER", "LOAD_POWER", "SOC")
                val results = paramsToTry.map { param ->
                    async {
                        try {
                            val res = api.queryDeviceHistoryData(pn, devcode, device.devaddr ?: 1, device.serialNumber, date, param)
                            if ((res.optJSONObject("dat")?.optJSONArray("detail")?.length() ?: 0) > 0) param to res else null
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull()

                if (results.isNotEmpty()) {
                    val merged = JSONObject().put("err", 0)
                    val mergedDat = JSONObject()
                    val mergedArray = org.json.JSONArray()
                    results.forEach { pair ->
                        val res = pair.second
                        val p = pair.first
                        val items = res.optJSONObject("dat")?.optJSONArray("detail")
                        for (i in 0 until (items?.length() ?: 0)) {
                            mergedArray.put(JSONObject(items!!.getJSONObject(i).toString()).put("title", p))
                        }
                    }
                    mergedDat.put("data", mergedArray)
                    merged.put("dat", mergedDat)
                    resultJson = merged
                }
            }

            if (resultJson != null) {
                historyCache[date] = resultJson.toString()
                prefs.edit().putString("history_cache", gson.toJson(historyCache)).apply()
                Result.success(resultJson)
            } else Result.failure(Exception("No data"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getHistoryRange(device: DeviceInfo, days: Int): Result<JSONObject> = withContext(Dispatchers.IO) {
        val today = java.time.LocalDate.now()
        val results = (0 until days).map { i ->
            val date = today.minusDays(i.toLong()).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            async { getHistory(device, date) }
        }.awaitAll()

        val combinedArray = org.json.JSONArray()
        results.forEach { res ->
            val json = res.getOrNull() ?: return@forEach
            val dat = json.optJSONObject("dat") ?: return@forEach
            val titles = dat.optJSONArray("title")
            val rows = dat.optJSONArray("row")
            if (rows != null && titles != null) {
                for (i in 0 until rows.length()) {
                    val fields = rows.getJSONObject(i).optJSONArray("field") ?: continue
                    val ts = fields.optString(1)
                    for (j in 2 until fields.length()) {
                        val tObj = titles.optJSONObject(j)
                        val rawTitle = tObj?.optString("title") ?: "F$j"
                        val mappedTitle = mapSensorTitle(device.devcode, rawTitle)
                        combinedArray.put(JSONObject().put("title", mappedTitle).put("ts", ts).put("val", fields.opt(j)))
                    }
                }
            } else {
                val data = dat.optJSONArray("data") ?: dat.optJSONArray("list") ?: dat.optJSONArray("detail") ?: json.optJSONArray("dat")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val rawTitle = item.optString("title")
                        if (rawTitle.isNotEmpty()) {
                            item.put("title", mapSensorTitle(device.devcode, rawTitle))
                        }
                        combinedArray.put(item)
                    }
                }
            }
        }
        Result.success(JSONObject().put("err", 0).put("dat", JSONObject().put("data", combinedArray)))
    }

    suspend fun getHistory24h(device: DeviceInfo): Result<JSONObject> = getHistoryRange(device, 1)

    suspend fun getAlarms(device: DeviceInfo): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        try {
            // First return cached alarms from DB if available
            // Note: Returning Result.success(cached) immediately might prevent network sync if not careful.
            // But the UI usually calls this once on launch.

            val pn = device.pn ?: return@withContext Result.failure(Exception("Missing PN"))
            val devcode = device.devcode ?: return@withContext Result.failure(Exception("Missing devcode"))
            val end = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            val start = java.time.LocalDate.now().minusDays(90).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

            val response = try {
                api.queryDeviceAlarms(pn, devcode, device.devaddr ?: 1, device.serialNumber, startDate = start, endDate = end)
            } catch (_: Exception) {
                api.queryDeviceAlarms(pn, devcode, device.devaddr ?: 1, device.serialNumber)
            }

            val dat = response.optJSONObject("dat")
            val list = dat?.optJSONArray("list") ?:
                       dat?.optJSONArray("data") ?:
                       dat?.optJSONArray("warning") ?:
                       dat?.optJSONArray("detail") ?:
                       response.optJSONArray("dat")

            val resultList = mutableListOf<JSONObject>()
            val entities = mutableListOf<AlarmEntity>()

            if (list != null) {
                for (i in 0 until list.length()) {
                    val json = list.getJSONObject(i)
                    resultList.add(json)
                    
                    val alarmTitle = listOf(
                        json.optString("name"),
                        json.optString("title"),
                        json.optString("alarmName"),
                        json.optString("warnName"),
                        json.optString("des"),
                        json.optString("desc"),
                        json.optString("descx"),
                        json.optString("msg"),
                        json.optString("content"),
                        json.optString("err"),
                        json.optString("error"),
                        json.optString("info")
                    ).firstOrNull { it.isNotBlank() } ?: "Alarm / Warning"

                    val alarmTime = listOf(
                        json.optString("gts"),
                        json.optString("ts"),
                        json.optString("time"),
                        json.optString("occurTime"),
                        json.optString("date")
                    ).firstOrNull { it.isNotBlank() } ?: ""

                    entities.add(AlarmEntity(
                        name = alarmTitle,
                        time = alarmTime,
                        status = json.optInt("status"),
                        deviceSn = device.serialNumber,
                        descx = json.optString("descx").ifBlank { json.optString("desc") },
                        gts = json.optString("gts")
                    ))
                }
                if (entities.isNotEmpty()) {
                    alarmDao.clearAlarms(device.serialNumber)
                    alarmDao.insertAlarms(entities)
                }
            }
            Result.success(resultList)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun getAlarmsFlow(deviceSn: String) = alarmDao.getAlarmsByDevice(deviceSn)

    suspend fun getControlFields(device: DeviceInfo): Result<JSONObject> = withContext(Dispatchers.IO) {
        try { Result.success(api.queryDeviceControlFields(device.pn!!, device.devcode!!, device.devaddr ?: 1, device.serialNumber)) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getControlValue(device: DeviceInfo, fieldId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try { Result.success(api.queryDeviceCtrlValue(device.pn!!, device.devcode!!, device.devaddr ?: 1, device.serialNumber, fieldId)) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun setControlValue(device: DeviceInfo, fieldId: String, value: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try { api.setDeviceControlValue(device.pn!!, device.devcode!!, device.devaddr ?: 1, device.serialNumber, fieldId, value); Result.success(true) } catch (e: Exception) { Result.failure(e) }
    }
}
