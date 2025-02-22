package com.saggitt.omega.appusage

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.Settings
import android.bluetooth.BluetoothAdapter
import android.util.Log
import java.util.Calendar

class UsageDataCollector(private val context: Context) {

    fun collectUsageData(packageName: String): AppUsage {

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY) // 24 hour format

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS or AudioManager.GET_DEVICES_OUTPUTS)

        val isAudioDeviceConnected = audioDevices.any { device ->
            device.type in setOf(
                AudioDeviceInfo.TYPE_AUX_LINE,
                AudioDeviceInfo.TYPE_BLE_BROADCAST,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET
            )
        }
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val status: Int? = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val isWifiConnected = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isMobileDataConnected = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothConnected = bluetoothAdapter != null && bluetoothAdapter.isEnabled && bluetoothAdapter.bondedDevices.isNotEmpty()

        val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)

        val appUsage = AppUsage(
            0,
            hour,
            packageName,
            isAudioDeviceConnected,
            isCharging,
            isWifiConnected,
            isMobileDataConnected,
            isBluetoothConnected,
            brightness
        )
        Log.d("UsageDataCollector", "usage:"+listOf(appUsage.id, appUsage.hourOfDay, appUsage.packageName, appUsage.isAudioDeviceConnected, appUsage.isCharging, appUsage.isWifiConnected, appUsage.isMobileDataConnected, appUsage.isBluetoothConnected, appUsage.brightness).joinToString(","))

        return appUsage
    }
}