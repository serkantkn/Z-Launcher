package com.serkantkn.zunelauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.model.LockScreenMode
import com.serkantkn.zunelauncher.ui.screens.lock.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ZuneLockScreenService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsDataStore: SettingsDataStore

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Check if lock screen mode is enabled
                serviceScope.launch {
                    val mode = settingsDataStore.lockScreenMode.firstOrNull() ?: LockScreenMode.DISABLED
                    if (mode != LockScreenMode.DISABLED) {
                        launchLockScreen(context)
                    }
                }
            } else if (intent?.action == Intent.ACTION_SCREEN_ON) {
                serviceScope.launch {
                    val mode = settingsDataStore.lockScreenMode.firstOrNull() ?: LockScreenMode.DISABLED
                    if (mode != LockScreenMode.DISABLED) {
                        launchLockScreen(context)
                    }
                }
            }
        }
    }

    private fun launchLockScreen(context: Context?) {
        context?.let {
            val lockIntent = Intent(it, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            it.startActivity(lockIntent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "zune_lock_screen_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Zune Kilit Ekranı Servisi",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Zune kilit ekranının arka planda çalışmasını sağlar"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Zune Kilit Ekranı")
            .setContentText("Kilit ekranı arka planda aktif")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
