package com.dessmonitor.smartess

import android.app.Application
import androidx.room.Room
import com.dessmonitor.smartess.data.db.AppDatabase
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.getKoin

class SmartEssApplication : Application() {
    lateinit var deviceRepository: DeviceRepository
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Koin DI framework
        startKoin {
            androidContext(this@SmartEssApplication)
            modules(
                module {
                    single { 
                        Room.databaseBuilder(get(), AppDatabase::class.java, "smartess_db")
                            .fallbackToDestructiveMigration()
                            .build()
                    }
                    single { get<AppDatabase>().alarmDao() }
                    single { DeviceRepository(get(), get()) }
                }
            )
        }
        
        deviceRepository = getKoin().get()
    }
}
