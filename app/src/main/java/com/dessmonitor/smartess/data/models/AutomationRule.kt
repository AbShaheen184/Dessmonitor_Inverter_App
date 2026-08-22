package com.dessmonitor.smartess.data.models

import java.util.UUID

enum class ComparisonOperator(val symbol: String, val label: String) {
    EQUAL("==", "Equals"),
    LESS_THAN_OR_EQUAL("<=", "Less than or equal to"),
    GREATER_THAN_OR_EQUAL(">=", "Greater than or equal to"),
    LESS_THAN("<", "Less than"),
    GREATER_THAN(">", "Greater than")
}

enum class RightOperandType {
    CUSTOM_VALUE,
    PARAMETER
}

data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isEnabled: Boolean = true,
    
    // Left side parameter (e.g. "Battery Voltage", "SOC", "PV Power")
    val leftParameter: String,
    
    // Operator
    val operator: ComparisonOperator,
    
    // Right side (Custom number vs another parameter title)
    val rightOperandType: RightOperandType = RightOperandType.CUSTOM_VALUE,
    val rightCustomValue: Double = 0.0,
    val rightParameter: String? = null,
    
    // Action (Inverter setting to change)
    val targetSettingId: String,          // e.g., "01"
    val targetSettingName: String,        // e.g., "Output source priority"
    val targetSettingValue: String,       // e.g., "0" (SBU), "1" (SUB)
    val targetSettingValueDisplay: String // e.g., "SBU Priority"
)
