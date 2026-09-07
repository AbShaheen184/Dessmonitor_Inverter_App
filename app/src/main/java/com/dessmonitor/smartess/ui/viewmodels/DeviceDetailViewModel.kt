package com.dessmonitor.smartess.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.dessmonitor.smartess.data.models.DeviceInfo
import com.dessmonitor.smartess.data.repositories.DeviceRepository
import kotlinx.coroutines.launch

class DeviceDetailViewModel(
    private val repository: DeviceRepository
) : ViewModel() {
    
    private val _device = MutableLiveData<DeviceInfo?>(null)
    val device: LiveData<DeviceInfo?> = _device
    
    fun loadDevice(serialNumber: String) {
        viewModelScope.launch {
            try {
                // In a real app, you'd have a method to get a specific device by serial number
                // For now, we'll use the repository's current devices list
                repository.loadDevices().onSuccess { allDevices ->
                    val foundDevice = allDevices.find { it.serialNumber == serialNumber }
                    _device.postValue(foundDevice)
                }.onFailure { error ->
                    Log.e("DeviceDetailViewModel", "Failed to load device", error)
                }
            } catch (e: Exception) {
                Log.e("DeviceDetailViewModel", "Exception loading device", e)
            }
        }
    }
}