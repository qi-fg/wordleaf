# 图片资源与清单检查

`WallpaperCatalogCheck.java` 直接检查 APK 使用的清单解析器和资源编码策略。它验证本仓库图片清单、文件大小、仓库 URL 边界、路径穿越、重复 ID、格式/哈希/大小限制，以及 JPEG 不使用文字字符编码。

JDK 测试使用 JSON-java 的 `org.json:json:20240303`；此依赖仅用于本机测试，不打包进 App。Android 构建使用系统提供的 `org.json`。将 Maven Central 的该 JAR 放到已忽略的 `tools/json-20240303.jar`，在仓库根目录运行：

```powershell
New-Item -ItemType Directory -Force build/catalog-tests
javac --release 8 -encoding UTF-8 -cp tools/json-20240303.jar -d build/catalog-tests app/src/cn/wordleaf/cet6/WallpaperCatalog.java app/src/cn/wordleaf/cet6/AssetResponsePolicy.java qa/WallpaperCatalogCheck.java
java -cp 'tools/json-20240303.jar;build/catalog-tests' cn.wordleaf.cet6.WallpaperCatalogCheck
```

浏览器回归中的 `qa-v1.3.5.js` 会模拟安卓桥接的图片字节，并主动阻断 JPEG 文件请求，检查数据图片、旧纯色偏好迁移和学习中冻结背景。它没有启动 Android WebView，不验证手机上的系统组件、网络线程和真实桥接运行环境。
