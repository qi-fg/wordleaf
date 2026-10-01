package cn.wordleaf.cet6;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech tts;
    private boolean speechReady;
    private String pendingExport;
    private static final String HOST = "app.wordleaf.local";

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(248,249,245));
        frame.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        web = new WebView(this);
        frame.addView(web, new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        web.setBackgroundColor(Color.rgb(248,249,245));
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                return !"https".equals(u.getScheme()) || !HOST.equals(u.getHost());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (!"https".equals(u.getScheme()) || !HOST.equals(u.getHost())) return denied();
                String p = u.getPath();
                if (p == null || p.contains("..")) return denied();
                if (p.equals("/")) p = "/index.html";
                String type = p.endsWith(".html") ? "text/html" : p.endsWith(".js") ? "application/javascript" : p.endsWith(".css") ? "text/css" : p.endsWith(".svg") ? "image/svg+xml" : "text/plain";
                try { return new WebResourceResponse(type, "UTF-8", getAssets().open(p.substring(1))); }
                catch (IOException e) { return denied(); }
            }
        });
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                speechReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setSpeechRate(0.85f);
                java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();
                if (voices != null) for (android.speech.tts.Voice voice : voices) {
                    if (voice.getLocale().equals(Locale.US) && !voice.isNetworkConnectionRequired()) { tts.setVoice(voice); break; }
                }
            }
        });
        web.loadUrl("https://" + HOST + "/index.html");
    }
    private WebResourceResponse denied() { return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",null,new ByteArrayInputStream(new byte[0])); }
    private void toast(String text) { runOnUiThread(() -> Toast.makeText(this,text,Toast.LENGTH_LONG).show()); }
    private void js(String code) { runOnUiThread(() -> web.evaluateJavascript(code,null)); }
    private class Bridge {
        @JavascriptInterface public String getState() { return getSharedPreferences("wordleaf", MODE_PRIVATE).getString("state", ""); }
        @JavascriptInterface public void saveState(String state) {
            if (state != null && state.length() <= 5000000) getSharedPreferences("wordleaf", MODE_PRIVATE).edit().putString("state", state).apply();
        }
        @JavascriptInterface public void speak(String word) {
            if (word == null || word.length() > 100) return;
            runOnUiThread(() -> {
                if (!speechReady) { toast("暂时没有可用的英语语音，请在手机的文字转语音设置中安装英语语音包。"); return; }
                if (tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "wordleaf") == TextToSpeech.ERROR) toast("发音失败，请检查手机的英语语音设置。");
            });
        }
        @JavascriptInterface public void exportBackup(String data) {
            if (data == null || data.length() > 5000000) return;
            pendingExport = data;
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
                    i.putExtra(Intent.EXTRA_TITLE,"wordleaf-backup.json"); startActivityForResult(i,100);
                } catch (Exception e) { toast("无法打开文件选择器。"); }
            });
        }
        @JavascriptInterface public void importBackup() {
            runOnUiThread(() -> {
                try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),101); }
                catch (Exception e) { toast("无法打开文件选择器。"); }
            });
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (result != RESULT_OK || data == null || data.getData() == null) { pendingExport = null; return; }
        try {
            if (request == 100 && pendingExport != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(),"wt")) { out.write(pendingExport.getBytes(StandardCharsets.UTF_8)); }
                pendingExport = null; toast("学习进度已备份。");
            } else if (request == 101) {
                try (InputStream in = getContentResolver().openInputStream(data.getData()); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] b = new byte[4096]; int n;
                    while ((n = in.read(b)) != -1) { bytes.write(b,0,n); if (bytes.size() > 5000000) throw new IOException("Too large"); }
                    js("window.receiveBackup(" + JSONObject.quote(new String(bytes.toByteArray(),StandardCharsets.UTF_8)) + ")");
                }
            }
        } catch (Exception e) { pendingExport = null; toast("文件读写失败，请选择有效的进度备份文件。"); }
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("window.appBack ? window.appBack() : false", result -> { if (!"true".equals(result)) super.onBackPressed(); });
    }
    @Override protected void onPause() {
        if (web != null) web.evaluateJavascript("window.pauseLearningClock && window.pauseLearningClock(" + System.currentTimeMillis() + ")", null);
        super.onPause(); if (tts != null) tts.stop();
    }
    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.resumeLearningClock && window.resumeLearningClock(" + System.currentTimeMillis() + ")", null);
    }
    @Override protected void onDestroy() { if (tts != null) tts.shutdown(); if (web != null) { web.removeJavascriptInterface("Android"); web.destroy(); } super.onDestroy(); }
}
