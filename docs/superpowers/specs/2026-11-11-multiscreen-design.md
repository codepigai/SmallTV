# 多屏观看（Multi-Screen）设计 — 2026-11-11

状态：用户已确认（2026-11-11 会话），待实施
关联：v1.0（tag `v1.0` @ `6dd2713`）之上的新功能

## 1. 背景与意图

核心场景：大型杯赛小组赛末轮多场比赛同时进行，用户想一屏同看（通常 2 路，偶尔 4 路）；家人各看各的频道为次要场景。

**探针实测结论（2026-11-10/11，TCL MT5879 + 本地中继 iPTV 源）**：
- 电视硬解能力足够：`OMX.MS.AVC.Decoder` 并发实例 ≥3 路 1080i H.264 同播，25fps 零丢帧；第 4 实例可初始化。
- 真正瓶颈是**源的并发脆弱性**：中继服务器对上游（央视频 CDN）多路新会话并发拉流时限流（间歇 403），多路同时冷启动会互相拖累甚至把已播会话卡死；低频渐进建立会话则稳定。
- 因此产品决策：**2 路是体验基准，4 路是目标能力**；App 必须做错峰起播与格间故障隔离，任何一路失败不得牵连其他路。

## 2. 目标 / 非目标 / 反目标

**目标**
- G1 同屏 2 或 4 路直播并发播放（3 路形态不存在：入口仅 2/4 两档，想开 3 路即留空格）
- G2 遥控器选中单个格子，对选中格独立换台
- G3 声音单路策略：默认归最后一次选台的格子；选中格可按菜单键显式切换
- G4 短按 BACK 逐层收起，长按 BACK（≥700ms）退出多屏
- G5 每格独立断流防护与失败重试，格间互不影响
- G6 首页一键进入（「多屏观看」页签）

**非目标**
- 多屏页不做：节目单/EPG 面板、收藏管理、码率信息面板、格子拖拽换位、多路音量混音、跨格记忆会话（退出即全部释放，再进为空网格）
- 不做失败格自动降码率/预览降级（若源只扛得住 2 路，第 3/4 格显示重试状态即可）
- 不做播放中切换布局（2↔4 靠退出重进；布局选择每次进入 Activity 都弹，进程内不记忆）
- 不改单屏 `PlaybackActivity` 任何行为

**反目标**
- 4 路同播把中继服务器打进限流黑名单（已实测会发生）→ 用错峰起播+退避上限约束
- 退出模式误触（BACK 短按绝不能退出多屏）

## 3. 用户流程

1. 首页顶部第 4 个页签「多屏观看」→ `MultiScreenActivity`
2. 自动弹出布局选择层（**每次进入 `MultiScreenActivity` 都弹**；居中两个大按钮：`2 屏` / `4 屏`，方向键+确定）→ 选择后屏幕立即切分
3. 各格初始：黑底 + 居中灰字「确定 选频道」+ 格号角标（1-4）
4. 方向键移动选中格（白描边框+轻微缩放高亮）→ 按确定 → 右侧滑出频道抽屉 → 选定频道 → 该格起播，**声音自动切给该格**，抽屉收起，焦点回格子
5. 播放中换台 = 再次对选中格按确定重选频道
6. 选中某格按菜单键 = 把声音切给该格（喇叭图标移动，不重开播）
7. 短按 BACK：抽屉开着 → 只关抽屉；抽屉已关 → 屏幕下方 Toast「长按返回键退出多屏」，不退出
8. 长按 BACK ≥700ms：直接退出多屏回首页（按住 400ms 时先亮提示「继续按住退出多屏」）
9. 从选择层按 BACK 也按第 8 条长按规则处理（防误触直接离开页面）；**不做播放中换布局**——想从 2 屏改 4 屏（或反之）只能长按 BACK 退出后重进（见非目标）

## 4. 键位总表

| 键 | 行为 |
|---|---|
| 方向键 | 抽屉收起时：在格间移动焦点（2 屏左右；4 屏田字上下左右，绕回）；抽屉内：正常列表导航 |
| 确定 | 空格：打开该格频道抽屉；已播格：打开抽屉换台（列表定位到当前频道） |
| 菜单 | 声音切换到当前选中格（仅当该格有频道时） |
| BACK 短按 | 逐层收起：抽屉 → （已无可收）Toast 提示长按退出 |
| BACK 长按 ≥700ms | 退出多屏（释放全部播放器） |
| 音量键 | 不拦截（系统全局音量，各路共用） |

## 5. 布局规格

- 全屏黑底。顶部保留 32dp 细信息条：左「多屏 2路/4路」，中「选中：CCTV5体育」，右「🔊 CCTV5体育」+ 操作提示淡字。
- **2 屏**：水平等分（各 50% 宽），`PlayerView` `RESIZE_MODE_FIT` → 16:9 内容在各半屏内自然居中，黑边对称（即用户要求的「居中」）。
- **4 屏**：田字 2×2 等分，同样 FIT。
- 选中格：2dp 白色描边（`focus_bg` 风格新 drawable），未选中透明描边占位防抖。
- 每格叠加层：左上角当前频道名（半透明黑底白字，常驻）；中央状态文本（起播/缓冲/重连提示）；右下角小喇叭（仅声音格显示）。

## 6. 组件与数据流

```
MultiScreenActivity
 ├─ 布局选择层 (LayoutPickerOverlay)     —— 2屏/4屏两按钮
 ├─ 网格容器：CellView × N (2|4)
 │    CellView = FrameLayout{ PlayerView(SurfaceView) + 名称条 + 状态文本 + 喇叭角标 + 描边 }
 ├─ ChannelDrawer（右侧滑出，宽 55%，两列：分组 | 频道）
 │    数据 = repo.currentSubscription() → repo.groups()/channels()，形态对齐首页双列
 │    （「全部」伪组 = null 即不过滤，同 HomeActivity）。分组列含「收藏」伪组：
 │    取全局 repo.favorites()（我的决策：与首页「收藏夹」页签同一数据源，可含跨订阅条目，
 │    播放可行性已被首页 playFavorite 证实）；Favorite 行映射为简化行（tvgId=null，
 │    抽屉无 EPG 故无影响），列表定位按 streamUrl 匹配当前格频道
 └─ AudioRouter（进程内单例，Activity 持有）
      setAudioOwner(i)：对 owner 外所有播放器 volume=0，owner volume=1；记喇叭角标与顶栏文本

每格状态机 CellPlayer（对应一格）：
  EMPTY →(选台) PREPARING → READY ⇄ BUFFERING；READY/BUFFERING →(fatal) RETRYING(n) → PREPARING
  播放器构建与单屏一致：DefaultMediaSourceFactory(DefaultHttpDataSource.Factory())
    .setLiveTargetOffsetMs(15_000).setLoadErrorHandlingPolicy(IptvLoadPolicy())
  所有播放器 setAudioAttributes(AudioAttributes.DEFAULT, handleAudioFocus=false)——焦点完全交给 AudioRouter
  fatal 重连：复刻单屏第 3 层退避（2s 起倍增封顶 15s，上限 20 次），文案沿用「Xs 后第 n 次自动重连」
  换台 = 本格 player.setMediaItem+prepare，不影响他格；重连成功清计数并更新名称条
```

**错峰起播（防源限流，关键）**：Activity 维护 `startGate` 队列——任何 `prepare()` 请求若距上一次 prepare 不足 **800ms** 则入队延迟执行。用户在抽屉里快速连续给多格选台时自动错峰；单格重连退避本身已错峰。

**生命周期**：onStop 全部 `playWhenReady=false`；onDestroy 全部 `release()`；不持久化任何状态。

## 7. 声音规则（用户规则 5 的展开）

- 恰有一个「声音格」；新格首次选台成功起播即成为声音格（= 最后选择）
- 选中格按菜单：该格成为声音格（不重启流）
- 声音格换台：仍是声音格；声音格 fatal 重连中：声音暂静，起播后自动恢复（owner 不变）
- 空格不拥有声音；退出前最后一个声音格不做记忆
- **系统 AudioFocus：明确不申请（有意简化）**。所有播放器 `handleAudioFocus=false`，AudioRouter 只做 volume 0/1 路由，不包 AudioFocusRequest。接受的后果：其他应用抢占音频焦点/出声时，多屏不暂停不闪避（音量本是系统全局，会叠声）；页面退到背景的暂停由 onStop 承担（验收 8）。多播放器各自申请焦点是互踩根源，故整体退出该体系。

## 8. 入口改动

`HomeActivity`：`tabs` 增加第 4 项 `tabMulti`「多屏观看」（`activity_home.xml` 顶部按钮组追加，样式沿用 `TabBtn`）→ 点击 `startActivity(MultiScreenActivity)`。首页其余行为零改动（`showTab` 的 0/1 语义不变，多屏不是 showTab 状态而是跳转）。

## 9. 新增/修改文件清单（实施边界）

- 新增 `ui/MultiScreenActivity.kt`（含 CellPlayer 状态机与 AudioRouter，可拆内部类，单文件 ≤400 行为宜）
- 新增 `ui/ChannelDrawer.kt`（或 Activity 内部实现——实施者定，倾向独立小类便于读）
- 新增 `res/layout/activity_multi_screen.xml`、`res/layout/view_channel_drawer.xml`（复用 `item_channel.xml`/`item_group.xml` 行样式）
- 新增 `res/drawable/cell_focus_frame.xml`；改 `res/values/strings.xml`（文案）
- 改 `res/layout/activity_home.xml` + `ui/HomeActivity.kt`（页签+跳转，各 ~10 行）
- 改 `AndroidManifest.xml`（注册 Activity，landscape + configChanges 同 `PlaybackActivity`）

**不动**：`PlaybackActivity`、`Repository`/Room schema、`net/*`、订阅与收藏逻辑、单屏键位。

## 10. 验收标准（真机）

1. 首页出现「多屏观看」，进入默认先见布局选择；选 2 屏屏幕左右对分
2. 2 屏各配一路球赛：两格同播、声音仅一路、喇叭角标与顶栏正确
3. 4 屏田字格 + 快速连选 4 台：不触发服务器拉黑（错峰生效），最终全部起播或个别格持续退避重试
4. 方向键可在格间移动，白框跟随；确定弹抽屉，抽屉内选台该格换台且声音跟去
5. 选中另一格按菜单：声音切换即时生效，无重连
6. 拔掉某一路流源（改 URL 或停中继）：仅该格重连循环，其余照常播
7. 短按 BACK 只收抽屉/出提示；长按 ≥700ms 回首页；回首页再进多屏**重新弹布局选择**且为全新空网格
8. 多屏中按系统 HOME 再返回：画面恢复播放（onStart 恢复 playWhenReady）
9. 回归：单屏播放、EPG、收藏、订阅页行为与 v1.0 一致

## 11. 风险与对策

| 风险 | 对策 |
|---|---|
| 源并发限流（已实证） | 错峰 800ms；格间隔离；重试有上限有文案；文档向用户说明「第 3/4 路偶发起播慢属源限制」 |
| MT5879 Android 11 OMX 实例数在个别流型（如 H.265/4K 混入）下不足 | 格状态机保证失败格不崩他格；不承诺 4 路全类型混播 |
| 长按 BACK 与系统「BACK 长按=主页」某些 ROM 行为冲突 | 在 `onKeyDown` 拦截并消费 BACK（DOWN/UP 成对），探针已证 BACK 可拦截 |
| 抽屉与 4 路 SurfaceView 同屏渲染压力 | 抽屉期各路继续播（FIT 缩略尺寸小）；若实测掉帧再优化为抽屉期暂停起播以外的流（留作实施期决策点，默认不做） |

## 12. 留给实施的决策（不改变本设计语义）

- ChannelDrawer 是否单独文件/类（倾向独立）
- 布局选择层用 overlay 还是首屏 Activity 状态（本设计按 Activity 内 overlay；弹出时机已定死为每次进入都弹）
- 提示文案微调；toast 时长；选中格缩放值（如不做缩放仅描边也可）

## 13. 构建者评审记录（2026-11-11）

fresh subagent 评审提出的 3 问均已闭环，无需用户拍板：①布局选择层时机矛盾→定为每次进入都弹、不做中途换布局（§3.2/3.9、§2）；②收藏伪组语义→全局收藏（§6，我的决策）；③AudioFocus 真空→显式声明不申请、纯音量路由及其后果（§7）。
