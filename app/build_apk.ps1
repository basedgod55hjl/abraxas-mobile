# build_apk.ps1 - ABRASAX UNIFIED v2 (no gradle).
# Assets: local.html, abraxas.conf (from .env, never committed), device
# BOND pair, and the LFM Q8_0 model as the soul vault (size: whatever it
# takes). compiles MainActivity + BotService, signs, verifies.
$ErrorActionPreference = 'Stop'
$root = 'C:\abrasax\ANDROID'
$jdk = "$root\jdk17\jdk-17.0.20.1+1"
$bt = "$root\sdk\build-tools\android-15"
$platform = "$root\sdk\platforms\android-35\android.jar"
$app = "$root\app"
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$bt;$env:PATH"

# ---- assets ----
New-Item -ItemType Directory -Force -Path "$app\assets\model" | Out-Null
$modelSrc = 'E:\ABRASAX_TILES\restored\LFM2.5-1.2B-Instruct-Q8_0.gguf'
$modelDst = "$app\assets\model\LFM2.5-1.2B-Instruct-Q8_0.gguf"
if (-not (Test-Path $modelDst)) {
  Write-Output "copying vault model (1.2 GB)..."
  Copy-Item $modelSrc $modelDst
}
$h = (Get-FileHash $modelDst -Algorithm SHA256).Hash.ToLower()
@{sha256=$h; name='LFM2.5-1.2B-Instruct-Q8_0'; note='tiles-restored, byte-identical to the PC original'} | ConvertTo-Json | Set-Content "$app\assets\model\vault.json" -Encoding UTF8
Copy-Item 'C:\abrasax\memory\device_bond.json' "$app\assets\device_bond.json" -Force
Copy-Item 'C:\abrasax\memory\device_bond.sig' "$app\assets\device_bond.sig" -Force
# abraxas.conf from .env (values never printed)
$cfg = @{}
Get-Content 'C:\abrasax\.env' | ForEach-Object {
  $t = $_.Trim()
  if ($t -and -not $t.StartsWith('#') -and $t.Contains('=')) {
    $i = $t.IndexOf('='); $k = $t.Substring(0,$i).Trim(); $v = $t.Substring($i+1).Trim().Trim('"').Trim("'")
    if (-not $cfg.ContainsKey($k)) { $cfg[$k] = $v }
  }
}
"token=$($cfg['ABRASAX_TELEGRAM_TOKEN'])" | Set-Content "$app\assets\abraxas.conf" -Encoding ASCII
"gateway_token=$($cfg['ABRASAX_GATEWAY_TOKEN'])" | Add-Content "$app\assets\abraxas.conf" -Encoding ASCII
"model=gemma-4-e2b-uncensored-hauhaucs-aggressive" | Add-Content "$app\assets\abraxas.conf" -Encoding ASCII

# ---- build ----
$work = "$app\build"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$work\classes","$work\dex","$work\apk" | Out-Null

javac -source 8 -target 8 -classpath $platform -d "$work\classes" `
  "$app\src\com\abraxas\companion\MainActivity.java" `
  "$app\src\com\abraxas\companion\BotService.java"
if ($LASTEXITCODE -ne 0) { exit 1 }

$classFiles = @(Get-ChildItem "$work\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
java -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --release --lib $platform --min-api 26 --output "$work\dex" @classFiles
if ($LASTEXITCODE -ne 0) { exit 1 }

aapt2 link -o "$work\base.apk" -I $platform --manifest "$app\AndroidManifest.xml" `
  --min-sdk-version 26 --target-sdk-version 35 -A "$app\assets"
if ($LASTEXITCODE -ne 0) { exit 1 }

Push-Location "$work\dex"
jar uf "$work\base.apk" classes.dex
Pop-Location

zipalign -f 4 "$work\base.apk" "$work\aligned.apk"
$ks = "$app\abraxas.keystore"  # persistent: same signature across upgrades
if (-not (Test-Path $ks)) {
  keytool -genkeypair -keystore $ks -alias abraxas -keyalg RSA -keysize 2048 -validity 10000 -storepass abraxas123 -keypass abraxas123 -dname "CN=ABRASAX Companion, O=ABRASAX, C=US"
}
java -jar "$bt\lib\apksigner.jar" sign --ks $ks --ks-pass pass:abraxas123 --key-pass pass:abraxas123 --out "$work\abraxas-unified.apk" "$work\aligned.apk"
java -jar "$bt\lib\apksigner.jar" verify "$work\abraxas-unified.apk"
$size = [math]::Round((Get-Item "$work\abraxas-unified.apk").Length/1MB,1)
Write-Output "APK_READY=$work\abraxas-unified.apk SIZE_MB=$size VAULT_SHA256=$($h.Substring(0,12))"
