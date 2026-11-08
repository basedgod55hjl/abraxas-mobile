# build_apk.ps1 - hand-assemble the ABRASAX Companion APK (no gradle).
# Toolchain: local jdk17 + build-tools android-15 (extracted from dl zips).
$ErrorActionPreference = 'Stop'
$root = 'C:\abrasax\ANDROID'
$jdk = "$root\jdk17\jdk-17.0.20.1+1"
$bt = "$root\sdk\build-tools\android-15"
$platform = "$root\sdk\platforms\android-35\android.jar"
$app = "$root\app"
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$bt;$env:PATH"

$work = "$app\build"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$work\classes","$work\dex","$work\apk" | Out-Null

# 1. javac
javac -source 8 -target 8 -classpath $platform -d "$work\classes" "$app\src\com\abraxas\companion\MainActivity.java"
if ($LASTEXITCODE -ne 0) { exit 1 }

# 2. d8 -> classes.dex
java -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --release --lib $platform --min-api 26 --output "$work\dex" "$work\classes\com\abraxas\companion\MainActivity.class"
if ($LASTEXITCODE -ne 0) { exit 1 }

# 3. aapt2 link (manifest only, no resources)
aapt2 link -o "$work\base.apk" -I $platform --manifest "$app\AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 35
if ($LASTEXITCODE -ne 0) { exit 1 }

# 4. add classes.dex
Push-Location "$work\dex"
jar uf "$work\base.apk" classes.dex
Pop-Location

# 5. zipalign + sign (debug keystore)
zipalign -f 4 "$work\base.apk" "$work\aligned.apk"
$ks = "$work\abraxas.keystore"
if (-not (Test-Path $ks)) {
  keytool -genkeypair -keystore $ks -alias abraxas -keyalg RSA -keysize 2048 -validity 10000 -storepass abraxas123 -keypass abraxas123 -dname "CN=ABRASAX Companion, O=ABRASAX, C=US"
}
java -jar "$bt\lib\apksigner.jar" sign --ks $ks --ks-pass pass:abraxas123 --key-pass pass:abraxas123 --out "$work\abraxas-companion.apk" "$work\aligned.apk"
java -jar "$bt\lib\apksigner.jar" verify "$work\abraxas-companion.apk"
Write-Output "APK_READY=$work\abraxas-companion.apk"
