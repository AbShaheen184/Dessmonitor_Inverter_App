package com.dessmonitor.smartess.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repository: DeviceRepository) {
    val devices by repository.devices.observeAsState(emptyList())
    val activeDevice = devices.firstOrNull()
    
    val selectedDate by repository.selectedDate.observeAsState(LocalDate.now())
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var historyJson by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    LaunchedEffect(selectedDate, activeDevice) {
        if (activeDevice != null) {
            Log.d("HistoryScreen", "Fetching history for ${selectedDate}")
            isLoading = true
            errorMessage = null
            repository.getHistory(activeDevice, selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .onSuccess { 
                    Log.d("HistoryScreen", "History fetch success")
                    historyJson = it
                    isLoading = false
                    selectedTime = null
                }
                .onFailure { 
                    Log.e("HistoryScreen", "History fetch failed", it)
                    errorMessage = it.message
                    isLoading = false
                    historyJson = null
                }
        }
    }

    // Extract time labels from history JSON
    val timePoints = remember(historyJson) {
        val list = mutableListOf<String>()
        val dat = historyJson?.optJSONObject("dat")
        
        // 1. Check for 'row' format (queryDeviceDataOneDay)
        val rows = dat?.optJSONArray("row")
        val titles = dat?.optJSONArray("title")
        if (rows != null && titles != null) {
            // Find the index of the time field
            var timeIndex = -1
            for (j in 0 until titles.length()) {
                val t = titles.getJSONObject(j).optString("title").uppercase()
                if (t.contains("TIME") || t.contains("TIMESTAMP") || t.contains("DATE")) {
                    timeIndex = j
                    break
                }
            }
            
            // Heuristic for time index if titles don't help
            if (timeIndex == -1 && rows.length() > 0) {
                val firstRowFields = rows.getJSONObject(0).optJSONArray("field")
                if (firstRowFields != null) {
                    for (j in 0 until firstRowFields.length()) {
                        val f = firstRowFields.optString(j)
                        if (f.contains(":") && f.length < 25) {
                            timeIndex = j
                            break
                        }
                    }
                }
            }
            
            // Default fallback
            if (timeIndex == -1) timeIndex = 0
            
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val fields = row.optJSONArray("field")
                if (fields != null && timeIndex < fields.length()) {
                    list.add(fields.getString(timeIndex))
                }
            }
        } else {
            // 2. Check for traditional flat formats
            val items = dat?.optJSONArray("detail") ?: 
                        dat?.optJSONArray("list") ?: 
                        dat?.optJSONArray("data") ?: 
                        historyJson?.optJSONArray("dat")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val time = item.optString("ts").ifEmpty { item.optString("time") }
                    if (time.isNotEmpty()) list.add(time)
                }
            }
        }

        // Improved chronological sorting
        list.distinct().sortedWith { a, b ->
            val extractTime = { s: String ->
                try {
                    val cleaned = s.trim()
                    // Handle "2026-08-19 18:43:02" or "18:43:02"
                    val timePart = if (cleaned.contains(" ")) cleaned.split(" ")[1] else cleaned
                    timePart
                } catch (e: Exception) { s }
            }
            
            val tA = extractTime(a)
            val tB = extractTime(b)
            
            // If they are yyyy-MM-dd HH:mm:ss, string compare works perfectly
            // If they are just times, we might need padding for 9:00 vs 10:00
            if (tA.length != tB.length && tA.contains(":") && tB.contains(":")) {
                val pad = { s: String -> if (s.indexOf(":") == 1) "0$s" else s }
                pad(tA).compareTo(pad(tB))
            } else {
                a.compareTo(b)
            }
        }
    }

    // Extract data for selected time
    val dataForTime = remember(selectedTime, historyJson) {
        val list = mutableListOf<String>()
        val dat = historyJson?.optJSONObject("dat")
        
        // 1. Check for 'row' format
        val rows = dat?.optJSONArray("row")
        val titles = dat?.optJSONArray("title")
        if (rows != null && titles != null && selectedTime != null) {
            // Find time index using the same heuristic as above
            var timeIndex = -1
            for (j in 0 until titles.length()) {
                val t = titles.getJSONObject(j).optString("title").uppercase()
                if (t.contains("TIME") || t.contains("TIMESTAMP") || t.contains("DATE")) {
                    timeIndex = j
                    break
                }
            }
            if (timeIndex == -1 && rows.length() > 0) {
                val firstRowFields = rows.getJSONObject(0).optJSONArray("field")
                if (firstRowFields != null) {
                    for (j in 0 until firstRowFields.length()) {
                        val f = firstRowFields.optString(j)
                        if (f.contains(":") && f.length < 25) {
                            timeIndex = j
                            break
                        }
                    }
                }
            }
            if (timeIndex == -1) timeIndex = 0

            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val fields = row.optJSONArray("field")
                if (fields != null && timeIndex < fields.length() && fields.getString(timeIndex) == selectedTime) {
                    for (j in 0 until fields.length()) {
                        if (j == timeIndex) continue
                        
                        val titleObj = titles.optJSONObject(j)
                        val title = titleObj?.optString("title") ?: "Field $j"
                        val value = fields.getString(j)
                        
                        // Skip values that look like long hashes
                        if (value.length > 25 && !value.contains(" ")) continue
                        
                        val unit = titleObj?.optString("unit") ?: ""
                        list.add("$title: $value $unit")
                    }
                }
            }
        } else {
            // 2. Check for traditional flat formats
            val items = dat?.optJSONArray("detail") ?: 
                        dat?.optJSONArray("list") ?: 
                        dat?.optJSONArray("data") ?: 
                        historyJson?.optJSONArray("dat")
            if (items != null && selectedTime != null) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val ts = item.optString("ts").ifEmpty { item.optString("time") }
                    val tsShort = if (ts.length >= 16) ts.substring(0, 16) else ts
                    val selectedTimeShort = if (selectedTime!!.length >= 16) selectedTime!!.substring(0, 16) else selectedTime!!
                    
                    if (tsShort == selectedTimeShort) {
                        val title = item.optString("title").ifEmpty { item.optString("name") }
                        val value = item.opt("val") ?: item.opt("value") ?: ""
                        val unit = item.optString("unit")
                        if (title.isNotEmpty()) {
                            list.add("$title: $value $unit")
                        } else {
                            list.add("Value: $value")
                        }
                    }
                }
            }
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device History") },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date")
                    }
                }
            )
        }
    ) { padding ->
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val newDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                            repository.setSelectedDate(newDate)
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Selected Date Display
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Log Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (historyJson == null || timePoints.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage ?: "No history data found for this date")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            // Trigger refresh by re-setting the same date to repository
                            repository.setSelectedDate(selectedDate)
                        }) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Column 1: History Time
                    LazyColumn(
                        modifier = Modifier.weight(1.2f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        items(timePoints) { time ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTime = time }
                                    .background(if (selectedTime == time) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                            ) {
                                Text(
                                    text = if (time.contains(" ")) time.split(" ")[1] else time, 
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedTime == time) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTime == time) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                    
                    // Vertical Divider
                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    
                    // Column 2: History Data
                    LazyColumn(
                        modifier = Modifier.weight(2f).fillMaxHeight()
                    ) {
                        if (selectedTime != null) {
                            item {
                                Text(
                                    "Logs for ${if (selectedTime!!.contains(" ")) selectedTime!!.split(" ")[1] else selectedTime}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            items(dataForTime) { data ->
                                Surface(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                                ) {
                                    Text(
                                        text = data,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            item {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "Select a time entry from the left", 
                                        modifier = Modifier.padding(32.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
