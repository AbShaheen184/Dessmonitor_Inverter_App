package com.dessmonitor.smartess.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dessmonitor.smartess.data.models.AutomationRule
import com.dessmonitor.smartess.data.models.ComparisonOperator
import com.dessmonitor.smartess.data.models.DeviceInfo
import com.dessmonitor.smartess.data.models.RightOperandType
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutomationsDialog(
    repository: DeviceRepository,
    activeDevice: DeviceInfo?,
    onDismiss: () -> Unit
) {
    val rules by repository.automationRules.observeAsState(emptyList())
    var ruleToEdit by remember { mutableStateOf<AutomationRule?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var availableFields by remember { mutableStateOf<List<ControlField>>(emptyList()) }
    var isLoadingFields by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Battery optimization check helper
    fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    val telemetryTitles = remember(activeDevice) {
        activeDevice?.dataPoints?.map { it.title }?.distinct()?.sorted() ?: listOf(
            "PV Power", "Output Power", "Battery Voltage", "SOC", "Battery Capacity",
            "Grid Voltage", "AC Output Rating Voltage", "Battery Charge Current", "Battery Discharge Current"
        )
    }

    LaunchedEffect(activeDevice) {
        if (activeDevice != null) {
            isLoadingFields = true
            repository.getControlFields(activeDevice).onSuccess { json ->
                availableFields = parseControlFields(json, activeDevice, repository)
                isLoadingFields = false
            }.onFailure {
                isLoadingFields = false
            }
        }
    }

    // Function to execute automations manually / on demand
    fun runAutomations() {
        if (activeDevice == null || rules.isEmpty()) return
        scope.launch {
            statusMessage = "Evaluating automation rules..."
            for (rule in rules) {
                if (!rule.isEnabled) continue
                
                // Helper to get numeric telemetry value
                fun getVal(title: String): Double? {
                    val dp = activeDevice.dataPoints.find { 
                        it.title.trim().equals(title, ignoreCase = true) || it.title.trim().contains(title, ignoreCase = true) 
                    }
                    return dp?.value?.toString()?.toDoubleOrNull()
                }

                val leftVal = getVal(rule.leftParameter) ?: continue
                val rightVal = if (rule.rightOperandType == RightOperandType.CUSTOM_VALUE) {
                    rule.rightCustomValue
                } else {
                    rule.rightParameter?.let { getVal(it) } ?: continue
                }

                val conditionMet = when (rule.operator) {
                    ComparisonOperator.EQUAL -> Math.abs(leftVal - rightVal) < 0.001
                    ComparisonOperator.LESS_THAN_OR_EQUAL -> leftVal <= rightVal
                    ComparisonOperator.GREATER_THAN_OR_EQUAL -> leftVal >= rightVal
                    ComparisonOperator.LESS_THAN -> leftVal < rightVal
                    ComparisonOperator.GREATER_THAN -> leftVal > rightVal
                }

                if (conditionMet) {
                    val actionsTriggered = mutableListOf<String>()

                    // Action 1: Inverter Setting Change (if enabled)
                    if (rule.enableInverterSettingAction && rule.targetSettingId != null && rule.targetSettingValue != null) {
                        actionsTriggered.add("Setting: ${rule.targetSettingName} → ${rule.targetSettingValueDisplay}")
                        repository.setControlValue(activeDevice, rule.targetSettingId, rule.targetSettingValue)
                    }

                    // Action 2: Mobile Notification (if enabled)
                    if (rule.enableNotificationAction) {
                        var formattedMessage = rule.notificationMessageTemplate ?: "Condition met for ${rule.name}"
                        activeDevice.dataPoints.forEach { dp ->
                            formattedMessage = formattedMessage.replace("{${dp.title}}", "${dp.value} ${dp.unit ?: ""}".trim(), ignoreCase = true)
                        }
                        actionsTriggered.add("Mobile Notification")
                        com.dessmonitor.smartess.utils.NotificationUtils.sendNotification(
                            context = context,
                            title = rule.notificationTitle ?: rule.name,
                            message = formattedMessage
                        )
                    }

                    statusMessage = "Condition met for '${rule.name}'. Triggered: ${actionsTriggered.joinToString(", ")}"
                }
            }
            statusMessage = "Automation evaluation complete."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Inverter Automations")
            }
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 520.dp)) {
                Column {
                    // Background Service Info Banner
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Background Service Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Automations continuously run in the background. Grant background & notification permissions below so rules trigger automatically even when the app is closed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.BatteryAlert, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Unrestricted Battery", style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Notifications", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (!statusMessage.isNullOrEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                statusMessage!!,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (rules.isEmpty()) {
                        Text(
                            "No automations configured. Create rules to trigger inverter parameter changes automatically based on telemetry conditions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(rules) { rule ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { ruleToEdit = rule },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(rule.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                Spacer(Modifier.width(6.dp))
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Rule", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            }
                                            val rightText = if (rule.rightOperandType == RightOperandType.CUSTOM_VALUE) rule.rightCustomValue.toString() else rule.rightParameter ?: ""
                                            Text(
                                                "IF ${rule.leftParameter} ${rule.operator.symbol} $rightText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            val actionsList = mutableListOf<String>()
                                            if (rule.enableInverterSettingAction) {
                                                actionsList.add("SET ${rule.targetSettingName ?: ""} → ${rule.targetSettingValueDisplay ?: ""}")
                                            }
                                            if (rule.enableNotificationAction) {
                                                actionsList.add("NOTIFY: \"${rule.notificationMessageTemplate ?: ""}\"")
                                            }
                                            Text(
                                                "THEN ${actionsList.joinToString(" AND ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        Switch(
                                            checked = rule.isEnabled,
                                            onCheckedChange = { isChecked ->
                                                val updated = rules.map { if (it.id == rule.id) it.copy(isEnabled = isChecked) else it }
                                                repository.setAutomationRules(updated)
                                            }
                                        )
                                        IconButton(onClick = {
                                            repository.setAutomationRules(rules.filter { it.id != rule.id })
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = activeDevice != null && !isLoadingFields
                    ) {
                        if (isLoadingFields) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Loading parameters...")
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create Automation Rule")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { runAutomations() }) {
                    Text("Run Now")
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    )

    if (showCreateDialog || ruleToEdit != null) {
        CreateAutomationRuleDialog(
            initialRule = ruleToEdit,
            telemetryTitles = telemetryTitles,
            availableFields = availableFields,
            onDismiss = { 
                showCreateDialog = false 
                ruleToEdit = null
            },
            onSave = { savedRule ->
                val existingIndex = rules.indexOfFirst { it.id == savedRule.id }
                val updatedRules = if (existingIndex >= 0) {
                    rules.toMutableList().apply { set(existingIndex, savedRule) }
                } else {
                    rules + savedRule
                }
                repository.setAutomationRules(updatedRules)
                showCreateDialog = false
                ruleToEdit = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateAutomationRuleDialog(
    initialRule: AutomationRule? = null,
    telemetryTitles: List<String>,
    availableFields: List<ControlField>,
    onDismiss: () -> Unit,
    onSave: (AutomationRule) -> Unit
) {
    var ruleName by remember(initialRule) { mutableStateOf(initialRule?.name ?: "") }
    var selectedLeftParam by remember(initialRule) { mutableStateOf(initialRule?.leftParameter ?: (telemetryTitles.firstOrNull() ?: "PV Power")) }
    var selectedOperator by remember(initialRule) { mutableStateOf(initialRule?.operator ?: ComparisonOperator.GREATER_THAN_OR_EQUAL) }
    var rightOperandType by remember(initialRule) { mutableStateOf(initialRule?.rightOperandType ?: RightOperandType.CUSTOM_VALUE) }
    var rightCustomValText by remember(initialRule) { mutableStateOf(initialRule?.rightCustomValue?.toString() ?: "1000") }
    var selectedRightParam by remember(initialRule) { mutableStateOf(initialRule?.rightParameter ?: (telemetryTitles.firstOrNull() ?: "Output Power")) }

    var enableInverterSettingAction by remember(initialRule) { mutableStateOf(initialRule?.enableInverterSettingAction ?: true) }
    var enableNotificationAction by remember(initialRule) { mutableStateOf(initialRule?.enableNotificationAction ?: false) }
    var notificationTitleText by remember(initialRule) { mutableStateOf(initialRule?.notificationTitle ?: "Automation Alert") }
    var notificationTemplateText by remember(initialRule) { mutableStateOf(initialRule?.notificationMessageTemplate ?: "The battery is {SOC} and voltage is {Battery Voltage}") }
    var paramDropdownExpanded by remember { mutableStateOf(false) }

    var selectedField by remember(initialRule, availableFields) {
        mutableStateOf(
            if (initialRule?.targetSettingId != null) availableFields.find { it.id == initialRule.targetSettingId } ?: availableFields.firstOrNull()
            else availableFields.firstOrNull()
        )
    }
    var selectedOptionKey by remember(initialRule, selectedField) { mutableStateOf(initialRule?.targetSettingValue ?: (selectedField?.options?.keys?.firstOrNull() ?: "")) }
    var selectedOptionDisplay by remember(initialRule, selectedField) { mutableStateOf(initialRule?.targetSettingValueDisplay ?: (selectedField?.options?.get(selectedOptionKey) ?: "")) }
    var customSettingTextVal by remember(initialRule) { mutableStateOf(initialRule?.targetSettingValue ?: "") }

    // Dropdown states
    var leftDropdownExpanded by remember { mutableStateOf(false) }
    var opDropdownExpanded by remember { mutableStateOf(false) }
    var rightParamDropdownExpanded by remember { mutableStateOf(false) }
    var fieldDropdownExpanded by remember { mutableStateOf(false) }
    var optionDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Automation Rule") },
        text = {
            Box(modifier = Modifier.heightIn(max = 500.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(
                            value = ruleName,
                            onValueChange = { ruleName = it },
                            label = { Text("Rule Name") },
                            placeholder = { Text("e.g. Switch to SBU on High PV") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // CONDITION HEADER
                    item {
                        Text("WHEN (Telemetry Condition)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    // Left Parameter Dropdown
                    item {
                        ExposedDropdownMenuBox(
                            expanded = leftDropdownExpanded,
                            onExpandedChange = { leftDropdownExpanded = !leftDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedLeftParam,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Parameter") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = leftDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = leftDropdownExpanded,
                                onDismissRequest = { leftDropdownExpanded = false }
                            ) {
                                telemetryTitles.forEach { title ->
                                    DropdownMenuItem(
                                        text = { Text(title) },
                                        onClick = {
                                            selectedLeftParam = title
                                            leftDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Comparison Operator Dropdown
                    item {
                        ExposedDropdownMenuBox(
                            expanded = opDropdownExpanded,
                            onExpandedChange = { opDropdownExpanded = !opDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = "${selectedOperator.symbol} (${selectedOperator.label})",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Operator") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = opDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = opDropdownExpanded,
                                onDismissRequest = { opDropdownExpanded = false }
                            ) {
                                ComparisonOperator.entries.forEach { op ->
                                    DropdownMenuItem(
                                        text = { Text("${op.symbol} — ${op.label}") },
                                        onClick = {
                                            selectedOperator = op
                                            opDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Right Operand Selector (Custom Value vs Parameter)
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = rightOperandType == RightOperandType.CUSTOM_VALUE,
                                onClick = { rightOperandType = RightOperandType.CUSTOM_VALUE },
                                label = { Text("Custom Value") }
                            )
                            FilterChip(
                                selected = rightOperandType == RightOperandType.PARAMETER,
                                onClick = { rightOperandType = RightOperandType.PARAMETER },
                                label = { Text("Compare to Parameter") }
                            )
                        }
                    }

                    if (rightOperandType == RightOperandType.CUSTOM_VALUE) {
                        item {
                            OutlinedTextField(
                                value = rightCustomValText,
                                onValueChange = { rightCustomValText = it },
                                label = { Text("Threshold Value") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        item {
                            ExposedDropdownMenuBox(
                                expanded = rightParamDropdownExpanded,
                                onExpandedChange = { rightParamDropdownExpanded = !rightParamDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedRightParam,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Compare with Parameter") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rightParamDropdownExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = rightParamDropdownExpanded,
                                    onDismissRequest = { rightParamDropdownExpanded = false }
                                ) {
                                    telemetryTitles.forEach { title ->
                                        DropdownMenuItem(
                                            text = { Text(title) },
                                            onClick = {
                                                selectedRightParam = title
                                                rightParamDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ACTION HEADER
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("THEN (Actions - Select One or Both)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    // Multi-action checkboxes
                    item {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { enableInverterSettingAction = !enableInverterSettingAction }
                            ) {
                                Checkbox(checked = enableInverterSettingAction, onCheckedChange = { enableInverterSettingAction = it })
                                Spacer(Modifier.width(8.dp))
                                Text("Change Inverter Setting", fontWeight = FontWeight.Medium)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { enableNotificationAction = !enableNotificationAction }
                            ) {
                                Checkbox(checked = enableNotificationAction, onCheckedChange = { enableNotificationAction = it })
                                Spacer(Modifier.width(8.dp))
                                Text("Send Mobile Notification", fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    if (enableInverterSettingAction) {
                        item {
                            Text("1. Inverter Setting Action", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        // Target Setting Dropdown
                        item {
                            ExposedDropdownMenuBox(
                                expanded = fieldDropdownExpanded,
                                onExpandedChange = { fieldDropdownExpanded = !fieldDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedField?.name ?: "Select Inverter Setting",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Inverter Setting to Change") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fieldDropdownExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = fieldDropdownExpanded,
                                    onDismissRequest = { fieldDropdownExpanded = false }
                                ) {
                                    availableFields.forEach { f ->
                                        DropdownMenuItem(
                                            text = { Text(f.name) },
                                            onClick = {
                                                selectedField = f
                                                selectedOptionKey = f.options.keys.firstOrNull() ?: ""
                                                selectedOptionDisplay = f.options[selectedOptionKey] ?: ""
                                                fieldDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Value Option Selection for the Target Setting
                        if (selectedField != null) {
                            if (selectedField!!.options.isNotEmpty()) {
                                item {
                                    ExposedDropdownMenuBox(
                                        expanded = optionDropdownExpanded,
                                        onExpandedChange = { optionDropdownExpanded = !optionDropdownExpanded }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedOptionDisplay.ifEmpty { selectedOptionKey },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Target Setting Value") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = optionDropdownExpanded) },
                                            modifier = Modifier.menuAnchor().fillMaxWidth()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = optionDropdownExpanded,
                                            onDismissRequest = { optionDropdownExpanded = false }
                                        ) {
                                            selectedField!!.options.forEach { (k, v) ->
                                                DropdownMenuItem(
                                                    text = { Text("$v ($k)") },
                                                    onClick = {
                                                        selectedOptionKey = k
                                                        selectedOptionDisplay = v
                                                        optionDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    OutlinedTextField(
                                        value = customSettingTextVal,
                                        onValueChange = { customSettingTextVal = it },
                                        label = { Text("Target Value ${selectedField?.unit ?: ""}") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    if (enableNotificationAction) {
                        item {
                            Text("2. Mobile Notification Action", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        item {
                            OutlinedTextField(
                                value = notificationTitleText,
                                onValueChange = { notificationTitleText = it },
                                label = { Text("Notification Title") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Column {
                                ExposedDropdownMenuBox(
                                    expanded = paramDropdownExpanded,
                                    onExpandedChange = { paramDropdownExpanded = !paramDropdownExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = notificationTemplateText,
                                        onValueChange = { notificationTemplateText = it },
                                        label = { Text("Notification Message") },
                                        placeholder = { Text("Use {SOC}, {Battery Voltage}, {PV Power} etc") },
                                        supportingText = { Text("Tip: Select parameters below or type '{' to insert live values") },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = paramDropdownExpanded,
                                        onDismissRequest = { paramDropdownExpanded = false }
                                    ) {
                                        telemetryTitles.forEach { title ->
                                            DropdownMenuItem(
                                                text = { Text("{$title}") },
                                                onClick = {
                                                    notificationTemplateText = "$notificationTemplateText {$title}"
                                                    paramDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                Text("Insert Parameter Placeholders:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(4.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    telemetryTitles.forEach { title ->
                                        AssistChip(
                                            onClick = {
                                                notificationTemplateText = "$notificationTemplateText {$title}"
                                            },
                                            label = { Text("{$title}", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = ruleName.isNotBlank() && 
                        (enableInverterSettingAction || enableNotificationAction) && 
                        (!enableInverterSettingAction || selectedField != null),
                onClick = {
                    val targetVal = if (selectedField?.options?.isNotEmpty() == true) selectedOptionKey else customSettingTextVal
                    val targetDisplay = if (selectedField?.options?.isNotEmpty() == true) selectedOptionDisplay else customSettingTextVal

                    val rule = initialRule?.copy(
                        name = ruleName,
                        leftParameter = selectedLeftParam,
                        operator = selectedOperator,
                        rightOperandType = rightOperandType,
                        rightCustomValue = rightCustomValText.toDoubleOrNull() ?: 0.0,
                        rightParameter = if (rightOperandType == RightOperandType.PARAMETER) selectedRightParam else null,
                        enableInverterSettingAction = enableInverterSettingAction,
                        enableNotificationAction = enableNotificationAction,
                        targetSettingId = if (enableInverterSettingAction) selectedField?.id else null,
                        targetSettingName = if (enableInverterSettingAction) selectedField?.name else null,
                        targetSettingValue = if (enableInverterSettingAction) targetVal else null,
                        targetSettingValueDisplay = if (enableInverterSettingAction) targetDisplay else null,
                        notificationTitle = if (enableNotificationAction) notificationTitleText else null,
                        notificationMessageTemplate = if (enableNotificationAction) notificationTemplateText else null
                    ) ?: AutomationRule(
                        name = ruleName,
                        leftParameter = selectedLeftParam,
                        operator = selectedOperator,
                        rightOperandType = rightOperandType,
                        rightCustomValue = rightCustomValText.toDoubleOrNull() ?: 0.0,
                        rightParameter = if (rightOperandType == RightOperandType.PARAMETER) selectedRightParam else null,
                        enableInverterSettingAction = enableInverterSettingAction,
                        enableNotificationAction = enableNotificationAction,
                        targetSettingId = if (enableInverterSettingAction) selectedField?.id else null,
                        targetSettingName = if (enableInverterSettingAction) selectedField?.name else null,
                        targetSettingValue = if (enableInverterSettingAction) targetVal else null,
                        targetSettingValueDisplay = if (enableInverterSettingAction) targetDisplay else null,
                        notificationTitle = if (enableNotificationAction) notificationTitleText else null,
                        notificationMessageTemplate = if (enableNotificationAction) notificationTemplateText else null
                    )
                    onSave(rule)
                }
            ) {
                Text("Save Automation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
