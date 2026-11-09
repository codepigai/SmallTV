# 用法：先查电视 IP（设置-网络），执行 .\install.ps1 192.168.x.x
param([string]$Ip)
$adb = "D:\app\bigtext\android-sdk\platform-tools\adb.exe"
$apk = "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { Write-Error "先执行 .\gradlew.bat assembleDebug"; exit 1 }
if ($Ip) { & $adb connect "${Ip}:5555" }
& $adb install -r $apk
& $adb shell monkey -p com.smallpig.iptv -c android.intent.category.LEANBACK_LAUNCHER 1
