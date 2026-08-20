package com.dessmonitor.smartess.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val time: String,
    val status: Int, // 1 for Active, 0 for Cleared
    val deviceSn: String,
    val descx: String? = null,
    val gts: String? = null
)
