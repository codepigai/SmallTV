# Android TV IPTV 播放器 — 设计文档

日期：2026-11-08
状态：已确认
来源需求：`需求.md`

## 1. 目标与范围

开发运行在 TCL 电视（Android 11–13，Android TV）上的 IPTV 直播播放器 App，遥控器全程操作。核心能力：

1. 订阅多个 IPTV 源，每个源 = m3u 频道列表链接 + XMLTV 节目单链接
2. 切换当前订阅，频道列表随之变化
3. 播放 m3u 中的直播流（HLS / HTTP-TS）
4. 全局收藏频道，快速进入
5. 播放界面可切换显示当前视频的编码格式、分辨率、码率

非目标（明确不做）：时移/回看、udp:// 组播流、多语言、设置页、手机扫码、U 盘导入、EPG 时间轴网格。

## 2. 技术选型

- 原生 Android（Kotlin，单模块）：Views + Leanback/Guideline，不用 Compose
- 播放器：androidx.media3（ExoPlayer + HLS + UI 组件）
- 存储：Room
- 网络：OkHttp（默认支持 gzip），Kotlin 协程做异步
- 构建：Gradle wrapper 8.5，AGP 8.1.x，compileSdk/targetSdk 34，minSdk 23
- 包名 `com.smallpig.iptv`，App 名 "IPTV 电视"

### 构建环境（本机已验证，不修改系统环境变量）

| 组件 | 位置 |
|---|---|
| JDK 17（Temurin 17.0.20） | `D:\app\bigtext\tools\jdk17` |
| Gradle 8.5 | `D:\app\bigtext\tools\gradle-8.5` |
| Android SDK（platform 34、build-tools 33.0.1、platform-tools） | `D:\app\bigtext\android-sdk` |

- `local.properties` 写 `sdk.dir=D:\\app\\bigtext\\android-sdk`
- 构建命令前设置会话级 `JAVA_HOME=D:\app\bigtext\tools\jdk17`

## 3. 界面结构（4 个界面）

### 3.1 首页（频道浏览）— 启动页
- 顶部页签：`全部频道` `收藏夹` `订阅管理`
- 全部频道：左侧分组列表（m3u `group-title`），右侧频道列表（频道名 + 台标 + 当前节目名）
- 确定键播放 → 播放页；菜单键 = 收藏/取消收藏
- 数据 = 当前订阅的频道缓存；切换订阅后列表变化

### 3.2 播放页 — 全屏
- 确定键：弹出/收起控制条（上一台、下一台、返回、收藏、节目单入口）
- 菜单键：切换信息浮层——视频编码、分辨率、码率、音频编码
- 底部半透明信息条：频道名 + 正在播出 + 下一节目，几秒后淡出

### 3.3 节目单页
- 从播放页/首页进入，显示所选频道当天全部节目列表，高亮正在播出

### 3.4 订阅管理页
- 订阅列表：名称、频道数、当前订阅标记、EPG 配置状态
- 新增/编辑：名称 + m3u 地址 + EPG 地址（屏幕键盘输入）
- 保存时下载测试连通性，显示解析出的频道数
- 设为当前订阅 / 删除订阅

### Manifest 电视声明
- 首页 Activity 带 `category.LEANBACK_LAUNCHER` intent（否则 TCL Android TV 桌面可能不显示图标）+ 桌面横幅（banner）资源 + `uses-feature android.software.leanback` / `android.hardware.touchscreen not required`

## 4. 数据模型（Room）

| 表 | 字段要点 | 说明 |
|---|---|---|
| `subscriptions` | id、名称、m3uUrl、epgUrl(可空)、isCurrent | 仅一条 isCurrent 为真 |
| `channels` | id、subscriptionId、name、logoUrl、groupTitle、streamUrl、tvgId、sortIndex | 解析 m3u 的缓存，按订阅隔离；tvgId 供 EPG 匹配 |
| `favorites` | id、name、logoUrl、streamUrl、addedAt | 全局共享，不挂订阅外键，按 (name, streamUrl) 去重 |
| `epg_programmes` | 复合主键 (channelKey, startTime, title)、description、endTime | 只存今天+明天；channelKey = tvg-id 优先，频道名兜底；一个节目多键→多行 |
| `app_state` | key、value | 最后播放频道等简单状态 |

## 5. 关键流程

1. **添加/编辑订阅**：下载 m3u → 解析（`#EXTM3U`/`#EXTINF`，提取 `tvg-name`/`tvg-logo`/`group-title`，兼容无扩展信息的简式 m3u/txt）→ 写 `channels`（替换该订阅旧数据）→ 显示频道数
2. **切换当前订阅**：更新 isCurrent → 首页用缓存刷新列表，不重新下载；提供手动"刷新频道"
3. **播放**：`channels.streamUrl` → MediaItem；`.m3u8` 走 HLS，其余走 Progressive（Media3 原生支持 mpeg-ts）
4. **EPG**：进首页/播放页时后台下载解析 XMLTV（XmlPullParser 流式）→ 写表 → 播放页按 channelKey + 当前时间查正在播出/下一节目
5. **频道↔EPG 匹配**：tvg-id 优先，频道名其次；匹配不到则隐藏节目信息，不影响播放
6. **码率/格式浮层**：播放中每秒轮询 `player.videoFormat`/`audioFormat`（宽高、编解码器、码率；HLS 直播取当前变体码率）

## 6. 错误处理

| 场景 | 策略 |
|---|---|
| m3u/EPG 下载失败 | 提示原因（超时/DNS/HTTP 码），保留旧缓存，可重试 |
| 流播放失败/中断 | Media3 错误回调 → 错误条 + "下一频道"跳过；播放中断线由 Media3 默认加载重试策略（指数退避，直至恢复）自动重连 |
| http 明文流 | networkSecurityConfig 允许 cleartext（直播流绝大多数为 http） |
| EPG 失败/未匹配 | 静默隐藏节目信息，下次进首页自动重试 |
| 超大 XMLTV | 流式解析 + 只保留今天/明天，防 OOM |

## 7. 测试策略

- JVM 单元测试：m3u 解析器（标准/简式/脏数据）、XMLTV 解析器（流式、过期过滤）、匹配逻辑、收藏去重
- 真机验收清单（TCL 电视手动执行，无法在电脑完全替代）：遥控器焦点导航、多订阅切换、播放起/切/停、信息浮层数值、收藏、节目单页
- 完成标准：单测全绿 + debug APK 构建成功；真机项逐条待用户装机确认

## 8. 交付物

- 可安装 debug APK（后续可选 release 签名）
- 源码工程（Gradle 构建可复现）
