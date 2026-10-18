package cn.wordleaf.cet6;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Keeps one verified daily picture; all failures fall back to packaged assets. */
final class WallpaperManager {
    private final Context context;
    private final SharedPreferences prefs;
    private final Runnable ready;
    private volatile boolean busy,closed;
    private volatile HttpURLConnection activeConnection;
    WallpaperManager(Context c,Runnable r){context=c.getApplicationContext();prefs=context.getSharedPreferences("wallpapers",0);ready=r;}
    static String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new java.util.Date());}
    static long localDay(){Calendar local=Calendar.getInstance(),utc=Calendar.getInstance(TimeZone.getTimeZone("UTC"));utc.clear();utc.set(local.get(Calendar.YEAR),local.get(Calendar.MONTH),local.get(Calendar.DAY_OF_MONTH));return utc.getTimeInMillis()/86400000L;}
    synchronized void refresh(){
        long now=System.currentTimeMillis();
        if(closed||busy||today().equals(prefs.getString("checked-day",""))||now-prefs.getLong("attempt",0)<1800000L)return;
        busy=true;prefs.edit().putLong("attempt",now).apply();
        new Thread(()->{
            try{
                String day=today();long dayIndex=localDay();
                byte[] json=download(WallpaperCatalog.ROOT+"wallpapers.json?day="+day,32768);
                List<WallpaperCatalog.Entry> images=WallpaperCatalog.parse(new String(json,"UTF-8"));
                WallpaperCatalog.Entry entry=images.get((int)Math.floorMod(dayIndex,(long)images.size()));
                byte[] image=download(entry.url+"?v="+entry.sha256,WallpaperCatalog.MAX_IMAGE);
                if(image.length!=entry.size||!digest(image).equals(entry.sha256))throw new IOException("Image mismatch");
                if(image.length<3||(image[0]&255)!=255||(image[1]&255)!=216||(image[2]&255)!=255)throw new IOException("Not JPEG");
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(image,0,image.length,bounds);
                if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>4096||bounds.outHeight>4096||(long)bounds.outWidth*bounds.outHeight>8000000L)throw new IOException("Image dimensions");
                if(closed||!day.equals(today()))return;
                String previous=prefs.getString("file",""),name="daily-a.jpg".equals(previous)?"daily-b.jpg":"daily-a.jpg";
                File file=new File(context.getFilesDir(),name);
                try(FileOutputStream out=new FileOutputStream(file)){out.write(image);out.getFD().sync();}
                // Switch the pointer only after the complete verified image is durable.
                boolean saved=prefs.edit().putString("file",name).putString("day",day).putString("sha256",entry.sha256).putString("checked-day",day).commit();
                if(saved&&!closed)ready.run();
            }catch(Exception ignored){/* No connectivity, malformed catalog or partial download: use built-in images. */}
            finally{busy=false;}
        },"Wordleaf-Wallpaper").start();
    }
    private byte[] download(String address,int limit)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();activeConnection=c;
        c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","Wordleaf/1.3.6");
        try{
            if(closed||c.getResponseCode()!=200)throw new IOException("Unavailable");
            try(InputStream in=c.getInputStream()){return read(in,limit);}
        }finally{c.disconnect();activeConnection=null;}
    }
    private static byte[] read(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Too large");out.write(buffer,0,n);}return out.toByteArray();
    }
    private static String digest(byte[] bytes)throws Exception{
        StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.US,"%02x",b&255));return out.toString();
    }
    String data(String id){
        if(!"lake".equals(id)&&!"forest".equals(id)&&!"coast".equals(id))return "";
        if(today().equals(prefs.getString("day",""))){
            String name=prefs.getString("file","");
            if("daily-a.jpg".equals(name)||"daily-b.jpg".equals(name))try(InputStream in=new FileInputStream(new File(context.getFilesDir(),name))){
                byte[] bytes=read(in,WallpaperCatalog.MAX_IMAGE);
                if(digest(bytes).equals(prefs.getString("sha256","")))return "data:image/jpeg;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP);
            }catch(Exception ignored){}
        }
        try(InputStream in=context.getAssets().open("wallpapers/"+id+".jpg")){
            return "data:image/jpeg;base64,"+Base64.encodeToString(read(in,WallpaperCatalog.MAX_IMAGE),Base64.NO_WRAP);
        }catch(Exception ignored){return "";}
    }
    void close(){closed=true;HttpURLConnection c=activeConnection;if(c!=null)c.disconnect();}
}
