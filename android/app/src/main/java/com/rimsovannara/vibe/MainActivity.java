package com.rimsovannara.vibe;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import fi.iki.elide.nanohttpd.NanoHTTPD;

import java.io.InputStream;

public final class MainActivity extends Activity {
    private static final String HOME_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int AUDIO_SERVER_PORT = 8080;

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private AudioServer audioServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        audioServer = new AudioServer(AUDIO_SERVER_PORT, getContentResolver());
        try {
            audioServer.start();
        } catch (java.io.IOException e) {
            Log.e("VibeApp", "Could not start audio server", e);
        }

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        webView.setBackgroundColor(Color.BLACK);
        configureWebView(webView);
        setContentView(webView);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (audioServer != null) {
            audioServer.stop();
        }

        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    private void configureWebView(WebView view) {
        assetLoader = new WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        }

        view.addJavascriptInterface(new VibeAppInterface(), "VibeApp");

        view.setWebChromeClient(new WebChromeClient());
        view.setWebViewClient(new LocalContentWebViewClient(assetLoader));
    }

    private final class VibeAppInterface {
        @JavascriptInterface
        public void requestDeviceAudio() {
            runOnUiThread(() -> {
                String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                    Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
                
                if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                    syncAudio();
                } else {
                    requestPermissions(new String[]{permission}, PERMISSION_REQUEST_CODE);
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                syncAudio();
            } else {
                Log.w("VibeApp", "Storage permission denied");
            }
        }
    }

    private void syncAudio() {
        new Thread(() -> {
            try {
                JSONArray jsonTracks = new JSONArray();
                String[] projection = new String[] {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.IS_MUSIC
                };
                
                String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
                try (Cursor cursor = getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        null,
                        MediaStore.Audio.Media.TITLE + " ASC"
                )) {
                    if (cursor != null) {
                        int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                        int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                        int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                        
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idCol);
                            String title = cursor.getString(titleCol);
                            String artist = cursor.getString(artistCol);
                            if (artist == null || artist.equals("<unknown>")) artist = "Unknown Artist";
                            
                            JSONObject track = new JSONObject();
                            track.put("title", title);
                            track.put("artist", artist);
                            track.put("src", "http://127.0.0.1:" + AUDIO_SERVER_PORT + "/audio/" + id);
                            track.put("mood", "From your device");
                            track.put("note", "Synced automatically by the Vibe Android app.");
                            track.put("accent", "#8a5bff");
                            
                            jsonTracks.put(track);
                        }
                    }
                }
                
                String jsonString = jsonTracks.toString();
                
                final String jsCode = "if(window.onAndroidAudioSync) { " +
                        "window.onAndroidAudioSync(" + jsonString + "); " +
                        "}";
                
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript(jsCode, null);
                    }
                });
            } catch (Exception e) {
                Log.e("VibeApp", "Failed to sync audio", e);
            }
        }).start();
    }

    private static class AudioServer extends NanoHTTPD {
        private final android.content.ContentResolver resolver;

        public AudioServer(int port, android.content.ContentResolver resolver) {
            super(port);
            this.resolver = resolver;
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if (!uri.startsWith("/audio/")) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
            }
            
            String idStr = uri.substring(uri.lastIndexOf("/") + 1);
            if (idStr.isEmpty()) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad Request");

            try {
                long id = Long.parseLong(idStr);
                Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                android.os.ParcelFileDescriptor pfd = resolver.openFileDescriptor(contentUri, "r");
                if (pfd == null) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");

                java.io.FileInputStream fis = new java.io.FileInputStream(pfd.getFileDescriptor());
                long fileLength = pfd.getStatSize();
                
                // Fallback for UNKNOWN_LENGTH (Telegram files, etc.)
                if (fileLength == android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH || fileLength <= 0) {
                     try (Cursor c = resolver.query(contentUri, new String[]{MediaStore.Audio.Media.SIZE}, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            fileLength = c.getLong(0);
                        }
                    }
                }
                
                if (fileLength <= 0) {
                     fis.close();
                     pfd.close();
                     return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Unknown File Length");
                }

                String mimeType = resolver.getType(contentUri);
                if (mimeType == null) mimeType = "audio/mpeg";

                String rangeHeader = session.getHeaders().get("range");
                long start = 0;
                long end = fileLength - 1;

                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    String[] parts = rangeHeader.substring(6).split("-");
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                }

                if (end > fileLength - 1) end = fileLength - 1;
                long contentLength = end - start + 1;

                if (start > 0) {
                    fis.getChannel().position(start);
                }

                Response res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, fis, contentLength);
                res.addHeader("Accept-Ranges", "bytes");
                res.addHeader("Content-Length", String.valueOf(contentLength));
                res.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                res.addHeader("Access-Control-Allow-Origin", "*");

                return res;
            } catch (Exception e) {
                Log.e("VibeApp", "AudioServer error", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }
    }

    private final class LocalContentWebViewClient extends WebViewClientCompat {
        private final WebViewAssetLoader assetLoader;

        private LocalContentWebViewClient(WebViewAssetLoader assetLoader) {
            this.assetLoader = assetLoader;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return assetLoader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return assetLoader.shouldInterceptRequest(Uri.parse(url));
        }
    }
}
