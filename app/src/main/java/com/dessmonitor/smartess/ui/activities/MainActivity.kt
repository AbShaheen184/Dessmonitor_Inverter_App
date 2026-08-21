package com.dessmonitor.smartess.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import com.dessmonitor.smartess.ui.screens.LoginScreen
import com.dessmonitor.smartess.ui.screens.MainScreen
import com.dessmonitor.smartess.ui.theme.SmartESSTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val repository: DeviceRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            SmartESSTheme {
                val isLoggedIn by repository.isLoggedIn.observeAsState(false)

                if (isLoggedIn) {
                    MainScreen(repository)
                } else {
                    LoginScreen(
                        repository = repository,
                        onLoginSuccess = {
                            // The repository updates isLoggedIn LiveData, 
                            // which will trigger recomposition to MainScreen
                        }
                    )
                }
            }
        }
    }
}
