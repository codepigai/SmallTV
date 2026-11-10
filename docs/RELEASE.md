# Release 发布指南

## 版本记录
- **v1.0**（versionCode 1）：2026-11-10 首个 release，验收 19 项 + 复验 A–F 全绿对应的版本。

## 签名库（⚠️ 请勿丢失）
- 密钥库：`keystore/iptv-release.jks`（别名 `iptv`，RSA 2048，有效期 10000 天）
- 口令：见项目根目录 `keystore.properties`（不在 git 仓库内）
- `keystore/` 与 `keystore.properties` 均已加入 `.gitignore`，**不会**随仓库提交。
- ⚠️ 发布后所有后续升级必须使用同一签名库；丢失则设备上必须卸载旧包重装（数据清空）才能更新。
- 请把 `keystore/iptv-release.jks` 和 `keystore.properties` 两个文件备份到仓库之外（网盘/移动硬盘），并妥善保存口令。
- 若换机器：把这两个文件放到新机器相同相对路径即可继续打包；缺失时 `assembleRelease` 会退化为未签名构建（产物名 `app-release-unsigned.apk`，无法直接安装）。

## 构建
```powershell
.\gradlew.bat assembleRelease
```
产物：`app/build/outputs/apk/release/app-release.apk`（已签名，可直接安装）

## 安装到电视
```powershell
.\install.ps1 <电视IP> -Type release
```
注意：release 与 debug 包签名不同，电视上装过 debug 包后首次安装 release 时，`install.ps1` 会自动卸载旧包再装新包（应用内的订阅地址、收藏夹、观看记录会被清空）。

## 发版检查清单
1. 更新 `app/build.gradle` 中 `versionCode`（整数递增）与 `versionName`（面向用户）。
2. `.\gradlew.bat assembleRelease`，确认产物为 `app-release.apk`（非 unsigned）。
3. 真机安装验证（安装、启动、播放、断流重连）。
4. git 提交并打标签：`git tag -a vX.Y -m " vX.Y release"`。
