package com.project.vortex.client.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import com.project.vortex.client.R
import com.project.vortex.client.constructors.AccountManager
import com.project.vortex.client.constructors.NetBound
import com.project.vortex.client.constructors.GameManager
import com.project.vortex.client.model.CaptureModeModel
import com.project.vortex.client.overlay.manager.OverlayManager

import com.project.vortex.relay.VortexRelay
import com.project.vortex.relay.VortexRelaySession
import com.project.vortex.relay.address.VortexAddress
import com.project.vortex.relay.definition.Definitions
import com.project.vortex.relay.listener.AutoCodecPacketListener
import com.project.vortex.relay.listener.BiomeDefinitionListPacketListener
import com.project.vortex.relay.listener.EncryptedLoginPacketListener
import com.project.vortex.relay.listener.GamingPacketHandler
import com.project.vortex.relay.listener.XboxLoginPacketListener
import com.project.vortex.relay.util.XboxIdentityTokenCacheFileSystem
import com.project.vortex.relay.util.captureVortexRelay
import android.app.ActivityManager
import com.project.vortex.client.remlink.TerminalViewModel
import com.project.vortex.client.discord.PresenceStateManager
import java.io.File
import kotlin.concurrent.thread

/**
 * Android Foreground Service to handle the CaptureMode functionality
 */
class Services : Service() {

    companion object {
        const val ACTION_CAPTURE_START = "com.project.vortex.relay.capture.start"
        const val ACTION_CAPTURE_STOP = "com.project.vortex.relay.capture.stop"
        private const val NOTIFICATION_CHANNEL_ID = "vortex_capture_channel"
        private const val NOTIFICATION_ID = 1001

        private val handler = Handler(Looper.getMainLooper())
        private var vortexRelay: VortexRelay? = null
        private var thread: Thread? = null
        var isActive by mutableStateOf(false)
        var RemisOnline by mutableStateOf(false)
        var RemInGame by mutableStateOf(false)
        var isLaunchingMinecraft by mutableStateOf(false)
        var currentServerHostName: String = ""

        fun toggle(context: Context, captureModeModel: CaptureModeModel) {
            if (!isActive) {
                // Ask for "All files access" here (user pressed start) instead of
                // throwing the user into settings when the app launches.
                if (!ensureStorageAccess(context)) return

                val intent = Intent(ACTION_CAPTURE_START)
                intent.setPackage(context.packageName)
                context.startForegroundService(intent)
                TerminalViewModel.addTerminalLog("Connection", "Services Starting...")
                return
            }

            val intent = Intent(ACTION_CAPTURE_STOP)
            intent.setPackage(context.packageName)
            context.startForegroundService(intent)
        }

        /** Returns false when the user still has to grant "All files access". */
        private fun ensureStorageAccess(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
            if (android.os.Environment.isExternalStorageManager()) return true

            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.fromParts("package", context.packageName, null)
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(
                    context,
                    "Allow \"All files access\" for Vortex Client, then press start again.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    "Grant file access in Settings > Apps > Vortex Client > Permissions.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }

        private fun on(context: Context, captureModeModel: CaptureModeModel) {
            if (thread != null) {
                return
            }

            val tokenCacheFile = File(context.cacheDir, "token_cache.json")

            isActive = true
            currentServerHostName = captureModeModel.serverHostName
            PresenceStateManager.setServerInfo(captureModeModel.serverHostName, captureModeModel.serverPort)
            PresenceStateManager.onRelayStarted()

            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val currentActivity = activityManager.appTasks
                .flatMap { it.taskInfo.topActivity?.className?.let { listOf(it) } ?: emptyList() }
                .firstOrNull()
            RemisOnline = currentActivity == "com.project.vortex.client.activity.RemoteLinkActivity"




            val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            handler.post {
                if (isPortrait) {
                    OverlayManager.dismiss()
                } else {
                    OverlayManager.show(context)
                }
            }

            thread = thread(name = "VortexRelayThread") {
                runCatching {
                    GameManager.loadConfig()
                }.exceptionOrNull()?.let {
                    it.printStackTrace()
                    context.toast("Load configuration error: ${it.message}")
                }

                runCatching {
                    Definitions.loadBlockPalette()
                }.exceptionOrNull()?.let {
                    it.printStackTrace()
                    context.toast("Load block palette error: ${it.message}")
                }

                val sessionEncryptor = if (AccountManager.currentAccount == null) {
                    EncryptedLoginPacketListener()
                } else {
                    AccountManager.currentAccount?.let { account ->
                        Log.e("VortexRelay", "Logged in as ${account.remark}")
                        TerminalViewModel.addTerminalLog("Connection", "Logged in as ${account.remark}")
                        TerminalViewModel.addTerminalLog("Help", "Type '!help' in chat to use modules.")
                        XboxLoginPacketListener({ account.refresh() }, account.platform).also {
                            it.tokenCache =
                                XboxIdentityTokenCacheFileSystem(tokenCacheFile, account.remark)
                        }
                    }
                }

                runCatching {
                    vortexRelay = captureVortexRelay(
                        remoteAddress = VortexAddress(
                            captureModeModel.serverHostName,
                            captureModeModel.serverPort
                        )
                    ) {
                        initModules(this)

                        listeners.add(AutoCodecPacketListener(this))
                        listeners.add(BiomeDefinitionListPacketListener(this))
                        sessionEncryptor?.let {
                            it.vortexRelaySession = this
                            listeners.add(it)
                        }
                        listeners.add(GamingPacketHandler(this))
                    }
                }.exceptionOrNull()?.let {
                    it.printStackTrace()
                    context.toast("Start VortexRelay error: ${it.stackTraceToString()}")
                }
            }
        }

        private fun off() {
            thread(name = "VortexRelayThread") {
                GameManager.saveConfig()
                isActive = false
                RemisOnline = false
                currentServerHostName = ""
                PresenceStateManager.onRelayDisconnected()
                vortexRelay?.disconnect()
                thread?.interrupt()
                thread = null

                isLaunchingMinecraft = false

                handler.post {
                    OverlayManager.dismiss()
                }
                TerminalViewModel.addTerminalLog("Connection", "Services Stopped.")
            }
        }

        private fun Context.toast(message: String) {
            handler.post {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        private fun initModules(vortexRelaySession: VortexRelaySession) {
            try {
                val session = NetBound(vortexRelaySession)
                vortexRelaySession.listeners.add(session)

                for (module in GameManager.elements) {
                    try {
                        module.session = session
                    } catch (e: Exception) {
                        Log.e("Services", "Failed to initialize session for module ${module.name}: ${e.message}")
                    }
                }

                TerminalViewModel.addTerminalLog("Connection", "Initializing Modules...")
            } catch (e: Exception) {
                Log.e("Services", "Failed to initialize modules: ${e.message}")
                TerminalViewModel.addTerminalLog("Error", "Failed to initialize modules: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAPTURE_START -> {
                startForeground(NOTIFICATION_ID, createNotification("Vortex capture service is running"))
                val captureModeModel = CaptureModeModel.from(
                    getSharedPreferences("game_settings", Context.MODE_PRIVATE)
                )
                on(applicationContext, captureModeModel)
            }
            ACTION_CAPTURE_STOP -> {
                off()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!isActive) return

        val isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        handler.post {
            if (isPortrait) {
                OverlayManager.dismiss()
            } else {
                OverlayManager.show(this)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Vortex Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used while Vortex capture mode is active"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        TerminalViewModel.addTerminalLog("Connection", "Notification Initialized.")
    }

    private fun createNotification(text: String) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle("Vortex Capture")
        .setContentText(text)
        .setSmallIcon(R.drawable.ic_notification)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            createPendingIntent(ACTION_CAPTURE_STOP)
        )
        .build()

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(action)
        intent.setPackage(packageName)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getService(
            this,
            0,
            intent,
            flags
        )
    }
}