package com.rimsovannara.vibe

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class MainActivity : Activity() {

    private var webView: WebView? = null
    private var assetLoader: WebViewAssetLoader? = null
    private var audioServer: AudioServer? = null
    private var audioServerPort = 8080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        audioServer = AudioServer(0, contentResolver)
        try {
            audioServer?.start()
            audioServerPort = audioServer?.listeningPort ?: 8080
        } catch (e: IOException) {
            Log.e("VibeApp", "Could not start audio server", e)
        }

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            configureWebView(this)
        }
        setContentView(webView)

        val filter = IntentFilter().apply {
            addAction(MediaService.ACTION_PLAY)
            addAction(MediaService.ACTION_PAUSE)
            addAction(MediaService.ACTION_NEXT)
            addAction(MediaService.ACTION_PREV)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(mediaReceiver, filter)
        }

        if (savedInstanceState != null) {
            webView?.restoreState(savedInstanceState)
        } else {
            webView?.loadUrl(HOME_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.saveState(outState)
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        audioServer?.stop()

        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null

        try {
            unregisterReceiver(mediaReceiver)
        } catch (ignored: Exception) {}

        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(view: WebView) {
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        }

        view.addJavascriptInterface(VibeAppInterface(), "VibeApp")
        view.webChromeClient = WebChromeClient()
        view.webViewClient = LocalContentWebViewClient(assetLoader!!)
    }

    private inner class VibeAppInterface {
        @JavascriptInterface
        fun requestDeviceAudio() {
            runOnUiThread {
                val permissionsToRequest = mutableListOf<String>()
                val mediaPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                if (checkSelfPermission(mediaPerm) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(mediaPerm)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (permissionsToRequest.isNotEmpty()) {
                    requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
                } else {
                    syncAudio()
                }
            }
        }

        @JavascriptInterface
        fun updateMetadata(title: String, artist: String) {
            val intent = Intent(this@MainActivity, MediaService::class.java).apply {
                action = MediaService.ACTION_UPDATE_METADATA
                putExtra("title", title)
                putExtra("artist", artist)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        @JavascriptInterface
        fun updatePlaybackState(isPlaying: Boolean) {
            val intent = Intent(this@MainActivity, MediaService::class.java).apply {
                action = MediaService.ACTION_UPDATE_STATE
                putExtra("isPlaying", isPlaying)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val view = webView ?: return
            val action = intent?.action ?: return
            
            val jsAction = when (action) {
                MediaService.ACTION_PLAY, MediaService.ACTION_PAUSE -> "window.VibePlayerInstance.togglePlayback();"
                MediaService.ACTION_NEXT -> "window.VibePlayerInstance.changeTrack(1);"
                MediaService.ACTION_PREV -> "window.VibePlayerInstance.changeTrack(-1);"
                else -> null
            }
            jsAction?.let { view.evaluateJavascript(it, null) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val mediaPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            val mediaGranted = if (checkSelfPermission(mediaPerm) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                permissions.indices.any { i ->
                    (permissions[i] == Manifest.permission.READ_MEDIA_AUDIO || 
                     permissions[i] == Manifest.permission.READ_EXTERNAL_STORAGE) && 
                    grantResults[i] == PackageManager.PERMISSION_GRANTED
                }
            }

            if (mediaGranted) {
                syncAudio()
            } else {
                Log.w("VibeApp", "Storage permission denied")
            }
        }
    }

    private fun syncAudio() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonTracks = JSONArray()
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.IS_MUSIC,
                    MediaStore.Audio.Media.DATA
                )

                var selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                        "${MediaStore.Audio.Media.IS_ALARM} == 0 AND " +
                        "${MediaStore.Audio.Media.IS_NOTIFICATION} == 0 AND " +
                        "${MediaStore.Audio.Media.IS_RINGTONE} == 0 AND " +
                        "${MediaStore.Audio.Media.IS_PODCAST} == 0 AND " +
                        "${MediaStore.Audio.Media.DATA} NOT LIKE '%/Android/%' AND " +
                        "${MediaStore.Audio.Media.DURATION} > 30000"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    selection += " AND ${MediaStore.MediaColumns.IS_TRASHED} == 0"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    selection += " AND ${MediaStore.MediaColumns.IS_PENDING} == 0"
                }

                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val dataPath = cursor.getString(dataCol)
                        if (dataPath != null) {
                            val f = File(dataPath)
                            if (!f.exists() || f.length() == 0L) continue
                        }

                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol)
                        var artist = cursor.getString(artistCol)
                        if (artist == null || artist == "<unknown>") artist = "Unknown Artist"

                        val track = JSONObject().apply {
                            put("title", title)
                            put("artist", artist)
                            put("src", "http://127.0.0.1:$audioServerPort/audio/$id")
                            put("mood", "From your device")
                            put("note", "Synced automatically by the Vibe Android app.")
                            put("accent", "#8a5bff")
                        }
                        jsonTracks.put(track)
                    }
                }

                val jsonString = jsonTracks.toString()
                val b64 = Base64.encodeToString(jsonString.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val jsCode = "if(window.onAndroidAudioSync) { " +
                        "window.onAndroidAudioSync(JSON.parse(decodeURIComponent(escape(window.atob('$b64'))))); " +
                        "}"

                withContext(Dispatchers.Main) {
                    webView?.evaluateJavascript(jsCode, null)
                }
            } catch (e: Exception) {
                Log.e("VibeApp", "Failed to sync audio", e)
            }
        }
    }

    private class AudioServer(port: Int, private val resolver: ContentResolver) : NanoHTTPD("127.0.0.1", port) {
        
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            
            if (uri.startsWith("/art/")) {
                return serveArt(uri)
            }
            
            if (!uri.startsWith("/audio/")) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }

            val idStr = uri.substringAfterLast("/")
            if (idStr.isEmpty()) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad Request")

            try {
                val id = idStr.toLong()
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                val pfd = resolver.openFileDescriptor(contentUri, "r") ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                val fis = FileInputStream(pfd.fileDescriptor)
                var fileLength = pfd.statSize

                if (fileLength == android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH || fileLength <= 0) {
                    resolver.query(contentUri, arrayOf(MediaStore.Audio.Media.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            fileLength = c.getLong(0)
                        }
                    }
                }

                if (fileLength <= 0) {
                    fis.close()
                    pfd.close()
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Unknown File Length")
                }

                val mimeType = resolver.getType(contentUri) ?: "audio/mpeg"
                val rangeHeader = session.headers["range"]
                var start = 0L
                var end = fileLength - 1

                val res: Response
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    try {
                        val parts = rangeHeader.substring(6).split("-")
                        start = parts[0].toLong()
                        if (parts.size > 1 && parts[1].isNotEmpty()) {
                            end = parts[1].toLong()
                        }
                    } catch (ignored: NumberFormatException) {}

                    if (end > fileLength - 1) end = fileLength - 1
                    val contentLength = end - start + 1

                    if (start > 0) {
                        try {
                            fis.channel.position(start)
                        } catch (e: Exception) {
                            fis.skip(start)
                        }
                    }

                    res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, fis, contentLength)
                    res.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                    res.addHeader("Content-Length", contentLength.toString())
                } else {
                    res = newFixedLengthResponse(Response.Status.OK, mimeType, fis, fileLength)
                    res.addHeader("Content-Length", fileLength.toString())
                }

                res.addHeader("Accept-Ranges", "bytes")
                res.addHeader("Access-Control-Allow-Origin", "*")

                // AutoCloseInputStream does not reliably close PFD sometimes, but NanoHTTPD handles streams.
                return res
            } catch (e: Exception) {
                Log.e("VibeApp", "AudioServer error", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
            }
        }

        private fun serveArt(uri: String): Response {
            val idStr = uri.substringAfterLast("/")
            if (idStr.isEmpty()) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad Request")

            try {
                val id = idStr.toLong()
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val bitmap = resolver.loadThumbnail(contentUri, Size(512, 512), null)
                        val bos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                        val bytes = bos.toByteArray()
                        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
                            addHeader("Cache-Control", "max-age=86400")
                            addHeader("Access-Control-Allow-Origin", "*")
                        }
                    } catch (ignored: Exception) {}
                }

                val mmr = MediaMetadataRetriever()
                val pfd = resolver.openFileDescriptor(contentUri, "r")
                if (pfd != null) {
                    mmr.setDataSource(pfd.fileDescriptor)
                    val art = mmr.embeddedPicture
                    mmr.release()
                    pfd.close()

                    if (art != null) {
                        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(art), art.size.toLong()).apply {
                            addHeader("Cache-Control", "max-age=86400")
                            addHeader("Access-Control-Allow-Origin", "*")
                        }
                    }
                }
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No Art")
            } catch (e: Exception) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
            }
        }
    }

    private inner class LocalContentWebViewClient(private val assetLoader: WebViewAssetLoader) : WebViewClientCompat() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            return assetLoader.shouldInterceptRequest(request.url)
        }

        @Suppress("DEPRECATION")
        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            return assetLoader.shouldInterceptRequest(Uri.parse(url))
        }
    }

    companion object {
        private const val HOME_URL = "https://appassets.androidplatform.net/assets/www/index.html"
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
