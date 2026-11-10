# 用法：先查电视 IP（设置-网络），执行 .\install.ps1 192.168.x.x [-Type release]
param([string]$Ip, [ValidateSet('debug', 'release')][string]$Type = 'debug')
$adb = "D:\app\bigtext\android-sdk\platform-tools\adb.exe"
$apk = "app\build\outputs\apk\$Type\app-$Type.apk"
$task = "assemble" + $Type.Substring(0, 1).ToUpper() + $Type.Substring(1)
if (-not (Test-Path $apk)) { Write-Error "先执行 .\gradlew.bat $task"; exit 1 }
if ($Ip) { & $adb connect "${Ip}:5555" }
# release 与 debug 签名不同，覆盖安装会报签名不一致，需先卸载旧包（会清空应用数据）
$err = & $adb install -r $apk 2>&1 | Out-String
if ($err -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
    Write-Warning "签名与已装版本不一致，卸载旧包后重装（应用数据将被清空）"
    & $adb uninstall com.smallpig.iptv | Out-Null
    & $adb install -r $apk
} elseif ($err -match 'Failure|Error') {
    Write-Error $err
    exit 1
}
& $adb shell monkey -p com.smallpig.iptv -c android.intent.category.LEANBACK_LAUNCHER 1
