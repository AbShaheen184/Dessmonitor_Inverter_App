package com.dessmonitor.smartess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dessmonitor.smartess.data.models.AutomationRule
import com.dessmonitor.smartess.data.models.ComparisonOperator
import com.dessmonitor.smartess.data.models.DeviceInfo
import com.dessmonitor.smartess.data.models.RightOperandType
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsDialog(
    repository: DeviceRepository,
    activeDevice: DeviceInfo?,
    onDismiss: () -> Unit
) {
    val rules by repository.automationRules.observeAsState(emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var availableFields by remember { mutableStateOf<List<ControlField>>(emptyList()) }
    var isLoadingFields by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }

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
                availableFields = processFields(json, activeDevice)
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
                    statusMessage = "Condition met for '${rule.name}'. Triggering setting: ${rule.targetSettingName} = ${rule.targetSettingValueDisplay}..."
                    repository.setControlValue(activeDevice, rule.targetSettingId, rule.targetSettingValue)
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
            Box(modifier = Modifier.heightIn(max = 480.dp)) {
                Column {
                    if (!statusMessage.isNullOrEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                statusMessage!!,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
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
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(rule.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                            val rightText = if (rule.rightOperandType == RightOperandType.CUSTOM_VALUE) rule.rightCustomValue.toString() else rule.rightParameter ?: ""
                                            Text(
                                                "IF ${rule.leftParameter} ${rule.operator.symbol} $rightText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "THEN SET ${rule.targetSettingName} → ${rule.targetSettingValueDisplay}",
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

    if (showCreateDialog) {
        CreateAutomationRuleDialog(
            telemetryTitles = telemetryTitles,
            availableFields = availableFields,
            onDismiss = { showCreateDialog = false },
            onSave = { newRule ->
                repository.setAutomationRules(rules + newRule)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAutomationRuleDialog(
    telemetryTitles: List<String>,
    availableFields: List<ControlField>,
    onDismiss: () -> Unit,
    onSave: (AutomationRule) -> Unit
) {
    var ruleName by remember { mutableStateOf("") }
    var selectedLeftParam by remember { mutableStateOf(telemetryTitles.firstOrNull() ?: "PV Power") }
    var selectedOperator by remember { mutableStateOf(ComparisonOperator.GREATER_THAN_OR_EQUAL) }
    var rightOperandType by remember { mutableStateOf(RightOperandType.CUSTOM_VALUE) }
    var rightCustomValText by remember { mutableStateOf("1000") }
    var selectedRightParam by remember { mutableStateOf(telemetryTitles.firstOrNull() ?: "Output Power") }

    var selectedField by remember { mutableStateOf(availableFields.firstOrNull()) }
    var selectedOptionKey by remember { mutableStateOf(selectedField?.options?.keys?.firstOrNull() ?: "") }
    var selectedOptionDisplay by remember { mutableStateOf(selectedField?.options?.get(selectedOptionKey) ?: "") }
    var customSettingTextVal by remember { mutableStateOf("") }

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
                                ComparisonOperator.values().forEach { op ->
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
                        Text("THEN (Inverter Action)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
            }
        },
        confirmButton = {
            Button(
                enabled = ruleName.isNotBlank() && selectedField != null,
                onClick = {
                    val targetVal = if (selectedField!!.options.isNotEmpty()) selectedOptionKey else customSettingTextVal
                    val targetDisplay = if (selectedField!!.options.isNotEmpty()) selectedOptionDisplay else customSettingTextVal

                    val rule = AutomationRule(
                        name = ruleName,
                        leftParameter = selectedLeftParam,
                        operator = selectedOperator,
                        rightOperandType = rightOperandType,
                        rightCustomValue = rightCustomValText.toDoubleOrNull() ?: 0.0,
                        rightParameter = if (rightOperandType == RightOperandType.PARAMETER) selectedRightParam else null,
                        targetSettingId = selectedField!!.id,
                        targetSettingName = selectedField!!.name,
                        targetSettingValue = targetVal,
                        targetSettingValueDisplay = targetDisplay
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
