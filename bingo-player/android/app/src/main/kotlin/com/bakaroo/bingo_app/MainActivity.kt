package com.bakaroo.bingo_app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.Intent
import android.net.Uri

class MainActivity : FlutterActivity() {
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
    }
}
