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
        // スマホのChromeとして完全偽装
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.6099.144 Mobile Safari/537.36");

        // サードパーティCookieを完全許可
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.startsWith("http")) {
                    String cleanJs = "javascript:(function() {" +
                            "   function clean() {" +
                            "       var css = 'header, footer, nav, aside, .cookie-consent, .modal, #header, #footer { display: none !important; }'; " +
                            "       css += 'body, html { overflow: hidden !important; background: black !important; }'; " +
                            "       css += 'video, .video-player, #player_container { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 99999 !important; background: black !important; border:none !important; }'; " +
                            "       var style = document.createElement('style'); style.innerHTML = css; document.head.appendChild(style); " +
                            "   }" +
                            "   setInterval(clean, 1000); clean();" +
                            "})()";
                    view.loadUrl(cleanJs);
                }
            }
        });

        setContentView(webView);
        showReadyScreen();
        startServer();
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
                        if (finalUrl.contains(".m3u8")) {
                            // hls.js を使ったカスタムプレイヤーで再生
                            // Android WebViewはネイティブHLSをサポートしないため hls.js が必須
                            String hlsPlayerHtml = buildHlsPlayerHtml(finalUrl);
                            // baseUrlに虎テレドメインを指定してCORSを回避
                            webView.loadDataWithBaseURL(
                                "https://movie.hanshintigers.jp",
                                hlsPlayerHtml,
                                "text/html", "UTF-8", null
                            );
                        } else {
                            // 通常URLの場合：CookieをセットしてWebViewで開く
                            if (finalCookie != null && !finalCookie.isEmpty()) {
                                CookieManager.getInstance().setCookie("https://movie.hanshintigers.jp", finalCookie);
                                CookieManager.getInstance().flush();
                            }
                            webView.loadUrl(finalUrl);
                        }
                    });
                }
            }
            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\n\r\nOK".getBytes());
            socket.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * hls.js を CDN から読み込み、m3u8 ストリームをテレビで再生するHTMLを生成する。
     * Android WebViewはネイティブHLSをサポートしないが、
     * hls.js はMediaSource Extensions(MSE)を使って確実に再生できる。
     */
    private String buildHlsPlayerHtml(String streamUrl) {
        // シングルクォートをエスケープしてJSインジェクションを防ぐ
        String safeUrl = streamUrl.replace("'", "\\'");
        return "<!DOCTYPE html>" +
            "<html><head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<style>" +
            "* { margin:0; padding:0; box-sizing:border-box; }" +
            "body { background:#000; width:100vw; height:100vh; overflow:hidden; " +
            "  display:flex; align-items:center; justify-content:center; }" +
            "video { width:100vw; height:100vh; object-fit:contain; }" +
            "#status { position:fixed; top:50%; left:50%; transform:translate(-50%,-50%); " +
            "  color:#ff0; font-size:2rem; font-family:sans-serif; text-align:center;" +
            "  background:rgba(0,0,0,0.7); padding:20px; border-radius:12px; pointer-events:none; }" +
            "</style>" +
            "</head><body>" +
            "<div id='status'>&#x1F405; 読み込み中...</div>" +
            "<video id='video' autoplay controls playsinline></video>" +
            "<script src='https://cdn.jsdelivr.net/npm/hls.js@latest'></script>" +
            "<script>" +
            "var streamUrl = '" + safeUrl + "';" +
            "var video = document.getElementById('video');" +
            "var status = document.getElementById('status');" +
            "if (Hls.isSupported()) {" +
            "  var hls = new Hls({ debug: false, enableWorker: true });" +
            "  hls.loadSource(streamUrl);" +
            "  hls.attachMedia(video);" +
            "  hls.on(Hls.Events.MANIFEST_PARSED, function() {" +
            "    status.style.display = 'none';" +
            "    video.play().catch(function(e) { console.error(e); });" +
            "  });" +
            "  hls.on(Hls.Events.ERROR, function(event, data) {" +
            "    if (data.fatal) {" +
            "      status.style.display = 'block';" +
            "      status.innerHTML = '&#x274C; 再生エラー<br>' + data.details;" +
            "    }" +
            "  });" +
            "} else if (video.canPlayType('application/vnd.apple.mpegurl')) {" +
            "  video.src = streamUrl;" +
            "  video.addEventListener('loadedmetadata', function() {" +
            "    status.style.display = 'none';" +
            "    video.play();" +
            "  });" +
            "} else {" +
            "  status.innerHTML = '&#x274C; HLS未対応の環境です';" +
            "}" +
            "</script>" +
            "</body></html>";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception e) {}
    }
}
