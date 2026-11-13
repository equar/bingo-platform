package com.bakaroo.bingo_app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.Intent
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

class MainActivity : FlutterActivity() {
    private var notificationPermissionResult: MethodChannel.Result? = null
    private val toneHandler = Handler(Looper.getMainLooper())
    private var activeTone: ToneGenerator? = null
    private var toneRelease: Runnable? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "bingo_player/external_links")
            .setMethodCallHandler { call, result ->
                if (call.method != "open") {
                    result.notImplemented()
                    return@setMethodCallHandler
                }
                val uri = (call.arguments as? String)?.let(Uri::parse)
                if (uri == null || uri.scheme !in listOf("https", "http")) {
                    result.error("invalid_url", "Only HTTP and HTTPS links can be opened.", null)
                    return@setMethodCallHandler
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    result.success(null)
                } catch (_: Exception) {
                    result.error("open_failed", "No app can open this link.", null)
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "bingo_player/notifications")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "playSound" -> {
                        val win = (call.arguments as? Map<*, *>)?.get("win") == true
                        playGameTone(win)
                        result.success(null)
                    }
                    "stopSound" -> {
                        stopGameTone()
                        result.success(null)
                    }
                    "requestPermission" -> {
                        if (Build.VERSION.SDK_INT < 33 ||
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                            result.success(true)
                        } else {
                            notificationPermissionResult = result
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
                        }
                    }
                    "show" -> {
                        val args = call.arguments as? Map<*, *>
                        val title = args?.get("title")?.toString().orEmpty()
                        val body = args?.get("body")?.toString().orEmpty()
                        val id = (args?.get("id") as? Number)?.toInt() ?: 7002
                        if (title.isBlank() || body.isBlank()) {
                            result.error("invalid_notification", "Notification title and body are required.", null)
                        } else {
                            result.success(showPlayerNotification(title, body, id))
                        }
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7001) {
            notificationPermissionResult?.success(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            notificationPermissionResult = null
        }
    }

    private fun showPlayerNotification(title: String, body: String, id: Int): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = "bingo_game_alerts"
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Bingo game alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        val openApp = PendingIntent.getActivity(
            this,
            id,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
        return true
    }

    private fun playGameTone(win: Boolean) {
        stopGameTone()
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 72)
        activeTone = tone
        tone.startTone(if (win) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, if (win) 320 else 150)
        val release = Runnable {
            if (activeTone === tone) {
                tone.release()
                activeTone = null
                toneRelease = null
            }
        }
        toneRelease = release
        toneHandler.postDelayed(release, if (win) 360 else 190)
    }

    private fun stopGameTone() {
        toneRelease?.let(toneHandler::removeCallbacks)
        toneRelease = null
        activeTone?.let { tone ->
            tone.stopTone()
            tone.release()
        }
        activeTone = null
    }

    override fun onDestroy() {
        stopGameTone()
        super.onDestroy()
    }
}
