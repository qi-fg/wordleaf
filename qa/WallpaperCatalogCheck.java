package cn.wordleaf.cet6;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.*;

public class WallpaperCatalogCheck {
    private static int passed;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;}
    private static void rejected(JSONObject value,String label)throws Exception{
        try{WallpaperCatalog.parse(value.toString());throw new AssertionError(label);}catch(IllegalArgumentException|JSONException expected){passed++;}
    }
    private static JSONObject fixture(String source)throws Exception{return new JSONObject(source);}
    public static void main(String[] args)throws Exception{
        String source=new String(Files.readAllBytes(Paths.get("wallpapers.json")),StandardCharsets.UTF_8);
        List<WallpaperCatalog.Entry> entries=WallpaperCatalog.parse(source);
        check(entries.size()==3,"Published catalog parses");
        for(WallpaperCatalog.Entry e:entries){byte[] b=Files.readAllBytes(Paths.get("app/assets/wallpapers/"+e.id+".jpg"));check(e.size==b.length,"File byte count");}
        JSONObject j=fixture(source);j.put("format","unknown");rejected(j,"Unknown format");
        j=fixture(source);j.put("images",new JSONArray());rejected(j,"Empty catalog");
        j=fixture(source);j.getJSONArray("images").getJSONObject(0).put("url","http://raw.githubusercontent.com/qi-fg/wordleaf/main/app/assets/wallpapers/lake.jpg");rejected(j,"Insecure scheme");
        j=fixture(source);j.getJSONArray("images").getJSONObject(0).put("url","https://raw.githubusercontent.com/other/repo/main/lake.jpg");rejected(j,"Different repository");
        j=fixture(source);j.getJSONArray("images").getJSONObject(0).put("url",WallpaperCatalog.ROOT+"app/assets/wallpapers/../lake.jpg");rejected(j,"Traversal");
        j=fixture(source);j.getJSONArray("images").getJSONObject(0).put("url",WallpaperCatalog.ROOT+"app/assets/wallpapers/lake.jpg?redirect=evil");rejected(j,"Unexpected query");
        j=fixture(source);j.getJSONArray("images").getJSONObject(0).put("sha256","bad");rejected(j,"Invalid digest");
        j=fixture(source);j.getJSONArray("images").getJSONObject(0).put("size",2000001);rejected(j,"Oversized image");
        j=fixture(source);j.getJSONArray("images").getJSONObject(0).put("size",0);rejected(j,"Empty image");
        j=fixture(source);j.getJSONArray("images").getJSONObject(1).put("id",j.getJSONArray("images").getJSONObject(0).getString("id"));rejected(j,"Duplicate ID");
        j=fixture(source);j.getJSONArray("images").getJSONObject(0).put("id","../lake");rejected(j,"Invalid ID");
        check("image/jpeg".equals(AssetResponsePolicy.type("/wallpapers/lake.jpg")),"JPEG MIME");
        check(AssetResponsePolicy.encoding("image/jpeg")==null,"Images have no character encoding");
        check("UTF-8".equals(AssetResponsePolicy.encoding("text/css")),"CSS keeps UTF-8");
        check("UTF-8".equals(AssetResponsePolicy.encoding("application/javascript")),"JS keeps UTF-8");
        check(AssetResponsePolicy.encoding("application/octet-stream")==null,"Other binary is not text");
        System.out.println("Native catalog and asset response policy: "+passed+" checks passed");
    }
}
