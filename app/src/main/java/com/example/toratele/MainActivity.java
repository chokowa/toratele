package com.example.toratele;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
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
        
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        
        playerView = new PlayerView(this);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setVisibility(View.GONE);
        playerView.setUseController(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                runOnUiThread(() -> showInfoScreen("&#x26A0; 再生エラー\n" + error.getErrorCodeName(), "#500"));
            }
        });

        rootLayout.addView(webView);
        rootLayout.addView(playerView);
        setContentView(rootLayout);

        showInfoScreen("&#x1F405; ToraTele READY\nスマホで動画を再生してから、送信ボタンを押してください", "#111");
        startServer();
    }

    private void showInfoScreen(String message, String bgColor) {
        runOnUiThread(() -> {
            String html = "<html><body style='background-color:" + bgColor + ";color:white;display:flex;justify-content:center;align-items:center;height:100vh;flex-direction:column;font-family:sans-serif;margin:0;padding:20px;text-align:center;'>" +
                    "<h1 style='color:#ff0;font-size:2rem;'>" + message.replace("\n", "<br>") + "</h1>" +
                    "</body></html>";
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            webView.setVisibility(View.VISIBLE);
            playerView.setVisibility(View.GONE);
        });
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
                Map<String, String> params = new HashMap<>();
                if (path.contains("?")) {
                    String query = path.substring(path.indexOf("?") + 1);
                    for (String p : query.split("&")) {
                        String[] pair = p.split("=", 2);
                        if (pair.length == 2) params.put(pair[0], URLDecoder.decode(pair[1], "UTF-8"));
                    }
                }
                
                String targetUrl = params.get("url");
                String cookieData = params.get("cookie");

                if (targetUrl != null) {
                    runOnUiThread(() -> {
                        if (targetUrl.contains(".m3u8")) {
                            playNativeStream(targetUrl, cookieData);
                        } else {
                            // ストリームURLが見つからなかった場合
                            showInfoScreen("&#x26A0; ストリームURLが見つかりません\nスマホで動画を一時停止・再開してから、もう一度送信してください", "#440");
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
        
        String ua = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.6099.144 Mobile Safari/537.36";
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setAllowCrossProtocolRedirects(true);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://movie.hanshintigers.jp/");
        if (cookie != null) headers.put("Cookie", cookie);
        dataSourceFactory.setDefaultRequestProperties(headers);

        player.setMediaSource(new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(url))));
        player.prepare();
        player.play();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) player.release();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception e) {}
    }
}
