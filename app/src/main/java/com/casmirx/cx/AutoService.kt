package com.casmirx.cx

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*

class AutoService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var stretched = false

    private val ignore = setOf(
        "com.android.systemui", "android", "com.samsung.android.app.cocktailbarservice",
        "com.sec.android.inputmethod", "com.google.android.inputmethod.latin"
    )

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESET) {
            Prefs.setAuto(this, false)
            scope.launch { try { Shell.reset() } catch (_: Throwable) {}; stretched = false; stopSelf() }
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (loop == null) loop = scope.launch { watch() }
        return START_STICKY
    }

    private fun focusPackage(): String? {
        val out = Shell.run("dumpsys window | grep mCurrentFocus")
        return Regex("u0 ([\\w.]+)/").find(out)?.groupValues?.get(1)
    }

    private suspend fun watch() {
        var leave = 0
        while (currentCoroutineContext().isActive) {
            try {
                val pkg = focusPackage()
                val games = Prefs.games(this)
                if (pkg != null && pkg in games) {
                    leave = 0
                    if (!stretched) { Shell.applyRatio(Prefs.ratio(this)); stretched = true }
                } else if (pkg != null && pkg !in ignore && stretched) {
                    // other app in front: need two checks in a row before restoring
                    if (++leave >= 2) { Shell.reset(); stretched = false; leave = 0 }
                }
            } catch (_: Throwable) { /* Shizuku not ready, retry */ }
            delay(1500)
        }
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "CX auto mode", NotificationManager.IMPORTANCE_LOW))
        val reset = PendingIntent.getService(
            this, 0, Intent(this, AutoService::class.java).setAction(ACTION_RESET),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH)
            .setContentTitle("CX auto mode on")
            .setContentText("Stretch applies when your game opens")
            .setSmallIcon(android.R.drawable.ic_menu_crop)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Reset & stop", reset).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        loop?.cancel()
        if (stretched) { try { Shell.reset() } catch (_: Throwable) {} }
        super.onDestroy()
    }

    companion object {
        const val CH = "cx_auto"
        const val ACTION_RESET = "com.casmirx.cx.RESET"
        fun start(c: Context) = c.startForegroundService(Intent(c, AutoService::class.java))
        fun stop(c: Context) = c.stopService(Intent(c, AutoService::class.java))
    }
}
