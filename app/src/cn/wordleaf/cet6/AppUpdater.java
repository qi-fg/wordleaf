package cn.wordleaf.cet6;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;

final class AppUpdater {
    interface Listener { void send(JSONObject data); }
    private final Activity activity;
    private final Listener listener;
    private volatile JSONObject release;
    private volatile boolean busy;
    private boolean pendingInstall;
    private static final String RELEASE="https://github.com/qi-fg/wordleaf/releases/latest";
    AppUpdater(Activity a, Listener l) { activity=a;listener=l; }
    boolean autoEnabled() { return activity.getSharedPreferences("updates",0).getBoolean("auto",false); }
    void setAuto(boolean enabled) { activity.getSharedPreferences("updates",0).edit().putBoolean("auto",enabled).apply(); if(enabled)check(false); }
    void autoCheck() {
        long last=activity.getSharedPreferences("updates",0).getLong("checked",0);
        if(autoEnabled()&&System.currentTimeMillis()-last>86400000L) check(false);
    }
    private void send(String status,String message) {
        try { JSONObject j=new JSONObject().put("status",status).put("message",message);
            if(release!=null) j.put("notes",release.optString("notes")); listener.send(j);
        } catch(Exception ignored) { }
    }
    private HttpURLConnection connect(String address) throws Exception {
        URL url=new URL(address);
        // Downloads may redirect to GitHub's signed asset CDN, always over HTTPS.
        for(int i=0;i<6;i++) {
            if(!"https".equals(url.getProtocol())) throw new IOException("HTTPS required");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Wordleaf/1.3.2");
            int status=c.getResponseCode();
            if(status>=300&&status<400) { String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException();url=new URL(url,location);continue; }
            if(status!=200) { c.disconnect();throw new IOException("HTTP "+status); }return c;
        }throw new IOException("Redirect limit");
    }
    synchronized void check(boolean manual) {
        if(busy) { if(manual)send("busy","正在检查或下载，请稍候。");return; }busy=true;
        if(manual)send("checking","正在检查新版…");
        new Thread(()->{
            boolean downloadAfter=false;
            try {
                HttpURLConnection c=connect("https://raw.githubusercontent.com/qi-fg/wordleaf/main/update.json");
                byte[] data;
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>32768)throw new IOException();}data=out.toByteArray();
                } finally { c.disconnect(); }
                JSONObject j=new JSONObject(new String(data,"UTF-8"));
                if(!j.getString("url").matches("https://github\\.com/qi-fg/wordleaf/releases/download/[^/]+/wordleaf-v[0-9.]+\\.apk")||!j.getString("sha256").matches("[a-f0-9]{64}")||j.getLong("size")<1||j.getLong("size")>50000000L||j.getInt("versionCode")<1)throw new IOException();
                activity.getSharedPreferences("updates",0).edit().putLong("checked",System.currentTimeMillis()).apply();
                long current=version(activity.getPackageManager().getPackageInfo(activity.getPackageName(),0));
                if(j.getInt("versionCode")<=current){release=null;send("latest","已是最新版本。");return;}
                release=j;
                File apk=new File(activity.getCacheDir(),"update.apk");
                boolean ready=false;
                if(apk.exists())try{verify(apk,j);ready=true;}catch(Exception ignored){}
                send(ready?"ready":"available",ready?"新版已下载，可继续安装。":"发现新版 "+j.getString("versionName"));
                downloadAfter=!manual&&!ready&&autoEnabled();
            } catch(Exception e){send("error","暂时无法连接 GitHub，请稍后重试或到发布页下载；离线学习不受影响。");}
            finally{busy=false;if(downloadAfter)download();}
        },"wordleaf-update-check").start();
    }
    synchronized void download() {
        if(busy||release==null)return;busy=true;JSONObject j=release;send("downloading","开始下载新版…");
        new Thread(()->{
            File part=new File(activity.getCacheDir(),"update.part");
            try {
                HttpURLConnection c=connect(j.getString("url"));long count=0,last=0,total=j.getLong("size");
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part)) {
                    byte[] b=new byte[16384];int n;
                    while((n=in.read(b))!=-1){count+=n;if(count>total)throw new IOException();out.write(b,0,n);
                        if(System.currentTimeMillis()-last>500){send("downloading","正在下载 "+(count*100/total)+"%");last=System.currentTimeMillis();}}
                }finally{c.disconnect();}
                verify(part,j);File apk=new File(activity.getCacheDir(),"update.apk");
                if(apk.exists()&&!apk.delete()||!part.renameTo(apk))throw new IOException();
                send("ready","下载完成并已校验，请点击安装新版。");
            }catch(Exception e){part.delete();send("error","下载或校验失败，未安装。请重新检查更新，或到发布页下载 APK。");}
            finally{busy=false;}
        },"wordleaf-update-download").start();
    }
    private long version(PackageInfo p) { return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode; }
    private Signature[] signatures(PackageInfo p) { return Build.VERSION.SDK_INT>=28?p.signingInfo.getApkContentsSigners():p.signatures; }
    private void verify(File file,JSONObject j) throws Exception {
        if(file.length()!=j.getLong("size"))throw new IOException();MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.US,"%02x",b&255));
        if(!hex.toString().equals(j.getString("sha256")))throw new IOException();
        PackageManager pm=activity.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo apk=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags),installed=pm.getPackageInfo(activity.getPackageName(),flags);
        if(apk==null||!installed.packageName.equals(apk.packageName)||version(apk)!=j.getInt("versionCode")||version(apk)<=version(installed)||!Arrays.equals(signatures(apk),signatures(installed)))throw new IOException();
    }
    void install() {
        activity.runOnUiThread(()->{
            try {
                if(busy||release==null)return;verify(new File(activity.getCacheDir(),"update.apk"),release);
                if(!activity.getPackageManager().canRequestPackageInstalls()) {
                    pendingInstall=true;send("ready","请允许词芽安装应用，返回后继续确认安装。");
                    activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));return;
                }
                pendingInstall=false;
                activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://cn.wordleaf.cet6.updates/update.apk"),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            }catch(Exception e){pendingInstall=false;send("error","无法启动安装，请到发布页下载 APK 覆盖安装。");}
        });
    }
    void resumed() { if(pendingInstall&&activity.getPackageManager().canRequestPackageInstalls())install(); }
    void openRelease() { activity.runOnUiThread(()->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(RELEASE)));}catch(Exception e){send("error","没有可用的浏览器，请用其他设备打开 GitHub 发布页。");}}); }
}
