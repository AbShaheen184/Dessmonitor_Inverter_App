package com.dessmonitor.smartess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dessmonitor.smartess.data.models.DeviceInfo
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import kotlinx.coroutines.*

private fun categorizeSetting(name: String): String {
    val n = name.uppercase()
    return when {
        n.contains("BATTERY") || n.contains("BMS") || n.contains("CHARGE") || n.contains("DISCHARGE") -> "Battery"
        n.contains("PV") || n.contains("SOLAR") -> "PV / Solar"
        n.contains("GRID") || n.contains("AC") || n.contains("LINE") -> "Grid / AC"
        n.contains("OUTPUT") || n.contains("LOAD") -> "Output"
        n.contains("SYSTEM") || n.contains("MODE") || n.contains("PRIORITY") || n.contains("BUZZER") -> "System"
        else -> "General"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: DeviceRepository,
    device: DeviceInfo,
    onBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var isSyncing by remember { mutableStateOf(false) }
    var fields by remember { mutableStateOf<List<ControlField>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Process fields helper
    fun processFields(json: org.json.JSONObject, currentDevice: DeviceInfo, individualValues: Map<String, String>? = null): List<ControlField> {
        val list = mutableListOf<ControlField>()
        val dat = json.optJSONObject("dat")
        val fieldsArray = dat?.optJSONArray("field")
        
        if (fieldsArray != null) {
            for (i in 0 until fieldsArray.length()) {
                val f = fieldsArray.getJSONObject(i)
                val id = f.optString("id")
                val name = f.optString("name")
                val options = mutableMapOf<String, String>()
                var selectedFromItems: String? = null
                
                val itemArray = f.optJSONArray("item")
                if (itemArray != null) {
                    for (j in 0 until itemArray.length()) {
                        val item = itemArray.getJSONObject(j)
                        val k = item.optString("key").ifEmpty { 
                            item.optString("id").ifEmpty { 
                                item.optString("v").ifEmpty { 
                                    item.optString("code", "") 
                                } 
                            } 
                        }.trim()
                        
                        val v = item.optString("val").ifEmpty { 
                            item.optString("name").ifEmpty { 
                                item.optString("desc").ifEmpty { 
                                    item.optString("text", "") 
                                } 
                            } 
                        }.trim()

                        if (k.isNotEmpty() && v.isNotEmpty()) {
                            options[k] = v
                        }
                        
                        if (item.optInt("sel") == 1 || item.optInt("selected") == 1 || 
                            item.optString("sel") == "1" || item.optBoolean("selected")) {
                            selectedFromItems = v
                        }
                    }
                }
                
                // 1. Try from NEW individualValues map (Result of queryDeviceCtrlValue)
                var rawVal = individualValues?.get(id) ?: ""
                
                // 2. Try from control field response itself
                if (rawVal.isEmpty()) {
                    rawVal = f.optString("val", "").ifEmpty { 
                        f.optString("value", "").ifEmpty { 
                            f.optString("cur", "").ifEmpty { 
                                f.optString("now", "") 
                            } 
                        } 
                    }.trim()
                }
                
                // 3. Match with telemetry data points
                if (rawVal.isEmpty() && selectedFromItems == null) {
                    val mappedName = repository.mapSensorTitle(currentDevice.devcode, name)
                    val telemetry = currentDevice.dataPoints.find { 
                        (it.id != null && it.id == id) ||
                        it.title.equals(mappedName, ignoreCase = true) || 
                        it.title.equals(name, ignoreCase = true) ||
                        it.title.contains(name, ignoreCase = true)
                    }
                    if (telemetry != null) {
                        rawVal = telemetry.value.toString().trim()
                    }
                }

                val displayValue = when {
                    selectedFromItems != null -> selectedFromItems
                    rawVal.isNotEmpty() -> {
                        val normalizedKey = if (rawVal.endsWith(".0")) rawVal.substring(0, rawVal.length - 2) else rawVal
                        val byKey = options[normalizedKey] ?: options[rawVal] ?: 
                                    options.entries.find { it.key.equals(normalizedKey, ignoreCase = true) }?.value
                        
                        if (byKey != null) {
                            byKey
                        } else {
                            val isValue = options.values.find { it.equals(rawVal, ignoreCase = true) || it.startsWith(rawVal, ignoreCase = true) }
                            if (isValue != null) {
                                isValue
                            } else if (options.isNotEmpty()) {
                                val idx = rawVal.toIntOrNull()
                                if (idx != null && idx >= 0 && idx < options.size) {
                                    options.values.toList()[idx]
                                } else {
                                    "$rawVal ${f.optString("unit")}".trim()
                                }
                            } else {
                                "$rawVal ${f.optString("unit")}".trim()
                            }
                        }
                    }
                    else -> repository.getCachedSettingsValue(id)
                }

                if (displayValue != null) {
                    repository.updateSettingsCache(id, displayValue)
                }

                list.add(ControlField(
                    id = id,
                    name = name,
                    unit = f.optString("unit"),
                    options = options,
                    category = categorizeSetting(name),
                    currentValue = displayValue
                ))
            }
        }
        return list
    }

    // Background refresh logic
    LaunchedEffect(device, selectedCategory) {
        if (selectedCategory == null) {
            // Initial component mount
            repository.getControlFields(device).onSuccess { json ->
                fields = processFields(json, device)
                if (fields.isNotEmpty()) {
                    selectedCategory = fields.first().category
                }
                isLoading = false
            }.onFailure { isLoading = false }
        } else if (!repository.isCategorySynced(selectedCategory!!)) {
            // Background sync ONLY if not already synced in this session
            isSyncing = true
            coroutineScope {
                repository.getControlFields(device).onSuccess { fieldsJson ->
                    val categoryFields = processFields(fieldsJson, device).filter { it.category == selectedCategory }
                    
                    // Concurrent fetch for each field's value in this category
                    val deferredValues = categoryFields.map { field ->
                        async {
                            val res = repository.getControlValue(device, field.id)
                            if (res.isSuccess) {
                                val dat = res.getOrThrow().optJSONObject("dat")
                                val v = dat?.optString("val") ?: dat?.optString("value") ?: ""
                                field.id to v
                            } else {
                                field.id to ""
                            }
                        }
                    }
                    val valueMap = deferredValues.awaitAll().filter { it.second.isNotEmpty() }.toMap()
                    
                    // Refresh telemetry as well
                    repository.loadDevices().onSuccess { devices ->
                        val updatedDevice = devices.find { it.serialNumber == device.serialNumber } ?: device
                        fields = processFields(fieldsJson, updatedDevice, valueMap)
                        repository.markCategorySynced(selectedCategory!!)
                    }.onFailure {
                        fields = processFields(fieldsJson, device, valueMap)
                    }
                }
            }
            isSyncing = false
            isLoading = false
        } else {
            // Already synced in session, just show cached fields
            isLoading = false
        }
    }

    val categories = remember(fields) {
        fields.map { it.category }.distinct().sortedBy { 
            // Custom order
            when(it) {
                "System" -> 0
                "PV / Solar" -> 1
                "Output" -> 2
                "Battery" -> 3
                "Grid / AC" -> 4
                else -> 5
            }
        }
    }

    val filteredFields = remember(selectedCategory, fields) {
        fields.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inverter Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Left Column: Categories
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    items(categories) { category ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = category }
                                .background(if (selectedCategory == category) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = category, 
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedCategory == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                // Vertical Divider
                VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // Right Column: Settings
                LazyColumn(
                    modifier = Modifier.weight(2f).fillMaxHeight()
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedCategory ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (isSyncing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Syncing...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                    items(filteredFields) { field ->
                        SettingsItem(field = field) { newValue ->
                            scope.launch {
                                repository.setControlValue(device, field.id, newValue)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

data class ControlField(
    val id: String,
    val name: String,
    val unit: String?,
    val options: Map<String, String>,
    val category: String,
    val currentValue: String? = null
)

@Composable
fun SettingsItem(field: ControlField, onValueChange: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    
    ListItem(
        headlineContent = { 
            Text(
                field.name, 
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            ) 
        },
        supportingContent = { 
            Column {
                if (!field.currentValue.isNullOrEmpty()) {
                    Text(
                        "Current: ${field.currentValue}", 
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                field.unit?.let { if (it.isNotEmpty()) Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        },
        trailingContent = {
            TextButton(
                onClick = { showDialog = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Edit")
            }
        }
    )

    if (showDialog) {
        if (field.options.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Set ${field.name}") },
                text = {
                    Box(modifier = Modifier.heightIn(max = 300.dp)) {
                        LazyColumn {
                            field.options.forEach { (key, value) ->
                                item {
                                    TextButton(
                                        onClick = {
                                            onValueChange(key)
                                            showDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(value, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            )
        } else {
            var textValue by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Set ${field.name}") },
                text = {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        label = { Text("Value ${field.unit ?: ""}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onValueChange(textValue)
                        showDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}
