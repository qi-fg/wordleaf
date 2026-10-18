package cn.wordleaf.cet6;

final class AssetResponsePolicy {
    static String type(String path){
        return path.endsWith(".html")?"text/html":path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".jpg")?"image/jpeg":"application/octet-stream";
    }
    static String encoding(String type){return type.startsWith("text/")||"application/javascript".equals(type)||"image/svg+xml".equals(type)?"UTF-8":null;}
}
