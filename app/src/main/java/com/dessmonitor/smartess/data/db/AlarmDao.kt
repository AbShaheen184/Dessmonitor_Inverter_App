package com.dessmonitor.smartess.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms WHERE deviceSn = :deviceSn ORDER BY ts DESC")
    fun getAlarmsByDevice(deviceSn: String): Flow<List<AlarmEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarms(alarms: List<AlarmEntity>)

    @Query("DELETE FROM alarms WHERE deviceSn = :deviceSn")
    suspend fun clearAlarms(deviceSn: String)
}
