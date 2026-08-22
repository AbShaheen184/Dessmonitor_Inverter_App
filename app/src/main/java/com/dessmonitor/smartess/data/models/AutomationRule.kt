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
    
    // Action Toggles (Both can be active at the same time)
    val enableInverterSettingAction: Boolean = true,
    val enableNotificationAction: Boolean = false,
    
    // Inverter Setting Action
    val targetSettingId: String? = null,          // e.g., "01"
    val targetSettingName: String? = null,        // e.g., "Output source priority"
    val targetSettingValue: String? = null,       // e.g., "0" (SBU), "1" (SUB)
    val targetSettingValueDisplay: String? = null,// e.g., "SBU Priority"
    
    // Mobile Notification Action
    val notificationTitle: String? = "Automation Alert",
    val notificationMessageTemplate: String? = "The battery is {SOC} and voltage is {Battery Voltage}"
)
