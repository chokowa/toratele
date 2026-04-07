package com.example.toratele;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView webView;
    private ServerSocket serverSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        
        // Androidスマホとして偽装
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.6099.144 Mobile Safari/537.36");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.contains("hanshintigers.jp")) {
                    injectFullscreenScript(view);
                }
            }
        });

        setContentView(webView);
        showReadyScreen();
        startServer();
    }

    private void injectFullscreenScript(WebView view) {
        String js = "javascript:(function() {" +
                "   var style = document.createElement('style');" +
                "   style.innerHTML = '" +
                "       header, footer, nav, aside, .cookie-consent, .modal, .header, .footer, .nav-bar, .side-menu, #header, #footer, .site-header, .site-footer { display: none !important; } " +
                "       body, html { overflow: hidden !important; background: black !important; padding:0 !important; margin:0 !important; } " +
                "       #player_container, .video-player-container, .video-js, video, #main_video1, #main_video2, .vjs-tech { " +
                "           position: fixed !important; top: 0 !important; left: 0 !important; " +
                "           width: 100vw !important; height: 100vh !important; " +
                "           z-index: 999999 !important; background: black !important; border:none !important; " +
                "       }';" +
                "   document.head.appendChild(style);" +
                "   function startPlay() {" +
                "       var v = document.querySelector('video');" +
                "       if(v) { v.play().catch(function(e){ console.log(e); }); }" +
                "       var btn = document.querySelector('.vjs-big-play-button');" +
                "       if(btn) { btn.click(); }" +
                "   }" +
                "   setInterval(startPlay, 1000);" +
                "   startPlay();" +
                "})()";
        view.loadUrl(js);
    }

    private void showReadyScreen() {
        String html = "<html><body style='background-color:#111;color:white;display:flex;justify-content:center;align-items:center;height:100vh;flex-direction:column;font-family:sans-serif;margin:0;'>" +
                "<h1 style='color:#ff0;font-size:3rem;margin-bottom:10px;'>&#x1F405; ToraTele READY</h1>" +
                "<p style='font-size:1.5rem;'>スマホから虎テレの試合を選んで送信してください</p>" +
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
                String[] parts = line.split(" ");
                String path = parts[1];
                
                String targetUrl = null;
                String cookieData = null;
                
                if (path.contains("?")) {
                    String query = path.substring(path.indexOf("?") + 1);
                    String[] params = query.split("&");
                    for (String param : params) {
                        if (param.startsWith("url=")) {
                            targetUrl = URLDecoder.decode(param.substring(4), "UTF-8");
                        } else if (param.startsWith("cookie=")) {
                            cookieData = URLDecoder.decode(param.substring(7), "UTF-8");
                        }
                    }
                }
                
                final String finalUrl = targetUrl;
                final String finalCookie = cookieData;

                if (finalUrl != null) {
                    runOnUiThread(() -> {
                        // Cookie同期を徹底強化
                        if (finalCookie != null && !finalCookie.isEmpty()) {
                            CookieManager cm = CookieManager.getInstance();
                            cm.setAcceptCookie(true);
                            cm.setAcceptThirdPartyCookies(webView, true);
                            
                            String[] cookies = finalCookie.split(";");
                            String[] domains = { "https://.hanshintigers.jp", "https://movie.hanshintigers.jp", "https://hanshintigers.jp" };
                            
                            for (String domainUrl : domains) {
                                for (String c : cookies) {
                                    cm.setCookie(domainUrl, c.trim() + "; Path=/; Domain=.hanshintigers.jp; Secure; SameSite=Lax");
                                }
                            }
                            cm.flush();
                        }
                        
                        // Refererを添えてロード（リダイレクト回避）
                        Map<String, String> extraHeaders = new HashMap<>();
                        extraHeaders.put("Referer", "https://movie.hanshintigers.jp/");
                        webView.loadUrl(finalUrl, extraHeaders);
                    });
                }
            }
            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\n\r\nOK".getBytes());
            socket.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception e) {}
    }
}
