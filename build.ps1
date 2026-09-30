param([string]$SdkRoot = "$PSScriptRoot\tools\sdk")
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$bt = Join-Path $SdkRoot 'android-15'
if (!(Test-Path -LiteralPath (Join-Path $bt 'aapt2.exe'))) {
    $bt = (Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
}
$platform = Join-Path $SdkRoot 'android-35\android.jar'
if (!(Test-Path -LiteralPath $platform)) { $platform = Join-Path $SdkRoot 'platforms\android-35\android.jar' }
if (!(Test-Path -LiteralPath $platform)) { throw 'Android API 35 SDK not found.' }
$build = Join-Path $root 'build'
$output = Join-Path $root 'output'
New-Item -ItemType Directory -Force -Path $build,$output,(Join-Path $root 'tools'),(Join-Path $build 'classes'),(Join-Path $build 'dex') | Out-Null
function Check([string]$step) { if ($LASTEXITCODE -ne 0) { throw "$step failed (exit $LASTEXITCODE)." } }
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $root 'app\res') -o (Join-Path $build 'resources.zip')
Check 'Resources'
& (Join-Path $bt 'aapt2.exe') link -o (Join-Path $build 'unsigned.apk') -I $platform --manifest (Join-Path $root 'app\AndroidManifest.xml') -A (Join-Path $root 'app\assets') (Join-Path $build 'resources.zip')
Check 'APK resources'
$sources = @(Get-ChildItem (Join-Path $root 'app\src') -Filter '*.java' -Recurse | ForEach-Object FullName)
& javac --release 8 -encoding UTF-8 -classpath $platform -d (Join-Path $build 'classes') @sources
Check 'Java compilation'
$classes = @(Get-ChildItem (Join-Path $build 'classes') -Filter '*.class' -Recurse | ForEach-Object FullName)
& java -cp (Join-Path $bt 'lib\d8.jar') com.android.tools.r8.D8 --release --min-api 26 --lib $platform --output (Join-Path $build 'dex') @classes
Check 'DEX compilation'
& jar uf (Join-Path $build 'unsigned.apk') -C (Join-Path $build 'dex') classes.dex
Check 'DEX packaging'
& (Join-Path $bt 'zipalign.exe') -f -p 4 (Join-Path $build 'unsigned.apk') (Join-Path $build 'aligned.apk')
Check 'APK alignment'
$key = Join-Path $root 'tools\wordleaf-signing.jks'
$passwordFile = Join-Path $root 'tools\signing-password.txt'
if (!(Test-Path -LiteralPath $key)) {
    $password = [guid]::NewGuid().ToString('N')
    Set-Content -LiteralPath $passwordFile -Value $password -NoNewline
    & keytool -genkeypair -keystore $key -storepass $password -keypass $password -alias wordleaf -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Wordleaf Local Build, O=Personal, C=CN'
    Check 'Signing key'
}
$env:WORDLEAF_SIGNING_PASSWORD = (Get-Content -LiteralPath $passwordFile -Raw).Trim()
try {
    & java -jar (Join-Path $bt 'lib\apksigner.jar') sign --ks $key --ks-key-alias wordleaf --ks-pass env:WORDLEAF_SIGNING_PASSWORD --out (Join-Path $output 'wordleaf-cet6.apk') (Join-Path $build 'aligned.apk')
    Check 'APK signing'
} finally { Remove-Item Env:WORDLEAF_SIGNING_PASSWORD -ErrorAction SilentlyContinue }
& java -jar (Join-Path $bt 'lib\apksigner.jar') verify --verbose (Join-Path $output 'wordleaf-cet6.apk')
Check 'APK signature verification'
& (Join-Path $bt 'aapt.exe') dump badging (Join-Path $output 'wordleaf-cet6.apk')
Check 'APK metadata verification'
Get-FileHash -LiteralPath (Join-Path $output 'wordleaf-cet6.apk') -Algorithm SHA256
