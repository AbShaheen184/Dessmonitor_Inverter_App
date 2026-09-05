package com.dessmonitor.smartess.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alarms",
    indices = [Index(value = ["deviceSn", "apiId"], unique = true)]
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val apiId: String? = null,
    val name: String,
    val ts: String, // event/occur time
    val cts: String? = null, // create time
    val gts: String? = null, // clear time
    val status: Int, // 1 for Active, 0 for Cleared
    val deviceSn: String,
    val descx: String? = null
)
