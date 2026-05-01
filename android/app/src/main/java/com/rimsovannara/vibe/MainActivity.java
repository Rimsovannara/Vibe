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

import java.io.InputStream;

public final class MainActivity extends Activity {
    private static final String HOME_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private WebView webView;
    private WebViewAssetLoader assetLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                            track.put("src", "https://appassets.androidplatform.net/device_audio/" + id + ".mp3");
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

    private static class BoundedInputStream extends InputStream {
        private final InputStream in;
        private long bytesRemaining;

        public BoundedInputStream(InputStream in, long size) {
            this.in = in;
            this.bytesRemaining = size;
        }

        @Override
        public int read() throws java.io.IOException {
            if (bytesRemaining <= 0) return -1;
            int b = in.read();
            if (b != -1) bytesRemaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            if (bytesRemaining <= 0) return -1;
            int bytesToRead = (int) Math.min(len, bytesRemaining);
            int bytesRead = in.read(b, off, bytesToRead);
            if (bytesRead != -1) bytesRemaining -= bytesRead;
            return bytesRead;
        }
        
        @Override
        public void close() throws java.io.IOException {
            in.close();
        }
    }

    private final class LocalContentWebViewClient extends WebViewClientCompat {
        private final WebViewAssetLoader assetLoader;

        private LocalContentWebViewClient(WebViewAssetLoader assetLoader) {
            this.assetLoader = assetLoader;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if ("appassets.androidplatform.net".equals(url.getHost()) && url.getPath() != null && url.getPath().startsWith("/device_audio/")) {
                try {
                    String idStr = url.getPath().replaceAll("[^0-9]", "");
                    if (idStr.isEmpty()) return null;
                    long id = Long.parseLong(idStr);
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    
                    android.content.res.AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(contentUri, "r");
                    if (afd == null) return null;
                    
                    long totalSize = afd.getLength();
                    if (totalSize == android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH) {
                        return null;
                    }

                    java.io.FileInputStream fis = afd.createInputStream();
                    String mimeType = getContentResolver().getType(contentUri);
                    if (mimeType == null) mimeType = "audio/mpeg";
                    
                    java.util.Map<String, String> requestHeaders = request.getRequestHeaders();
                    String range = requestHeaders != null ? requestHeaders.get("Range") : null;
                    
                    if (range != null && range.startsWith("bytes=")) {
                        String[] bounds = range.substring(6).split("-");
                        long start = Long.parseLong(bounds[0]);
                        long end = bounds.length > 1 && !bounds[1].isEmpty() ? Long.parseLong(bounds[1]) : totalSize - 1;
                        
                        if (end > totalSize - 1) end = totalSize - 1;
                        long length = end - start + 1;
                        fis.getChannel().position(start);
                        
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Range", "bytes " + start + "-" + end + "/" + totalSize);
                        headers.put("Content-Length", String.valueOf(length));
                        headers.put("Accept-Ranges", "bytes");
                        headers.put("Content-Type", mimeType);
                        headers.put("Access-Control-Allow-Origin", "*");
                        
                        return new WebResourceResponse(mimeType, null, 206, "Partial Content", headers, new BoundedInputStream(fis, length));
                    } else {
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Length", String.valueOf(totalSize));
                        headers.put("Accept-Ranges", "bytes");
                        headers.put("Content-Type", mimeType);
                        headers.put("Access-Control-Allow-Origin", "*");
                        
                        return new WebResourceResponse(mimeType, null, 200, "OK", headers, fis);
                    }
                } catch (Exception e) {
                    Log.e("VibeApp", "Failed to serve media", e);
                    return null;
                }
            }
            return assetLoader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            Uri parsedUrl = Uri.parse(url);
            if ("appassets.androidplatform.net".equals(parsedUrl.getHost()) && parsedUrl.getPath() != null && parsedUrl.getPath().startsWith("/device_audio/")) {
                 return null;
            }
            return assetLoader.shouldInterceptRequest(parsedUrl);
        }
    }
}
