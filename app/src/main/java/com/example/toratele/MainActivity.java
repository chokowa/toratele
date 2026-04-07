package com.example.toratele;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

public class MainActivity extends Activity {
    private WebView webView;
    private ServerSocket serverSocket;
    private final int PORT = 8080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // メインのWebViewを作成 (全画面)
        webView = new WebView(this);
        setContentView(webView);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false); // 自動再生を可能に
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        
        webView.setWebViewClient(new WebViewClient());
        
        // 初期画面 (待機中)
        webView.loadData("<html><body style='background:black;color:white;display:flex;justify-content:center;align-items:center;height:100vh;font-family:sans-serif;'><h1>ToraTele Ready<br>スマホから送信を待っています...</h1></body></html>", "text/html", "UTF-8");

        // HTTPサーバーを別スレッドで起動
        startServer();
    }

    private void startServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(PORT);
                    while (true) {
                        Socket client = serverSocket.accept();
                        handleClient(client);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handleClient(Socket client) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String line = br.readLine();
            if (line == null) return;

            // 例: GET /?url=https... HTTP/1.1
            if (line.contains("GET /?url=")) {
                int start = line.indexOf("url=") + 4;
                int end = line.indexOf(" ", start);
                String encodedUrl = line.substring(start, end);
                final String url = URLDecoder.decode(encodedUrl, "UTF-8");

                // UIスレッドでWebViewを更新
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl(url);
                    }
                });
            }

            // シンプルな成功レスポンスを返す (CORS許可)
            OutputStream os = client.getOutputStream();
            String response = "HTTP/1.1 200 OK\r\n" +
                              "Content-Type: text/plain\r\n" +
                              "Access-Control-Allow-Origin: *\r\n" +
                              "Content-Length: 2\r\n" +
                              "Connection: close\r\n\r\nOK";
            os.write(response.getBytes());
            os.flush();
            client.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) {}
    }
}
