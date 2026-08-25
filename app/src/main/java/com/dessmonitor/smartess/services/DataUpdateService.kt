package com.dessmonitor.smartess.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.minutes

class DataUpdateService : Service() {
    private val repository: DeviceRepository by inject()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "automation_service_channel"
        private const val TAG = "DataUpdateService"

        fun start(context: Context) {
            val intent = Intent(context, DataUpdateService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DataUpdateService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting in foreground")
        startForeground(NOTIFICATION_ID, createNotification())
        
        job?.cancel()
        job = serviceScope.launch {
            while (isActive) {
                try {
                    Log.d(TAG, "Evaluating automations...")
                    repository.evaluateAutomations(applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in automation loop", e)
                }
                delay(5.minutes)
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        job?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartESS Automation Active")
            .setContentText("Monitoring inverter parameters in the background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Automation Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps automations running in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
