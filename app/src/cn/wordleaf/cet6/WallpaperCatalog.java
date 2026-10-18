package cn.wordleaf.cet6;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class WallpaperCatalog {
    static final String ROOT="https://raw.githubusercontent.com/qi-fg/wordleaf/main/";
    static final int MAX_IMAGE=2000000;
    static final class Entry {
        final String id,url,sha256;
        final int size;
        Entry(String i,String u,String h,int n){id=i;url=u;sha256=h;size=n;}
    }
    static List<Entry> parse(String text) throws Exception {
        JSONObject catalog=new JSONObject(text);
        if(!"wordleaf-wallpapers-v1".equals(catalog.getString("format")))throw new IllegalArgumentException("Format");
        JSONArray entries=catalog.getJSONArray("images");
        if(entries.length()<1||entries.length()>60)throw new IllegalArgumentException("Count");
        List<Entry> result=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        for(int i=0;i<entries.length();i++){
            JSONObject j=entries.getJSONObject(i);
            String id=j.getString("id"),url=j.getString("url"),hash=j.getString("sha256");
            long size=j.getLong("size");
            if(!id.matches("[a-z0-9-]{1,40}")||!seen.add(id)||!url.startsWith(ROOT+"app/assets/wallpapers/")||!url.substring((ROOT+"app/assets/wallpapers/").length()).matches("[a-z0-9-]+\\.jpg")||!hash.matches("[a-f0-9]{64}")||size<1||size>MAX_IMAGE)throw new IllegalArgumentException("Image");
            result.add(new Entry(id,url,hash,(int)size));
        }
        return result;
    }
}
