# SmallTV

一个为家中老人开发的 Android TV 网络电视（IPTV）播放应用。

家里长辈长期要看央视，这个应用的目标很简单：**打开就能看电视，遥控器几个键就能换台、收藏、看节目单**——界面字大、焦点清晰、全程遥控器可操作，没有多余功能。自用项目，代码放在内网 Gitea 备份，顺便开源分享。

## 功能

- **多订阅管理**：可添加多个 IPTV 播放列表（m3u / m3u8），配套 XMLTV 节目单链接；切换订阅即切换频道列表。
- **频道收藏**：播放中一键收藏，全局收藏抽屉随时快速进入，不依赖当前订阅。
- **节目单（EPG）**：播放面板内呼出节目单，显示当前/即将播出的节目。
- **播放信息**：播放界面可显示当前视频的编码格式（H.264/H.265 等）、实时码率与网络延迟。
- **多屏同看**：支持 2–5 路画面同屏，一路主画面 + 小窗预览。（此功能尚不完善）
- **画廊 / 列表两种首页**：宫格或列表浏览频道，带频道图标。
- **断流自动重连**：播放异常自动重试，减少老人遇到的「卡住不会办」情况。

## 适老化设计

- 横屏大屏布局，全局思源黑体 SC，字号偏大；
- 全遥控器方向键焦点导航，无触屏依赖；
- 顶栏五个页签带图标，进入路径短、层级浅。

## 技术栈

| | |
|---|---|
| 语言 / 平台 | Kotlin，Android（minSdk 23 / targetSdk 34） |
| 播放器 | AndroidX Media3（ExoPlayer + HLS） |
| 存储 | Room（订阅、频道、收藏元信息） |
| 网络 / 图片 | OkHttp、Glide |
| 异步 | Kotlin Coroutines |
| 测试 | JUnit 纯函数单元测试（70 用例） |

## 构建与安装

```powershell
# 构建 release（需先配置 keystore.properties，见 docs/RELEASE.md）
.\gradlew.bat assembleRelease

# 安装到电视
.\install.ps1 <电视IP> -Type release
```

发布、签名库备份与升级注意事项见 [docs/RELEASE.md](docs/RELEASE.md)。

## 仓库结构

```
app/src/main/java/com/smallpig/iptv/
├── data/     Room 数据库与仓储
├── net/      列表拉取与流媒体策略
├── parse/    m3u / XMLTV 解析、格式与码率展示（纯函数）
└── ui/       首页、播放、多屏、订阅管理、设置
app/src/test/ 单元测试
docs/         发布指南与设计文档
```

## 许可

个人自用项目。随包字体 Source Han Sans SC 遵循 SIL OFL 1.1（见 `app/src/main/assets/fonts/OFL.txt`）。
