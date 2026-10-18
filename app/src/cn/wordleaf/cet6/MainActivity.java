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
    private FrameLayout frame;
    private TextToSpeech tts;
    private boolean speechReady;
    private AppUpdater updater;
    private WallpaperManager wallpapers;
    private String pendingExport;
    private static final String HOST = "app.wordleaf.local";

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        frame = new FrameLayout(this);
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
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        // Refresh only WebView resources after an upgrade; never clear learning storage.
        try {
            String version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
            if(!version.equals(getSharedPreferences("wordleaf",0).getString("assets-version",""))){
                web.clearCache(true);
                getSharedPreferences("wordleaf",0).edit().putString("assets-version",version).apply();
            }
        } catch(Exception ignored) { }
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
                String type = AssetResponsePolicy.type(p);
                String encoding=AssetResponsePolicy.encoding(type);
                java.util.Map<String,String> headers=new java.util.HashMap<>();headers.put("Cache-Control","no-store");
                try { return new WebResourceResponse(type, encoding, 200, "OK", headers, getAssets().open(p.substring(1))); }
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
        updater = new AppUpdater(this, data -> js("window.receiveUpdate && window.receiveUpdate(" + data.toString() + ")"));
        wallpapers = new WallpaperManager(this, () -> { if(!isFinishing())js("window.wallpaperReady && window.wallpaperReady()"); });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) { if(progress==100){updater.autoCheck();wallpapers.refresh();} }
        });
        web.loadUrl("https://" + HOST + "/index.html");
    }
    private WebResourceResponse denied() { return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",null,new ByteArrayInputStream(new byte[0])); }
    private void toast(String text) { runOnUiThread(() -> Toast.makeText(this,text,Toast.LENGTH_LONG).show()); }
    private void js(String code) { runOnUiThread(() -> { if(web!=null&&!isDestroyed())web.evaluateJavascript(code,null); }); }
    private class Bridge {
        @JavascriptInterface public String wallpaperData(String id) { return wallpapers==null?"":wallpapers.data(id); }
        @JavascriptInterface public void setScenicTheme(boolean scenic) {
            runOnUiThread(() -> {
                int color = scenic ? Color.rgb(20,46,57) : Color.rgb(248,249,245);
                frame.setBackgroundColor(color);
                web.setBackgroundColor(color);
                int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                if (!scenic) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                getWindow().getDecorView().setSystemUiVisibility(flags);
            });
        }
        @JavascriptInterface public void checkUpdate(boolean manual) { if(updater!=null)updater.check(manual); }
        @JavascriptInterface public void downloadUpdate() { if(updater!=null)updater.download(); }
        @JavascriptInterface public void installUpdate() { if(updater!=null)updater.install(); }
        @JavascriptInterface public void openRelease() { if(updater!=null)updater.openRelease(); }
        @JavascriptInterface public boolean autoUpdateEnabled() { return updater==null||updater.autoEnabled(); }
        @JavascriptInterface public void setAutoUpdate(boolean enabled) { if(updater!=null)updater.setAuto(enabled); }
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
        @JavascriptInterface public void importWordlist() {
            runOnUiThread(() -> {
                try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),102); }
                catch (Exception e) { toast("无法打开词表文件选择器。"); }
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
            } else if (request == 101 || request == 102) {
                try (InputStream in = getContentResolver().openInputStream(data.getData()); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] b = new byte[4096]; int n;
                    while ((n = in.read(b)) != -1) { bytes.write(b,0,n); if (bytes.size() > (request==102?3000000:5000000)) throw new IOException("Too large"); }
                    js((request==102?"window.receiveWordlist(":"window.receiveBackup(") + JSONObject.quote(new String(bytes.toByteArray(),StandardCharsets.UTF_8)) + ")");
                }
            }
        } catch (Exception e) { pendingExport = null; toast(request==102?"文件读取失败，请选择有效的词表文件。":"文件读写失败，请选择有效的进度备份文件。"); }
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
        if(updater!=null)updater.resumed();
        if(wallpapers!=null)wallpapers.refresh();
        if (web != null) web.evaluateJavascript("window.resumeLearningClock && window.resumeLearningClock(" + System.currentTimeMillis() + ")", null);
    }
    @Override protected void onDestroy() { if(wallpapers!=null)wallpapers.close();if (tts != null) tts.shutdown(); if (web != null) { web.removeJavascriptInterface("Android"); web.destroy(); } super.onDestroy(); }
}
