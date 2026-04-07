package com.example.toratele;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.ui.PlayerView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private FrameLayout rootLayout;
    private WebView webView;
    private PlayerView playerView;
    private ExoPlayer player;
    private ServerSocket serverSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        rootLayout = new FrameLayout(this);
        
        // 1. WebViewの初期化 (待機画面・ハイブリッド用)
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.6099.144 Mobile Safari/537.36");

        // 2. ExoPlayer (PlayerView) の初期化 (ネイティブ再生用)
        playerView = new PlayerView(this);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setVisibility(View.GONE);
        playerView.setUseController(true); // リモコン操作のためコントローラー有効

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                error.printStackTrace();
                runOnUiThread(() -> {
                    playerView.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    showErrorScreen("再生エラー: " + error.getMessage() + "\n(認証切れ、またはDRM非対応の可能性)");
                });
            }
        });

        rootLayout.addView(webView);
        rootLayout.addView(playerView);
        setContentView(rootLayout);

        showReadyScreen();
        startServer();
    }

    private void showReadyScreen() {
        runOnUiThread(() -> {
            String html = "<html><body style='background-color:#111;color:white;display:flex;justify-content:center;align-items:center;height:100vh;flex-direction:column;font-family:sans-serif;margin:0;'>" +
                    "<h1 style='color:#ff0;font-size:3rem;margin-bottom:10px;'>&#x1F405; ToraTele READY</h1>" +
                    "<p style='font-size:1.5rem;'>ネイティブ・プレイヤー待機中 (ExoPlayer)</p>" +
                    "</body></html>";
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            webView.setVisibility(View.VISIBLE);
            playerView.setVisibility(View.GONE);
        });
    }

    private void showErrorScreen(String message) {
        String html = "<html><body style='background-color:#500;color:white;display:flex;justify-content:center;align-items:center;height:100vh;flex-direction:column;font-family:sans-serif;margin:0;'>" +
                "<h1 style='color:#fff;font-size:2rem;margin-bottom:10px;'>&#x274C; Error</h1>" +
                "<p style='font-size:1.2rem;text-align:center;padding:20px;'>" + message + "</p>" +
                "<p style='font-size:1rem;color:#ccc;'>スマホから再度送信してください</p>" +
                "</body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(8080);
                while (true) {
                    Socket socket = serverSocket.accept();
                    handleRequest(socket);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void handleRequest(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line = reader.readLine();
            if (line != null && line.contains("GET")) {
                String path = line.split(" ")[1];
                String targetUrl = null, cookieData = null;
                
                if (path.contains("?")) {
                    String query = path.substring(path.indexOf("?") + 1);
                    for (String param : query.split("&")) {
                        if (param.startsWith("url=")) targetUrl = URLDecoder.decode(param.substring(4), "UTF-8");
                        else if (param.startsWith("cookie=")) cookieData = URLDecoder.decode(param.substring(7), "UTF-8");
                    }
                }
                
                final String finalUrl = targetUrl;
                final String finalCookie = cookieData;

                if (finalUrl != null) {
                    runOnUiThread(() -> {
                        if (finalUrl.contains(".m3u8")) {
                            playNativeStream(finalUrl, finalCookie);
                        } else {
                            // URLがストリームでない場合はWebViewで開く
                            webView.setVisibility(View.VISIBLE);
                            playerView.setVisibility(View.GONE);
                            webView.loadUrl(finalUrl);
                        }
                    });
                }
            }
            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\n\r\nOK".getBytes());
            socket.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void playNativeStream(String url, String cookie) {
        webView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        
        // Androidスマホとして偽装
        String ua = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.6099.144 Mobile Safari/537.36";
        
        // 通信レイヤーでのヘッダー強制注入 (AES鍵、セグメントも網羅)
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000)
                .setAllowCrossProtocolRedirects(true);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://movie.hanshintigers.jp/");
        if (cookie != null && !cookie.isEmpty()) headers.put("Cookie", cookie);
        dataSourceFactory.setDefaultRequestProperties(headers);

        HlsMediaSource mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(url)));

        player.setMediaSource(mediaSource);
        player.prepare();
        player.play();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) player.release();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception e) {}
    }
}
