# SmallTV 视觉改进批次 — 设计（2026-11-12）

## 意图与原因

v1.2 功能验收通过后做一轮纯视觉打磨：品牌化（SmallTV 命名 + 用户提供的 logo）、顶栏图标化圆角卡片、全局统一「思源柔黑体」（GenJyuuGothic）、订阅页间距与详情卡片化、设置页照 config.png 学两栏布局。不改任何交互行为。

用户已确认的决策（原话要点）：
- 顶栏「都找一些 SVG 的小图标放文字前面，每个框都是圆角」。
- 首页左列「再窄一些」。
- 字体用项目根目录 `GenJyuuGothic-Normal-2.ttf`（用户称「思源柔黑体」）。
- 订阅管理「左侧每个文本框都是圆角，框间距大一些；右边上面的配置信息格式化一下，也用框框起来，显示的底色不一样，跟下面的按钮框左对齐」。
- 配置页「学下布局（config.png），现在设置里有三项整行显示太长了」→ 左侧大 logo 插画 + 右侧收窄卡片行的两栏。
- 回放六项全案时用户「没有问题，继续」→ 上述理解与默认拍板值已获认可。

## 目标
1. 六项视觉改动全部落地，见 §①-§⑥。
2. 键位、导航跳转、数据逻辑零改动（设置页行点击 toggle 行为原样保留）。
3. 全量回归 63/63 绿 + release 包重出（**不升版**：versionCode 3 / 1.2 不动，用户未下升版指令）。

## 非目标
- 播放页、多屏页、EpgPanel/ChannelDrawer 的布局不动（抽屉/节目单内行背景随全局 focus_bg 圆角变化属可接受的全局副作用）。
- 不改配色体系（底 #1A1A2E、焦点红 #E94560、选中深蓝 #0F3460、灰蓝 #8899BB 沿用）。
- 设置页不加顶部导航（保持独立 Activity 跳转模式）。
- 不做自适应图标（adaptive icon）分层遮罩——logo 本身是深底圆角构图，直接整图作图标。

## 约束
- minSdk 23：不得使用 API 28+；主题 `android:fontFamily` 引用 res/font 需 API 26+，电视为 Android 9+ 生效，低版本静默回落系统字体（验收点，若回落再议程序化 Typeface 方案）。
- 零新增依赖；无 Robolectric/androidTest，本批无新增 JVM 可测纯逻辑（全部是资源/布局/样式），验证=编译 + 全量回归 + 真机。
- 素材不抠图不裁剪（无图像工具链）：JPG 原图直接入 res。

## ① 命名与图标
- `strings.xml`：`app_name` → `SmallTV`。
- `SmallTV-logo.jpg` → `res/drawable-nodpi/app_logo.jpg`（nodpi 防 density 缩放；manifest `android:icon="@drawable/app_logo"`）。旧 `drawable/app_icon.xml` 删除。
- `drawable/banner.xml` 重写为 layer-list：底色 `#0B1130`（logo 深底取样）+ `app_logo` gravity=center、宽 180dp（16:9 横幅 320x180 内居中）。

## ② 顶栏：图标 + 圆角
- 新增 5 个 24dp 矢量图标（单色，`fillColor="#FFFFFF"`，由 TextView drawableTint 不处理——直接白色；未选中态整页签文字本就全白，无需双色）：
  `ic_tab_list`（三横线）/`ic_tab_star`（五角星）/`ic_tab_folder`（文件夹）/`ic_tab_grid`（2x2 四宫格）/`ic_tab_gear`（齿轮）。
- `activity_home.xml` 五个 TabBtn 各加 `android:drawableStart` + `android:drawablePadding="6dp"`（padding 入 TabBtn style）。
- `focus_bg.xml` 三态圆角 6dp → **12dp**（全局：页签/按钮/卡片/抽屉与节目单行统一受益）。

## ③ 首页左列
- `groupList` 宽 260dp → **200dp**。

## ④ 全局字体
- `GenJyuuGothic-Normal-2.ttf` → `res/font/genjyuu.ttf`（资源名小写字母数字下划线）。
- `themes.xml` Theme.IptvTv 加 `<item name="android:fontFamily">@font/genjyuu</item>`。
- 字体文件保留在根目录（用户素材，不删），但**不提交进 git**（.ttf 二进制入 res/font 即可，根目录原件属未跟踪素材，同 apk 惯例）。
- 预期 APK +≈5MB，用户已接受。

## ⑤ 订阅管理
- `item_sub.xml`：`layout_marginBottom` 14dp → **20dp**（圆角随全局 focus_bg 12dp）。
- `activity_subs.xml` 右栏：`subDetail` 裸 TextView → 圆角卡片：新 `drawable/card_bg.xml`（solid `#202A55`、radius 12dp，与页底 `#1A1A2E`、focus_bg 常态 `#22223A` 区分出「底色不一样」），padding 16dp、match_parent 宽（与下方按钮列同容器左对齐）。
- 卡片内文案格式化（`updateDetail()` 调整）：`名称：…` / `地址：…（maxLines=2 ellipsize=end 防长 URL 撑爆）` / `N 频道 · HH:mm 刷新`（subMetaText 现成输出，不新增逻辑） / `EPG：已配置|未配置`，标题行「订阅信息」小字灰。地址行超长是已知风险，故 maxLines 截断。空订阅态：沿用现有「还没有订阅：点右上『＋ 添加订阅』…」一行，放进卡片内。
- 按钮列：按钮宽保持 wrap_content，`layout_marginTop` 10dp → 14dp；卡片与首按钮间距 16dp。

## ⑥ 设置页两栏（学 config.png 布局）
- `activity_settings.xml` 重写为 horizontal 两栏（页面 padding 32dp）：
  - 左栏 weight=1：`ImageView`（app_logo，adjustViewBounds，maxWidth 420dp，垂直居中）。
  - 右栏宽 480dp：标题「设置」24sp → 分组小标「通用」13sp `#8899BB` → 三张卡片行（行距 14dp）。
- 卡片行结构（三行同构，仅图标与 id 异）：可聚焦 clickable `LinearLayout`（background=新 `setting_row.xml` selector：focused `#E94560` / 常态 `#202A55`，radius 12dp，padding 16dp）内部横排：图标 20dp + 名称 TextView（weight=1，17sp 白，paddingStart 10dp）+ 值 TextView（15sp `#8899BB`）+ `›`（18sp `#667788`，paddingStart 12dp）。
  - 行图标：`ic_display`（显示器轮廓，显示方式）/ `ic_tab_grid`（复用，宫格路数）/ `ic_info`（圆 i，版本）。
  - 行的可点击根 id：`rowMode` / `rowSpan`（LinearLayout）；值 TextView id：`rowModeValue` / `rowSpanValue`；版本行 id `rowVersion`（LinearLayout，不可聚焦不可点）+ 值 id `rowVersionValue`。**版本行不放 ›**（无后续动作，builder-check 共识）。
  - 值文本保留「（确认键切换）」提示（并入 rowModeValue/rowSpanValue 文本，可发现性零损失）。
- `SettingsActivity.kt`：点击注册与文本赋值由整行 TextView 改为值 TextView（toggle/循环切换逻辑、onResume 刷新、packageInfo 取版本全部原样保留，只改 view 引用与「名称：值」拆分为名称静态+值动态两处 text）。

## 键位/行为回归面
- 本批只动：strings/themes/manifest/若干 drawable/layout + SettingsActivity 视图绑定。焦点顺序：设置页三行的 nextFocus 不设（列表纵向天然）；订阅页结构不变仅包裹详情卡。
- 首页 tab 加图标后宽度略增——TabBtn minWidth 160dp 不变，5 tab 总宽 ≈ 现有 +（图标+间距≈30dp）×5，屏宽 960dp 富余（终审曾提 908dp 贴边风险，此改动反而需注意；验收点：右缘「设置」页签完整可见）。

## 验收清单（真机）
1. 电视启动器：应用名 SmallTV、横幅为深底 logo、图标为 logo。
2. 全部界面文字为思源柔黑体观感（若仍系统字体→报回，转程序化 Typeface 方案；**计划里此项排最早出包验证**——主题 android:fontFamily 在个别 OEM TV ROM 上可能不透传，是唯一真机风险）。
3. 顶栏 5 页签：图标+文字、圆角明显、焦点/选中态正常；右缘不裁切。
4. 首页左列变窄、上下键路由不变；画廊/列表切换不回归（上批①）。
5. 订阅管理：卡片圆角间距、右栏详情卡片底色、长 URL 不撑爆、按钮功能与焦点不变。
6. 设置页：两栏、三张卡片行、图标值箭头、点击切换即时生效、返回正常。
7. 覆盖安装 v1.2→新包数据保留；播放页键位（上批②③）不回归。

## 我自己拍的板（错了可改）
- 图标风格：白色单色线性矢量，无描边动画；页签选中/焦点仍用现有深蓝/红底（config.png 是白胶囊深字，属配色体系，不动）。
- 首页左列取 200dp（"再窄一些"的量化）。
- 圆角统一 12dp、卡片底色统一 `#202A55`（含设置行与订阅详情卡，一个语言）。
- banner 深底 `#0B1130` 取自 logo。
- 版本行不做可聚焦（无操作），但同样卡片化保持整齐。
- 字体不瘦身不子集化（无工具链约束），整包入 res/font。
