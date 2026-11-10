# 多屏观看（Multi-Screen）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 v1.0 之上新增「多屏观看」：同屏 2/4 路直播并发、遥控选格换台、单路声音、短按 BACK 收起 / 长按 ≥700ms 退出。

**Architecture:** 新 `MultiScreenActivity`（网格 + 布局选择 overlay + 右侧频道抽屉）内每格一个独立 ExoPlayer（复刻单屏三层断流防护），可测纯逻辑（错峰门控、音量路由、退避、抽屉数据）抽到 `MultiScreenLogic.kt` 做 JVM 单测；`HomeActivity` 只加一个跳转页签。

**Tech Stack:** Kotlin、传统 Views（FrameLayout/LinearLayout/RecyclerView）、media3 1.2.1（ExoPlayer/PlayerView）、Room（现有 Repository，不改）。

**Spec:** `docs/superpowers/specs/2026-11-11-multiscreen-design.md`（构建者评审已闭环，本计划逐节对应）

## Global Constraints

- 不新增任何依赖；minSdk 23 / target 34 / compileSdk 34 不动；`PlaybackActivity`、`Repository`、`net/*`、单屏键位零改动（spec §9「不动」清单）。
- media3 1.2.1 API 用前先 `javap` gradle 缓存 AAR 验签名（记忆规则）。已由探针在真机验证过、可直接采用的三例：`player.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)` 编译+运行均正常；**`pv.player = p` 必须赋值**（漏了=永久 BUFFERING，探针血泪）；`RESIZE_MODE_FIT` 常量在 `androidx.media3.ui.AspectRatioFrameLayout`。
- UI 文案全部中文字面量写死在代码/布局里（与现有 4 个页面一致；**偏离 spec §9**：不动 strings.xml）。
- 构建前缀（PowerShell 5.1，无 `&&`）：`$env:JAVA_HOME='D:\app\bigtext\tools\jdk17';`
  - 单测：`.\gradlew.bat testDebugUnitTest --tests com.smallpig.iptv.MultiScreenLogicTest --offline`
  - 编译：`.\gradlew.bat assembleDebug`；release：`.\gradlew.bat assembleRelease -x lintVitalAnalyzeRelease -x lintVitalRelease`
- 提交：分支 `feature/multiscreen`；`git add` 必须显式列路径（**永不 `git add .`**，防带入 `.claude/`；`keystore.properties`、`keystore/` 永不入库）；消息前缀 `[feat]`/`[fix]`/`[docs]`，末尾空行后加 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。合入 main / 推送须用户单独批准。
- 真机验证唯一路径：构建 APK 交用户 USB 安装（TCL ROM 拦截 adb install，勿再尝试）。开发中期各任务只要求编译通过 + 逻辑单测绿；行为验收集中在 Task 6。
- 布局选择层**每次进入 Activity 都弹**（spec §3.2/§13）；不做播放中换布局；不持久化任何状态。

## Review Focus

计划中的单测覆盖不到的输入类，Task 3–5 实现与 Task 6 验收必须逐条盯：

- **漏绑 `pv.player`**：任一格 PlayerView 未绑定播放器 → 该格永久缓冲且无报错——每格构建后立即断言式检查绑定在位。
- **BACK 事件配对**：长按计时器已触发 finish 后仍收到 UP，或 DOWN 被消费而 UP 漏给系统 → 退出半途卡死；短按绝不 finish，长按必 finish，成对消费。
- **焦点甩飞**：抽屉开/收后焦点必须回到选中格；4 格全空、单格已播等其他格场景下方向键不可死区；换台确认后 `requestFocus` 回网格。
- **播放器泄漏**：`onDestroy` 每格先 `pv.player = null` 再 `player.release()`，重复进出页面不残留（探针经验：解绑顺序错会崩或黑屏）。
- **错峰门控饿死**：某格持续 fatal 重连（每 ≥2s 一个 gate 请求）时，用户给另一格选台的 prepare 只会被推迟 ≤800ms 而非排队到无穷——gate 是"限流串行"不是"优先级队列"，重连退避天然错开即可。
- **无订阅空态**：`repo.currentSubscription() == null` 时进入多屏：网格正常切分，格中央显示「暂无订阅：请到『订阅管理』添加」，开抽屉显示同文案不崩溃。

## File Structure

| 文件 | 动作 | 职责 |
|---|---|---|
| `app/src/main/java/com/smallpig/iptv/ui/MultiScreenLogic.kt` | 新建 | 纯逻辑（无 Android 依赖）：StartGate / AudioRouter / backoffMs / PickRow / 抽屉分组过滤。JVM 可测 |
| `app/src/test/java/com/smallpig/iptv/MultiScreenLogicTest.kt` | 新建 | 上者的单测（包名与现有测试文件一致） |
| `app/src/main/java/com/smallpig/iptv/ui/MultiScreenActivity.kt` | 新建 | Activity：布局选择 overlay、网格与 CellPlayer 状态机、键位、AudioRouter/StartGate 接线（目标 ≤400 行） |
| `app/src/main/java/com/smallpig/iptv/ui/ChannelDrawer.kt` | 新建 | 右侧滑出频道抽屉（类结构仿 `EpgPanel.kt`） |
| `app/src/main/res/layout/activity_multi_screen.xml` | 新建 | 根 FrameLayout：信息条 + gridContainer + 布局选择 overlay + `<include layout="@layout/view_channel_drawer"/>` |
| `app/src/main/res/layout/view_channel_drawer.xml` | 新建 | 抽屉：宽 55%、分组列 + 频道列两个 RecyclerView（行样式复用 `item_channel.xml`/`item_group.xml`） |
| `app/src/main/res/drawable/cell_focus_frame.xml` | 新建 | 格描边 selector：state_selected → 2dp 白描边，默认透明描边占位 |
| `app/src/main/res/layout/activity_home.xml` | 修改 | tab 行追加 `tabMulti`（样式 `TabBtn`，文案「多屏观看」） |
| `app/src/main/java/com/smallpig/iptv/ui/HomeActivity.kt` | 修改 | `tabs` 行（:73）后加 tabMulti 点击 → startActivity（不进 showTab 体系） |
| `app/src/main/AndroidManifest.xml` | 修改 | 注册 MultiScreenActivity（`screenOrientation`/`configChanges` 照抄 PlaybackActivity 行 :25） |
| `app/build.gradle` | 修改（仅 Task 6） | versionCode 2 / versionName "1.1" |

---

### Task 1: 纯逻辑层 MultiScreenLogic + 单测

**Files:**
- Create: `app/src/main/java/com/smallpig/iptv/ui/MultiScreenLogic.kt`
- Create: `app/src/test/java/com/smallpig/iptv/MultiScreenLogicTest.kt`
- Modify: 无

**Interfaces:**
- Consumes: 现有数据类 `com.smallpig.iptv.data.Channel`（字段 name/streamUrl/tvgId/logoUrl/groupTitle）、`Favorite`（name/logoUrl/streamUrl/dedupeKey）、`com.smallpig.iptv.parse.FavoriteKey.of(name, streamUrl): String`
- Produces（Task 3/4 按这些精确签名调用；package `com.smallpig.iptv.ui`，文件内零 Android import）:

```kotlin
const val GROUP_FAV = "__fav__"                 // 抽屉伪组：收藏；「全部」用 null（同 HomeActivity currentGroup 语义）
data class PickRow(val name: String, val url: String, val tvgId: String?, val fav: Boolean)
fun channelsToRows(channels: List<Channel>, favKeys: Set<String>): List<PickRow>
fun favoritesToRows(favs: List<Favorite>): List<PickRow>   // tvgId 恒 null
fun drawerGroups(realGroups: List<String>, favCount: Int): List<String?>  // [null] + (favCount>0 → GROUP_FAV) + realGroups
fun filterRows(rows: List<PickRow>, group: String?, favRows: List<PickRow>): List<PickRow> // null→rows；GROUP_FAV→favRows；否则不可用（抽屉频道列先按组过滤 Channel 再转 Row，见下注）
fun backoffMs(retries: Long): Long              // min(2000L * (1L shl retries.coerceAtMost(3).toInt()), 15_000L)
class StartGate(minIntervalMs: Long, now: () -> Long, post: (Runnable, Long) -> Unit) { fun request(action: () -> Unit) }
class AudioRouter {
    fun attach(index: Int, setVolume: (Float) -> Unit); fun detach(index: Int)
    fun setOwner(index: Int)                    // 未 attach 或 -1 → 全员静音（owner=-1）；否则 owner=1f 其余 0f
    var owner: Int; companion object { const val NONE = -1 }
}
```

> 注（我的决策，简化抽屉过滤）：`filterRows` 只处理「全部/具体组」两种入参来自当前订阅频道行，收藏组直接取 `favoritesToRows(repo.favorites())`——抽屉实现（Task 4）调 `if (group == GROUP_FAV) favRows else filterRows(groupRowsOf(group), group, favRows)`。

- [ ] **Step 1: 建分支并提交 spec**

```powershell
git checkout -b feature/multiscreen
git add docs/superpowers/specs/2026-11-11-multiscreen-design.md
git commit -m "[docs] 多屏观看设计文档（探针结论+构建者评审闭环）" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

- [ ] **Step 2: 写失败单测 `MultiScreenLogicTest.kt`**（junit4，参照现有测试文件包名）

必须包含的用例与断言（值不许改）：
- `startGate_serializesByMinInterval`：fake now 恒 0、minInterval=800 → 连发 3 个 request，post 记录的 delay 依次 `0L, 800L, 1600L`（即使时钟不动，槽位也串行预留）
- `startGate_respectsClockAdvance`：now 从 0 变 500 → 第 2 个 delay=`300L`
- `audioRouter_onlyOwnerHasVolume`：attach 0/1/2（记录各自收到的音量）→ setOwner(1) → 音量分别 `0f, 1f, 0f`
- `audioRouter_attachAfterOwnerIsMuted`：setOwner(0) 后 attach(2) → 2 收到 `0f`
- `audioRouter_detachOwnerGoesSilent`：owner=0 时 detach(0) → owner==`AudioRouter.NONE` 且无人持有 1f
- `audioRouter_setNoneMutesAll`：setOwner(AudioRouter.NONE) → 全部 `0f`
- `backoffMs_growthAndCap`：`0L→2000, 1L→4000, 2L→8000, 3L→15000, 9L→15000`
- `drawerGroups_shape`：`drawerGroups(listOf("CCTV","体育"), 3) == listOf<String?>(null, GROUP_FAV, "CCTV", "体育")`；favCount=0 时无 GROUP_FAV
- `channelsToRows_marksFav`：favKeys 含频道 A 的 FavoriteKey → A 行 `fav=true`、B 行 `fav=false`、tvgId 透传
- `favoritesToRows_nullTvg`：url/name 透传、tvgId=null、fav=true
- `filterRows_byGroup`：group=null 全量；group="体育" 仅该组（入参已是组过滤后的行则原样返回——按上面注释的用法测「具体组传入对应行集」语义即可）

- [ ] **Step 3: 跑测试确认失败**

Run: `$env:JAVA_HOME='D:\app\bigtext\tools\jdk17'; .\gradlew.bat testDebugUnitTest --tests com.smallpig.iptv.MultiScreenLogicTest --offline`
Expected: 编译失败（unresolved reference: StartGate 等）

- [ ] **Step 4: 实现 `MultiScreenLogic.kt`**——按 Interfaces 签名逐个实现；StartGate 内部 `var lastStart = Long.MIN_VALUE / 2`，`request` = `due = maxOf(0, lastStart + minIntervalMs - now()); lastStart = now() + due; post(Runnable(action), due)`。

- [ ] **Step 5: 跑测试确认通过**（命令同 Step 3，Expected: 全绿）

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/smallpig/iptv/ui/MultiScreenLogic.kt app/src/test/java/com/smallpig/iptv/MultiScreenLogicTest.kt
git commit -m "[feat] 多屏纯逻辑层：错峰门控/音量路由/重连退避/抽屉数据模型+单测" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 资源、Activity 骨架与首页入口

**Files:**
- Create: `app/src/main/res/layout/activity_multi_screen.xml`、`view_channel_drawer.xml`、`app/src/main/res/drawable/cell_focus_frame.xml`
- Create: `app/src/main/java/com/smallpig/iptv/ui/MultiScreenActivity.kt`（仅骨架）
- Modify: `app/src/main/res/layout/activity_home.xml`（tab 行 :11-13 后追加）、`ui/HomeActivity.kt`（:73-76 附近）、`AndroidManifest.xml`（:25 后加一行）

**Interfaces:**
- Produces（Task 3–5 依赖的 id）：`R.id.multiInfoBar`（32dp 横条：内含 `multiModeText`/`multiSelText`/`multiAudioText` 三个 TextView）、`R.id.gridContainer`（FrameLayout，占信息条以下全部）、`R.id.pickerOverlay`（默认 gone 的全屏层：标题「多屏观看」+ 两个 focusable TextView `pickerTwo`「2 屏」/`pickerFour`「4 屏」+ 底部提示「长按返回键退出」）、抽屉 id：`drawerPanel`/`drawerGroupList`/`drawerChannelList`/`drawerTitle`。
- MultiScreenActivity 骨架：`setContentView(R.layout.activity_multi_screen)`；onCreate 末尾 `pickerOverlay.visibility = VISIBLE` 且 `pickerTwo.requestFocus()`（Task 3 前点击暂不做动作）。
- Manifest：`<activity android:name=".ui.MultiScreenActivity" android:screenOrientation="landscape" android:configChanges="keyboardHidden|screenSize|screenLayout|smallestScreenSize|orientation|navigation" />`

- [ ] **Step 1: 写三个新资源**

`cell_focus_frame.xml`：selector，state_selected → shape stroke 2dp `#FFFFFF`、solid `#00000000`、corners 0；默认 → stroke 2dp `#00000000`（占位防抖，spec §5）。
`view_channel_drawer.xml`：与 `view_epg_panel.xml` 同构——根 LinearLayout `@+id/drawerPanel`，`android:layout_width="520dp"`（TV 1080p density 1.5 → 全屏 960dp，520dp ≈ spec §6 的 55%；观感需调属 §12 细节授权）、`layout_height="match_parent"`、`layout_gravity="end"`、`orientation="vertical"`、`background="#F20B0E17"`、`elevation="8dp"`、`padding="20dp"`、`visibility="gone"`。内部竖排：`drawerTitle`（18sp 白）→ 横排 weight=1（`drawerGroupList` 宽 150dp + `drawerChannelList` 宽 0dp weight=1、marginStart 12dp，均 `focusable="false"`）→ 底部灰字「返回键关闭」（仿 epg panel）。
`activity_multi_screen.xml`：根 FrameLayout 黑底（`#000000`）：竖排 [multiInfoBar（32dp：modeText 左 / selText 中 weight=1 居中 / audioText 右，textSize 12sp，颜色 `#8899BB`）+ gridContainer（weight=1）]，随后 pickerOverlay（全屏 `#CC000000` 居中竖排，两个 TextView `style="@style/CtrlBtn"`），最后 `<include layout="@layout/view_channel_drawer" />`。

- [ ] **Step 2: 骨架 Activity + 首页入口 + Manifest 注册**

HomeActivity 改动（共 ~5 行）：`tabs` 定义行后加 `val tabMulti = findViewById<TextView>(R.id.tabMulti)`，点击 `startActivity(Intent(this, MultiScreenActivity::class.java))`；**不**加入 `tabs` 列表（不是 showTab 状态，spec §8）；activity_home.xml tab 行追加 `<TextView android:id="@+id/tabMulti" style="@style/TabBtn" android:text="多屏观看" />`。

- [ ] **Step 3: 编译验证**

Run: `$env:JAVA_HOME='D:\app\bigtext\tools\jdk17'; .\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 跑全量单测防回归** → `.\gradlew.bat testDebugUnitTest --offline` Expected: 全绿（既有 28 个 + Task 1 新增）

- [ ] **Step 5: Commit** `git add` 上述 7 个文件；`[feat] 多屏：布局/抽屉资源、Activity骨架、首页入口`（+Attribution）

---

### Task 3: 网格与 CellPlayer（构建/绑播放器/错峰/三层防护/路由接线）

**Files:**
- Modify: `ui/MultiScreenActivity.kt`
- Consumes: Task 1 `StartGate/AudioRouter/backoffMs`；Task 2 布局 id

**Interfaces（Activity 内私有成员，签名固定）：**

```kotlin
private class Cell(val index: Int, val root: android.widget.FrameLayout, val pv: PlayerView,
                   val nameLabel: TextView, val statusText: TextView, val speaker: TextView) {
    var player: ExoPlayer? = null; var row: PickRow? = null
    var reconnects = 0L; val reconnect = Runnable { /* doReconnect(this) */ }  // 实施者定形，保留这两个字段
}
private fun buildGrid(mode: Int)                       // mode=2 横排两格；4 为两行×两列；格内 PlayerView 程序化创建
private fun createCell(index: Int): Cell
private fun playInCell(cell: Cell, row: PickRow)       // setMediaItem+prepare 包进 gate.request；换台取消旧重连
private fun scheduleReconnect(cell: Cell, errorName: String)  // backoffMs 退避、>MAX_AUTO_RETRY(20L) 停手，文案「${ch.name}　播放失败：$errorName（${delay/1000}s 后第 ${cell.reconnects} 次自动重连）」写 cell.statusText
private fun onCellRecovered(cell: Cell)
private fun setAudioOwner(index: Int)                  // router.setOwner + 喇叭角标 + multiAudioText
```

- [ ] **Step 1: PlayerView 程序化构建与绑定**——`PlayerView(this).apply { useController = false; setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT) }`（`PlayerView(context, null, attrDefault)` 构造可用；若 1.2.1 无 `useController` Kotlin 属性则调 `setUseController(false)`——先 javap `androidx.media3.ui.PlayerView` 确认，缓存路径记忆规则）。叠加层（channelName 左上、statusText 中央、speaker 右下「🔊」默认 gone、root.background 黑 + `cell_focus_frame` 前景）按 spec §5 组装，`root.isFocusable = true`，格号角标（「1」–「4」，左上名字未选台时以「格 ${i+1}　确定 选频道」为中央文本）。
- [ ] **Step 2: 播放器构建（逐格复刻单屏 + 两处多屏差异）**——照抄 `PlaybackActivity.kt:151-156` 的 msFactory/player 构建；差异：`player.setAudioAttributes(AudioAttributes.DEFAULT, false)`（media3 版 AudioAttributes，探针已验）；`pv.player = p` **紧跟 build 之后**（Review Focus #1）；`router.attach(index, p::setVolume)`；初始音量随 owner。加 `Player.Listener`：`onPlayerError`（fatal → scheduleReconnect）、`onPlaybackStateChanged(STATE_READY)`/`onIsPlaying(true)` → onCellRecovered。**格内所有 Handler 回调统一用 Activity 的 `handler`，携带 cell 引用，onDestroy 前 `removeCallbacksAndMessages(null)`。**
- [ ] **Step 3: buildGrid + playInCell 走 gate**——`StartGate(minIntervalMs = 800L, now = { SystemClock.elapsedRealtime() }, post = { r, d -> handler.postDelayed(r, d) })`（spec §6 错峰）。playInCell：cancel 本格 reconnect、`row` 落格、`setMediaItem`+`prepare`+`playWhenReady=true` 包进 `gate.request`，`statusText` 置「起播中…」。
- [ ] **Step 4: 生命周期**——onStop：全部 `playWhenReady=false`；onStart：非空格 `playWhenReady=true`（Review Focus：参照 PlaybackActivity :185-186 的 `isInitialized` 守卫改为「cell.player != null」判空）；onDestroy：每格 `pv.player = null` 再 `player.release()` 再 `router.detach(index)`（Review Focus #4），`handler.removeCallbacksAndMessages(null)`。
- [ ] **Step 5: 编译 + 单测回归**（同 Task 2 Step 3/4 命令）
- [ ] **Step 6: Commit** `git add app/src/main/java/com/smallpig/iptv/ui/MultiScreenActivity.kt`；`[feat] 多屏：网格与逐格播放器状态机（错峰起播+三层防护+音量路由）`（+Attribution）

---

### Task 4: ChannelDrawer 滑出抽屉与选台接线

**Files:**
- Create: `ui/ChannelDrawer.kt`、（activity_multi_screen.xml 已 include，本任务填类实现）
- Modify: `ui/MultiScreenActivity.kt`（picker 选完布局后打开抽屉的通路暂以 `openDrawerFor(cell)` 为入口）

**Interfaces:**
- Consumes: Task 1 `PickRow/GROUP_FAV/drawerGroups/filterRows/channelsToRows/favoritesToRows`；repo `currentSubscription()/channels()/groups()/favorites()`；Task 3 `playInCell`
- Produces:

```kotlin
class ChannelDrawer(root: View, repo: Repository, scope: LifecycleCoroutineScope) {
    var isOpen = false; private set
    fun show(currentUrl: String?, onPick: (PickRow) -> Unit)  // 加载数据、滑入（仿 EpgPanel.show 的 translationX 220ms 动画）、定位到 currentUrl 行（indexOfFirst url==currentUrl）
    fun hide(): Boolean                                        // 未开返回 false（BACK 消费判定，同 EpgPanel.hide）
}
```

MultiScreenActivity 新增：`private fun openDrawerFor(cell: Cell)`（isOpen 时先 hide 完成再 show——用 `post` 串），抽屉 onPick → `playInCell(cell, row); setAudioOwner(cell.index); drawer.hide(); cell.root.requestFocus()`（spec §3.4：声音自动跟最后选台）。

- [ ] **Step 1: 实现 ChannelDrawer**——数据：`lifecycleScope` 协程内 `repo.currentSubscription()`；null → 频道列空、`drawerTitle` = 「暂无订阅：请到『订阅管理』添加」（Review Focus #6）；分组列 `drawerGroups(repo.groups(sub.id), favs.size)`，item 布局复用 `item_group.xml`（「全部」null → 文案「全部」，GROUP_FAV → 「收藏」）；频道列复用 `item_channel.xml`（`chNow` 不填或填组名，无 EPG/无 rtt，★ 用 PickRow.fav）。组点击 → 换 filterRows 重submit 频道列并把焦点送进频道列（同 HomeActivity groupAdapter 回调语义）。
- [ ] **Step 2: Activity 接线**——openDrawerFor + onPick 链路（上面签名）；抽屉开着时网格按键由 Task 5 统一分发，本步先保证确定键能开抽屉（对选中格）。
- [ ] **Step 3: 编译 + 单测回归**（同上）
- [ ] **Step 4: Commit** `git add` 2 文件；`[feat] 多屏：右侧频道抽屉与选台起播/换台接线`（+Attribution）

---

### Task 5: 键位全量（选布局/格子导航/菜单切声/BACK 长短按）与信息条

**Files:**
- Modify: `ui/MultiScreenActivity.kt`

**Interfaces:** Consumes: Task 2 picker id、Task 3 `setAudioOwner/buildGrid`、Task 4 `ChannelDrawer.hide(): Boolean`、Task 1 `AudioRouter.NONE`

- [ ] **Step 1: 布局选择**——`pickerTwo/pickerFour` 点击/确定：`buildGrid(2|4)`、藏 overlay、`selectCell(0)`（overlay 期间方向键靠两个按钮天然 focusable 导航，无需自绘）。
- [ ] **Step 2: 格子选中模型**——`private var selected = -1`；每格 `setOnFocusChangeListener { has -> if (has) selectCell(i) }`；`selectCell` 更新各 `root.isSelected`（描边切换）+ `multiSelText`（「选中：${row?.name ?: "空"}」）+ `multiModeText`（「多屏 ${mode}路」）。焦点移动交给方向键原生 focus search：buildGrid 时按 spec §4 绕回语义设 `nextFocus{Left,Right,Up,Down}`（2 屏左右互设；4 屏对角…田字上下左右互设；overlay 隐藏后首焦 `cells[0].root.requestFocus()`）。
- [ ] **Step 3: 确定/菜单**——onKeyDown：`DPAD_CENTER/ENTER`（repeatCount==0 且无 overlay 且焦点不在抽屉）→ `openDrawerFor(cells[selected])`；`MENU` → 选中格有 row 则 `setAudioOwner(selected)` + Toast「声音已切到 ${row.name}」，空格 Toast「本格还没有频道」。
- [ ] **Step 4: BACK 短/长按**——`onKeyDown(BACK)`：DOWN repeatCount==0 时记录 `backDownAt = SystemClock.uptimeMillis()`，`handler.postDelayed(backHint, 400)`（hint：状态条区域 Toast 或 overlay 文案「继续按住退出多屏」均可，拍板：小 Toast）、`postDelayed({ finish() }, 700)`；`onKeyUp(BACK)`：<700ms → remove 两个回调，然后 `drawer.hide() || Toast("长按返回键退出多屏")`（overlay 开着则直接 Toast）。DOWN/UP 均 return true（Review Focus #2 成对消费）。`backDownAt = -1` 重置。**默认 back 行为永不可达。**
- [ ] **Step 5: 编译 + 单测回归 + Commit**——`[feat] 多屏：遥控键位（布局选择/格子焦点/菜单切声/BACK长短按分层）`（+Attribution）

---

### Task 6: v1.1 真机验收与收尾

**Files:**
- Modify: `app/build.gradle`（versionCode 2、versionName "1.1"）
- Modify: 本计划与 spec 的状态行、`docs/` 交付记录；memory `project_iptv_status.md`

- [ ] **Step 1: 版本号 1.1 + release 构建**

Run: `$env:JAVA_HOME='D:\app\bigtext\tools\jdk17'; .\gradlew.bat assembleRelease -x lintVitalAnalyzeRelease -x lintVitalRelease`
Expected: `app\build\outputs\apk\release\app-release.apk` 存在且已签名（`keytool -printcert -jarfile` 别名 `iptv`）。复制到仓库根 `iptv-v1.1-release.apk`（仓库根 APK 不入库）。

- [ ] **Step 2: 交用户 USB 装机，按 spec §10 九项 + Review Focus 逐条真机验收**（含回归第 9 项）。用户报告问题 → 逐条 `[fix]` 提交回 Task 5 结构，重出包直至全绿。
- [ ] **Step 3: 收尾文档**——本计划标题下补「执行记录」小节（完成日期、验收结果、问题轮次）；更新 memory；`git add` 显式路径提交 `[docs] 多屏观看 v1.1 验收记录`。
- [ ] **Step 4: 提请用户批准合入 main**（记忆规则：合并需单独确认，不得自行 merge）

---

## Self-Review 记录（2026-11-11）

- **spec 覆盖**：§3 流程→T2/T3/T4/T5；§4 键位→T5；§5 布局→T2/T3；§6 组件/错峰/防护→T1/T3；§7 声音→T1(T3 接线)；§8 入口→T2；§9 文件清单→File Structure（strings.xml 偏离已在 Global Constraints 声明）；§10 验收→T6；§11 风险（错峰/隔离）→T1 单测+T3 Step3；§13 三决议→T1（收藏全局）/T5（每次弹 overlay）/T3 Step2（不申请焦点）。
- **签名一致性**：`PickRow/GROUP_FAV/StartGate/AudioRouter/backoffMs` 在 T1 定义、T3/T4 消费处同名同参；`drawer.hide(): Boolean`、`openDrawerFor`、`setAudioOwner` 与消费方一致；MAX_AUTO_RETRY=20L 与 PlaybackActivity 常量语义一致（本文件内自定，不改单屏）。
- **步骤可执行性**：每步一个可检查产物；T2 Step1 抽屉宽度已拍板 520dp（≈55%，观感微调属 §12 授权）。
