# Android TV IPTV 播放器 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 TCL 电视（Android 11–13）上的 IPTV 播放器：多订阅 m3u + XMLTV 节目单、全局收藏、播放信息浮层（格式/分辨率/码率）。

**Architecture:** Kotlin 单模块原生 App。纯 Kotlin 逻辑层（m3u/XMLTV 解析、匹配键）JVM 单测；Room 持久化订阅/频道/收藏/EPG；androidx.media3 播放；传统 Views 实现 4 个界面（首页/播放/节目单/订阅管理）。

**Tech Stack:** Kotlin 1.9.22、AGP 8.1.4、Gradle 8.5、androidx.media3 1.2.1、Room 2.6.1(kapt)、OkHttp 4.12.0、Glide 4.16.0、协程 1.7.3。

**规格文档:** `docs/superpowers/specs/2026-11-08-androidtv-iptv-design.md`

---

## 本机构建环境（重要，所有 gradle 命令都依赖）

| 组件 | 位置 |
|---|---|
| JDK 17 | `D:\app\bigtext\tools\jdk17` |
| Gradle 8.5 zip（做 wrapper 离线源） | `D:\app\bigtext\tools\gradle.zip` |
| Gradle 8.5 可执行 | `D:\app\bigtext\tools\gradle-8.5\bin\gradle.bat` |
| Android SDK（platform 34、build-tools 33.0.1、platform-tools） | `D:\app\bigtext\android-sdk` |

JAVA_HOME 与 SDK 路径都写进项目配置文件，**不要修改系统环境变量**。所有命令在项目根目录 `D:\app\androidtvapp` 用 PowerShell 执行。

## 文件结构总览

```
androidtvapp/
├── settings.gradle / build.gradle / gradle.properties / local.properties / .gitignore
├── gradlew.bat + gradle/wrapper/          (Task 1)
└── app/
    ├── build.gradle
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml         (Task 2 完整，含 5 个 Activity 声明)
        │   ├── res/                        (Task 2: 主题/图标/横幅/焦点背景/布局)
        │   └── java/com/smallpig/iptv/
        │       ├── App.kt                  (Task 5: 手写 DI 单例)
        │       ├── parse/M3uParser.kt      (Task 3)
        │       ├── parse/XmlTvParser.kt    (Task 4)
        │       ├── parse/Match.kt          (Task 4: 匹配键/收藏键/nowNext 纯函数)
        │       ├── data/Entities.kt        (Task 5)
        │       ├── data/Daos.kt            (Task 5)
        │       ├── data/AppDatabase.kt     (Task 5)
        │       ├── net/Fetcher.kt          (Task 6)
        │       ├── data/Repository.kt      (Task 6)
        │       └── ui/  Home/Playback/Epg/Subs/SubEdit Activity (Task 7-10)
        └── test/java/com/smallpig/iptv/
            ├── M3uParserTest.kt            (Task 3)
            ├── XmlTvParserTest.kt          (Task 4)
            └── MatchTest.kt                (Task 4)
```

---

### Task 1: 项目脚手架 + wrapper + 空工程可编译

**Files:**
- Create: `.gitignore`、`settings.gradle`、`build.gradle`、`gradle.properties`、`local.properties`
- Create: `app/build.gradle`、`app/src/main/AndroidManifest.xml`（最小）
- Create: `gradle/wrapper/*`、`gradlew.bat`（由 wrapper 任务生成）

- [ ] **Step 1: 生成 wrapper（离线源指向本机 gradle.zip）**

```powershell
cd D:\app\androidtvapp
$env:JAVA_HOME='D:\app\bigtext\tools\jdk17'
& "D:\app\bigtext\tools\gradle-8.5\bin\gradle.bat" wrapper --gradle-version 8.5 --distribution-type bin
```

Expected: 生成 `gradlew.bat`、`gradle\wrapper\gradle-wrapper.jar`、`gradle-wrapper.properties`。

- [ ] **Step 2: 改 `gradle/wrapper/gradle-wrapper.properties` 的 distributionUrl 为本地文件（避免重新下载）**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=file\:///D\:/app/bigtext/tools/gradle.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 3: 写 `.gitignore`**

```gitignore
.gradle
build/
app/build/
local.properties
.idea
*.iml
.kotlin
```

- [ ] **Step 4: 写 `settings.gradle`**

```groovy
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "IptvTv"
include ':app'
```

- [ ] **Step 5: 写根 `build.gradle`**

```groovy
plugins {
    id 'com.android.application' version '8.1.4' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
    id 'org.jetbrains.kotlin.kapt' version '1.9.22' apply false
}
```

- [ ] **Step 6: 写 `gradle.properties`（含本机 JDK 路径，换机器需改）**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.java.home=D\:/app/bigtext/tools/jdk17
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 7: 写 `local.properties`（git 忽略，只存在于本机）**

```properties
sdk.dir=D\:/app/bigtext/android-sdk
```

- [ ] **Step 8: 写 `app/build.gradle`**

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.kapt'
}

android {
    namespace 'com.smallpig.iptv'
    compileSdk 34

    defaultConfig {
        applicationId "com.smallpig.iptv"
        minSdk 23
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
    buildTypes {
        release { minifyEnabled false }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = '17' }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'

    implementation 'androidx.media3:media3-exoplayer:1.2.1'
    implementation 'androidx.media3:media3-exoplayer-hls:1.2.1'
    implementation 'androidx.media3:media3-ui:1.2.1'

    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.github.bumptech.glide:glide:4.16.0'

    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'

    testImplementation 'junit:junit:4.13.2'
    // 让 JVM 单元测试能使用 XmlPullParser（Android 上由系统框架提供）
    testImplementation 'xmlpull:xmlpull:1.1.3.1'
    testImplementation 'xpp3:xpp3_min:1.1.4c'
}
```

- [ ] **Step 9: 写最小 `app/src/main/AndroidManifest.xml`（Task 2 会替换为完整版）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application />
</manifest>
```

- [ ] **Step 10: 构建验证**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`，产物 `app\build\outputs\apk\debug\app-debug.apk`。
若依赖下载失败：报告具体 artifact 坐标，不要改用镜像/离线 hack，等用户处理网络。

- [ ] **Step 11: Commit**

```powershell
git add .gitignore settings.gradle build.gradle gradle.properties gradlew.bat gradle gradle\wrapper app
git commit -m "[build] Gradle 工程脚手架与 wrapper（AGP 8.1.4 / Kotlin 1.9.22）"
```

---

### Task 2: 资源、主题、Manifest 完整版 + 5 个占位 Activity

**Files:**
- Create: `app/src/main/res/values/strings.xml`、`res/values/themes.xml`
- Create: `app/src/main/res/drawable/app_icon.xml`、`banner.xml`、`focus_bg.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Modify: `app/src/main/AndroidManifest.xml`（替换为完整版）
- Create: `app/src/main/java/com/smallpig/iptv/ui/HomeActivity.kt`、`PlaybackActivity.kt`、`EpgActivity.kt`、`SubsActivity.kt`、`SubEditActivity.kt`（占位实现，Task 7-10 填充）

- [ ] **Step 1: `res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">IPTV 电视</string>
</resources>
```

- [ ] **Step 2: `res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.IptvTv" parent="Theme.AppCompat.NoActionBar">
        <item name="android:windowBackground">#1A1A2E</item>
        <item name="android:textColorPrimary">#FFFFFF</item>
        <item name="android:textColor">#DDDDDD</item>
    </style>
</resources>
```

- [ ] **Step 3: `res/drawable/app_icon.xml`（纯矢量，免 PNG 素材）**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#1A1A2E" android:pathData="M0,0h108v108h-108z"/>
    <path android:fillColor="#E94560" android:pathData="M22,36h64v38h-64z"/>
    <path android:fillColor="#FFFFFF" android:pathData="M50,48l16,8l-16,8z"/>
    <path android:fillColor="#0F3460" android:pathData="M40,80h28v5h-28z"/>
</vector>
```

- [ ] **Step 4: `res/drawable/banner.xml`（TV 桌面横幅）**

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#0F3460"/>
    <size android:width="320dp" android:height="180dp"/>
</shape>
```

- [ ] **Step 5: `res/drawable/focus_bg.xml`（遥控器焦点高亮，全局复用）**

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape><solid android:color="#E94560"/><corners android:radius="6dp"/></shape>
    </item>
    <item android:state_selected="true">
        <shape><solid android:color="#0F3460"/><corners android:radius="6dp"/></shape>
    </item>
    <item>
        <shape><solid android:color="#22223A"/><corners android:radius="6dp"/></shape>
    </item>
</selector>
```

- [ ] **Step 6: `res/xml/network_security_config.xml`（允许 http 明文直播流）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

- [ ] **Step 7: 完整 `AndroidManifest.xml`（替换 Task 1 的最小版）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:name=".App"
        android:label="@string/app_name"
        android:icon="@drawable/app_icon"
        android:banner="@drawable/banner"
        android:networkSecurityConfig="@xml/network_security_config"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.IptvTv">

        <activity android:name=".ui.HomeActivity" android:exported="true" android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name=".ui.PlaybackActivity" android:screenOrientation="landscape" />
        <activity android:name=".ui.EpgActivity" android:screenOrientation="landscape" />
        <activity android:name=".ui.SubsActivity" android:screenOrientation="landscape" />
        <activity android:name=".ui.SubEditActivity" android:screenOrientation="landscape" />
    </application>
</manifest>
```

- [ ] **Step 8: 5 个占位 Activity + App 空壳**

`App.kt`：

```kotlin
package com.smallpig.iptv

import android.app.Application

class App : Application()
```

每个 ui 占位文件同构（以 HomeActivity 为例，其余 4 个把类名与文案换掉）：

```kotlin
package com.smallpig.iptv.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "首页（待实现）"; setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f; setPadding(48, 48, 48, 48)
        })
    }
}
```

- [ ] **Step 9: 构建验证**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 10: Commit**

```powershell
git add app
git commit -m "[feat] 资源、TV Manifest 声明与 5 个界面占位"
```

---

### Task 3: M3uParser（TDD）

**Files:**
- Create: `app/src/main/java/com/smallpig/iptv/parse/M3uParser.kt`
- Test: `app/src/test/java/com/smallpig/iptv/M3uParserTest.kt`

解析规则（规格 §5.1）：支持标准 `#EXTINF`（提取 `tvg-id`/`tvg-logo`/`group-title`、逗号后显示名）、`#EXTGRP` 兜底分组、简式 `名称,http://…` 单行、裸 URL 行（名称取文件名）、忽略其它 `#` 注释；BOM 与 CRLF 兼容；EXTINF 后无 URL 则丢弃该 EXTINF。

- [ ] **Step 1: 写失败的测试 `M3uParserTest.kt`**

```kotlin
package com.smallpig.iptv

import com.smallpig.iptv.parse.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {
    @Test fun standardExtInf() {
        val text = "#EXTM3U\n" +
            "#EXTINF:-1 tvg-id=\"cctv1\" tvg-logo=\"http://l/1.png\" group-title=\"央视\",CCTV-1\n" +
            "http://s/1.m3u8\n" +
            "#EXTINF:-1 group-title=\"卫视\",湖南卫视\n" +
            "https://s/2.ts?token=ab,cd\n"
        val r = M3uParser.parse(text)
        assertEquals(2, r.size)
        assertEquals("CCTV-1", r[0].name)
        assertEquals("http://s/1.m3u8", r[0].url)
        assertEquals("http://l/1.png", r[0].logo)
        assertEquals("cctv1", r[0].tvgId)
        assertEquals("央视", r[0].group)
        assertEquals("湖南卫视", r[1].name)
        assertEquals("https://s/2.ts?token=ab,cd", r[1].url)
        assertEquals(0, r[0].order); assertEquals(1, r[1].order)
    }

    @Test fun simpleTxtNameCommaUrl() {
        val text = "CCTV-1,http://a/1.m3u8\nHunan TV,https://b/2.ts\n"
        val r = M3uParser.parse(text)
        assertEquals(2, r.size)
        assertEquals("CCTV-1", r[0].name); assertEquals("http://a/1.m3u8", r[0].url)
        assertEquals("未分组", r[0].group)
    }

    @Test fun bareUrlUsesFileName() {
        val r = M3uParser.parse("http://host/path/CCTV5.m3u8\n")
        assertEquals("CCTV5.m3u8", r[0].name)
    }

    @Test fun extGrpFallbackAndComments() {
        val text = "#EXTM3U\n#一些注释\n#EXTVLCOPT:http-user-agent=x\n#EXTGRP:卫视\nhttp://u/a.ts\n"
        val r = M3uParser.parse(text)
        assertEquals(1, r.size)
        assertEquals("卫视", r[0].group)
    }

    @Test fun extinfWithoutUrlDropped() {
        val r = M3uParser.parse("#EXTINF:-1 tvg-id=\"x\",孤儿频道\n")
        assertEquals(0, r.size)
    }

    @Test fun emptyExtInfNameFallsBack() {
        val r = M3uParser.parse("#EXTINF:-1,\nhttp://host/x.m3u8\n")
        assertEquals(1, r.size); assertEquals("x.m3u8", r[0].name)
    }

    @Test fun bomCrlfAndBlankLines() {
        val text = "﻿" + "#EXTM3U\r\n\r\n#EXTINF:-1 tvg-logo=\"\",A\r\nhttp://a\r\n\r\n"
        val r = M3uParser.parse(text)
        assertEquals(1, r.size); assertEquals("A", r[0].name)
        assertEquals(null, r[0].logo)
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.smallpig.iptv.M3uParserTest"
```

Expected: 编译失败（Unresolved reference: M3uParser）。

- [ ] **Step 3: 实现 `parse/M3uParser.kt`**

```kotlin
package com.smallpig.iptv.parse

data class ParsedChannel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val tvgId: String?,
    val order: Int,
)

object M3uParser {
    private data class Pending(
        val name: String?, val logo: String?, val group: String?, val tvgId: String?,
    )

    fun parse(text: String): List<ParsedChannel> {
        val clean = text.removePrefix("﻿")
        val out = ArrayList<ParsedChannel>()
        var pending: Pending? = null
        var extGrp: String? = null
        var order = 0
        for (raw in clean.split("\r\n", "\n")) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTINF") -> pending = parseExtInf(line)
                line.startsWith("#EXTGRP:") -> extGrp = line.substring(8).trim().ifBlank { null }
                line.startsWith("#") -> {}
                else -> {
                    val pair = splitNameUrl(line, pending?.name)
                    if (pair != null) {
                        val (name, url) = pair
                        out += ParsedChannel(
                            name = name.ifBlank { fallbackName(url) },
                            url = url,
                            logo = pending?.logo,
                            group = pending?.group ?: extGrp ?: "未分组",
                            tvgId = pending?.tvgId,
                            order = order++,
                        )
                    }
                    pending = null
                }
            }
        }
        return out
    }

    private fun parseExtInf(line: String): Pending? {
        val comma = line.indexOf(',')
        val attrs: String; val name: String
        if (comma < 0) { attrs = line; name = "" } else { attrs = line.substring(0, comma); name = line.substring(comma + 1).trim() }
        return Pending(
            name = name.ifBlank { null },
            logo = attr(attrs, "tvg-logo"),
            group = attr(attrs, "group-title")?.ifBlank { null },
            tvgId = attr(attrs, "tvg-id"),
        )
    }

    private fun attr(attrs: String, key: String): String? =
        Regex("$key=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1)

    private fun splitNameUrl(line: String, pendingName: String?): Pair<String, String>? {
        val ci = line.indexOf(',')
        if (ci > 0) {
            val u = line.substring(ci + 1).trim()
            if (isUrl(u) && !u.contains(' ')) return line.substring(0, ci).trim() to u
        }
        if (isUrl(line)) return (pendingName ?: "") to line
        return null
    }

    private fun isUrl(s: String) = s.startsWith("http://", true) || s.startsWith("https://", true) || s.contains("://")
    private fun fallbackName(url: String) = url.substringAfterLast('/').substringBefore('?').ifBlank { url }
}
```

- [ ] **Step 4: 运行确认通过**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.smallpig.iptv.M3uParserTest"
```

Expected: `BUILD SUCCESSFUL`，7 个测试全绿。

- [ ] **Step 5: Commit**

```powershell
git add app/src
git commit -m "[feat] M3uParser：标准/简式/裸URL/BOM 兼容，含单测"
```

---

### Task 4: XmlTvParser + 匹配/收藏纯逻辑（TDD）

**Files:**
- Create: `app/src/main/java/com/smallpig/iptv/parse/XmlTvParser.kt`
- Create: `app/src/main/java/com/smallpig/iptv/parse/Match.kt`
- Test: `app/src/test/java/com/smallpig/iptv/XmlTvParserTest.kt`
- Test: `app/src/test/java/com/smallpig/iptv/MatchTest.kt`

要点（规格 §5.4/5.5）：XmlPullParser 流式解析防 OOM；只保留 [今天 00:00, 后天 00:00) 区间节目；每个 programme 生成多匹配键 `id:<channel>` + `<display-name>` 的 `name:` 键；时间格式 `yyyyMMddHHmmss [.sss] [±HHMM]`，无时区按本地时区。

- [ ] **Step 1: 写失败的测试 `MatchTest.kt`**

```kotlin
package com.smallpig.iptv

import com.smallpig.iptv.parse.EpgMatcher
import com.smallpig.iptv.parse.FavoriteKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchTest {
    private data class Row(val s: Long, val e: Long, val t: String)

    @Test fun normalizeStripsSpacesAndUpperCases() {
        assertEquals("CCTV1", EpgMatcher.normalize("  CCTV 1 "))
        assertEquals("name:CCTV-1高清", EpgMatcher.nameKey("cctv-1高清"))
    }

    @Test fun keyForChannelPrefersTvgId() {
        assertEquals(listOf("id:x", "name:CCTV1"), EpgMatcher.keyForChannel("x", "cctv 1"))
        assertEquals(listOf("name:CCTV1"), EpgMatcher.keyForChannel(null, "CCTV1"))
        assertEquals(listOf("name:CCTV1"), EpgMatcher.keyForChannel("  ", "CCTV1"))
    }

    @Test fun favoriteKeyDedupesByNormalizedNameAndUrl() {
        assertEquals(FavoriteKey.of(" CCTV 1 ", "http://a"), FavoriteKey.of("cctv1", "http://a"))
        assertEquals(false, FavoriteKey.of("a", "http://1") == FavoriteKey.of("a", "http://2"))
    }

    @Test fun nowNextPicksCurrentAndFollowing() {
        val rows = listOf(Row(10, 20, "a"), Row(15, 25, "b"), Row(30, 40, "c"))
        val r = EpgMatcher.nowNext(rows, { it.s }, { it.e }, 16)
        assertEquals("b", r.first?.t); assertEquals("c", r.second?.t)
    }

    @Test fun nowNextBetweenProgrammes() {
        val rows = listOf(Row(10, 20, "a"), Row(30, 40, "c"))
        val r = EpgMatcher.nowNext(rows, { it.s }, { it.e }, 25)
        assertEquals(null, r.first); assertEquals("c", r.second?.t)
    }

    @Test fun nowNextEmpty() {
        val r = EpgMatcher.nowNext(emptyList<Row>(), { it.s }, { it.e }, 1)
        assertEquals(null, r.first); assertEquals(null, r.second)
    }
}
```

- [ ] **Step 2: 写失败的测试 `XmlTvParserTest.kt`**

```kotlin
package com.smallpig.iptv

import com.smallpig.iptv.parse.XmlTvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.util.TimeZone

class XmlTvParserTest {
    private fun xml(body: String) =
        """<?xml version="1.0" encoding="UTF-8"?><tv><channel id="cctv1"><display-name>CCTV-1</display-name></channel>$body</tv>"""

    private fun String.toStream() = ByteArrayInputStream(toByteArray())

    private fun stamp(dayOffset: Int, hour: Int, min: Int, zone: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(zone).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0)
        }
        fun p(n: Int, w: Int = 2) = n.toString().padStart(w, '0')
        return "${p(c.get(Calendar.YEAR), 4)}${p(c.get(Calendar.MONTH) + 1)}${p(c.get(Calendar.DAY_OF_MONTH))}" +
            "${p(c.get(Calendar.HOUR_OF_DAY))}${p(c.get(Calendar.MINUTE))}00"
    }

    @Test fun keepsTodayFiltersYesterdayAndFarFuture() {
        val now = System.currentTimeMillis()
        val body =
            "<programme channel=\"cctv1\" start=\"${stamp(-1, 8, 0)}\" stop=\"${stamp(-1, 9, 0)}\"><title>昨天</title></programme>" +
            "<programme channel=\"cctv1\" start=\"${stamp(0, 8, 0)}\" stop=\"${stamp(0, 9, 0)}\"><title>今天</title></programme>" +
            "<programme channel=\"cctv1\" start=\"${stamp(2, 3, 0)}\" stop=\"${stamp(2, 4, 0)}\"><title>大后天</title></programme>"
        val r = XmlTvParser.parse(xml(body).toStream(), now)
        assertEquals(1, r.size)
        assertEquals("今天", r[0].title)
    }

    @Test fun programmeKeysIncludeIdAndDisplayName() {
        val now = System.currentTimeMillis()
        val body = "<programme channel=\"cctv1\" start=\"${stamp(0, 8, 0)}\" stop=\"${stamp(0, 9, 0)}\"><title>新闻</title><desc>描述</desc></programme>"
        val r = XmlTvParser.parse(xml(body).toStream(), now)
        assertEquals(1, r.size)
        assertTrue(r[0].channelKeys.contains("id:cctv1"))
        assertTrue(r[0].channelKeys.contains("name:CCTV-1"))
        assertEquals("描述", r[0].description)
    }

    @Test fun parsesTimezoneOffset() {
        val ms = XmlTvParser.parseXmlTvTime("20261108120000+0800")!!
        val c = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00")).apply { timeInMillis = ms }
        assertEquals(2026, c.get(Calendar.YEAR)); assertEquals(10, c.get(Calendar.MONTH))
        assertEquals(8, c.get(Calendar.DAY_OF_MONTH)); assertEquals(12, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun skipsBrokenTimeAndBlankTitle() {
        val now = System.currentTimeMillis()
        val body =
            "<programme channel=\"cctv1\" start=\"garbage\" stop=\"x\"><title>坏时间</title></programme>" +
            "<programme channel=\"cctv1\" start=\"${stamp(0, 8, 0)}\" stop=\"${stamp(0, 9, 0)}\"><title>  </title></programme>"
        val r = XmlTvParser.parse(xml(body).toStream(), now)
        assertEquals(0, r.size)
    }
}
```

- [ ] **Step 3: 运行确认失败**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.smallpig.iptv.MatchTest" --tests "com.smallpig.iptv.XmlTvParserTest"
```

Expected: 编译失败（Unresolved reference: EpgMatcher / XmlTvParser）。

- [ ] **Step 4: 实现 `parse/Match.kt`**

```kotlin
package com.smallpig.iptv.parse

object EpgMatcher {
    fun normalize(name: String): String = name.trim().replace(Regex("\\s+"), "").uppercase()
    fun nameKey(displayName: String): String = "name:${normalize(displayName)}"

    fun keyForChannel(tvgId: String?, name: String): List<String> =
        listOfNotNull(tvgId?.trim()?.takeIf { it.isNotEmpty() }?.let { "id:$it" }, nameKey(name))

    /** items 中找 now 时刻正在播出项与紧随其后的下一项；items 可乱序 */
    fun <T> nowNext(items: List<T>, startOf: (T) -> Long, stopOf: (T) -> Long, now: Long): Pair<T?, T?> {
        val sorted = items.sortedBy(startOf)
        val cur = sorted.lastOrNull { startOf(it) <= now && stopOf(it) > now }
        val next = sorted.firstOrNull { startOf(it) > now }
            ?: if (cur != null) sorted.firstOrNull { startOf(it) >= stopOf(cur) && it !== cur } else null
        return cur to next
    }
}

object FavoriteKey {
    fun of(name: String, streamUrl: String): String =
        "${EpgMatcher.normalize(name)}|${streamUrl.trim()}"
}
```

- [ ] **Step 5: 实现 `parse/XmlTvParser.kt`**

```kotlin
package com.smallpig.iptv.parse

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.regex.Pattern

data class ParsedProgramme(
    val channelKeys: List<String>,
    val title: String,
    val description: String?,
    val startMillis: Long,
    val stopMillis: Long,
)

object XmlTvParser {
    private val TIME_RE = Pattern.compile("^(\\d{14})(?:\\.\\d+)?([+-]\\d{4})?$")

    /** 流式解析 XMLTV；只保留 [今天00:00, 后天00:00) 的节目 */
    fun parse(input: InputStream, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): List<ParsedProgramme> {
        val windowStart = dayStart(now, zone, 0)
        val windowEnd = dayStart(now, zone, 2)

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        val displayNames = HashMap<String, ArrayList<String>>()
        val raw = ArrayList<Triple<String, Long, Long>>() // channel, start, stop（title 阶段再补）
        val titles = ArrayList<Pair<String?, String?>>()  // title, desc 与 raw 对齐
        var curChannelId: String? = null
        var pChannel: String? = null
        var pStart = 0L
        var pStop = 0L
        var text: StringBuilder? = null
        var textFor = ""
        var pTitle: String? = null
        var pDesc: String? = null

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> curChannelId = parser.getAttributeValue(null, "id")
                    "display-name" -> { text = StringBuilder(); textFor = "dn" }
                    "programme" -> {
                        pChannel = parser.getAttributeValue(null, "channel")
                        pStart = parseXmlTvTime(parser.getAttributeValue(null, "start")) ?: -1
                        pStop = parseXmlTvTime(parser.getAttributeValue(null, "stop")) ?: -1
                        pTitle = null; pDesc = null
                    }
                    "title" -> { text = StringBuilder(); textFor = "t" }
                    "desc" -> { text = StringBuilder(); textFor = "d" }
                }
                XmlPullParser.TEXT -> text?.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "display-name" -> {
                        val v = text?.toString()?.trim()
                        if (v != null && v.isNotEmpty() && curChannelId != null)
                            displayNames.getOrPut(curChannelId!!) { arrayListOf() }.add(v)
                        text = null
                    }
                    "channel" -> curChannelId = null
                    "title" -> { pTitle = text?.toString()?.trim(); text = null }
                    "desc" -> { pDesc = text?.toString()?.trim(); text = null }
                    "programme" -> {
                        val ch = pChannel
                        if (ch != null && pStart > 0 && pStop > 0 && pStop > windowStart && pStart < windowEnd) {
                            raw.add(Triple(ch, pStart, pStop)); titles.add(pTitle to pDesc)
                        }
                        pChannel = null
                    }
                }
            }
            ev = parser.next()
        }

        val out = ArrayList<ParsedProgramme>(raw.size)
        for (i in raw.indices) {
            val (ch, s, e) = raw[i]
            val (title, desc) = titles[i]
            if (title.isNullOrEmpty()) continue
            val keys = LinkedHashSet<String>().apply {
                add("id:$ch")
                displayNames[ch]?.forEach { add(EpgMatcher.nameKey(it)) }
            }
            out.add(ParsedProgramme(keys.toList(), title, desc, s, e))
        }
        return out
    }

    fun parseXmlTvTime(raw: String?): Long? {
        if (raw == null) return null
        val t = raw.trim().replace(" ", "")
        val m = TIME_RE.matcher(t)
        if (!m.matches()) return null
        val base = m.group(1)!!
        val off = m.group(2) ?: ""
        val zone = if (off.isEmpty()) TimeZone.getDefault() else TimeZone.getTimeZone("GMT$off")
        val c = Calendar.getInstance(zone)
        c.clear()
        c.set(
            base.substring(0, 4).toInt(), base.substring(4, 6).toInt() - 1, base.substring(6, 8).toInt(),
            base.substring(8, 10).toInt(), base.substring(10, 12).toInt(), base.substring(12, 14).toInt(),
        )
        return c.timeInMillis
    }

    private fun dayStart(millis: Long, zone: TimeZone, plusDays: Int): Long {
        val c = Calendar.getInstance(zone).apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_YEAR, plusDays)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }
}
```

- [ ] **Step 6: 运行确认通过**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: 全部测试绿（M3uParserTest 7 + 本任务 10）。

- [ ] **Step 7: Commit**

```powershell
git add app/src
git commit -m "[feat] XMLTV 流式解析器与匹配/收藏纯逻辑，含单测"
```

---

### Task 5: Room 数据层 + App DI

**Files:**
- Create: `app/src/main/java/com/smallpig/iptv/data/Entities.kt`、`data/Daos.kt`、`data/AppDatabase.kt`
- Modify: `app/src/main/java/com/smallpig/iptv/App.kt`（挂 db/repo 单例，repo 在 Task 6 创建，本步先只建 db）

- [ ] **Step 1: `data/Entities.kt`**

```kotlin
package com.smallpig.iptv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val m3uUrl: String,
    val epgUrl: String? = null,
    val isCurrent: Boolean = false,
)

@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String,
    val streamUrl: String,
    val tvgId: String?,
    val sortIndex: Int,
)

@Entity(tableName = "favorites", indices = [Index(value = ["dedupeKey"], unique = true)])
data class Favorite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val dedupeKey: String,
    val addedAt: Long,
)

@Entity(tableName = "epg_programmes", primaryKeys = ["channelKey", "startTime", "title"])
data class EpgProgramme(
    val channelKey: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
)

@Entity(tableName = "app_state")
data class AppState(@PrimaryKey val key: String, val value: String)
```

- [ ] **Step 2: `data/Daos.kt`**

```kotlin
package com.smallpig.iptv.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SubscriptionDao {
    @Insert suspend fun insert(s: Subscription): Long
    @Update suspend fun update(s: Subscription)
    @Delete suspend fun delete(s: Subscription)
    @Query("SELECT * FROM subscriptions ORDER BY id") suspend fun all(): List<Subscription>
    @Query("SELECT * FROM subscriptions WHERE id = :id") suspend fun byId(id: Long): Subscription?
    @Query("SELECT * FROM subscriptions WHERE isCurrent = 1 LIMIT 1") suspend fun current(): Subscription?
    @Query("UPDATE subscriptions SET isCurrent = 0") suspend fun clearCurrent()
    @Query("UPDATE subscriptions SET isCurrent = 1 WHERE id = :id") suspend fun setCurrent(id: Long)
}

@Dao
interface ChannelDao {
    @Insert suspend fun insertAll(list: List<Channel>)
    @Query("DELETE FROM channels WHERE subscriptionId = :subId") suspend fun clearFor(subId: Long)
    @Query("SELECT * FROM channels WHERE subscriptionId = :subId ORDER BY sortIndex") suspend fun forSub(subId: Long): List<Channel>
    @Query("SELECT groupTitle FROM channels WHERE subscriptionId = :subId GROUP BY groupTitle ORDER BY MIN(sortIndex)") suspend fun groupsFor(subId: Long): List<String>
    @Query("SELECT COUNT(*) FROM channels WHERE subscriptionId = :subId") suspend fun countFor(subId: Long): Int
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(f: Favorite)
    @Delete suspend fun delete(f: Favorite)
    @Query("DELETE FROM favorites WHERE dedupeKey = :key") suspend fun deleteByKey(key: String)
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC") suspend fun all(): List<Favorite>
    @Query("SELECT * FROM favorites WHERE dedupeKey = :key") suspend fun byKey(key: String): Favorite?
}

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<EpgProgramme>)
    @Query("DELETE FROM epg_programmes") suspend fun clear()
    @Query("SELECT COUNT(*) FROM epg_programmes") suspend fun count(): Int
    @Query("SELECT * FROM epg_programmes WHERE channelKey = :key AND endTime > :from ORDER BY startTime")
    suspend fun upcoming(key: String, from: Long): List<EpgProgramme>
    @Query("SELECT * FROM epg_programmes WHERE channelKey = :key AND endTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime")
    suspend fun day(key: String, dayStart: Long, dayEnd: Long): List<EpgProgramme>
}

@Dao
interface AppStateDao {
    @Query("SELECT value FROM app_state WHERE key = :key") suspend fun get(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(v: AppState)
}
```

- [ ] **Step 3: `data/AppDatabase.kt`**

```kotlin
package com.smallpig.iptv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Subscription::class, Channel::class, Favorite::class, EpgProgramme::class, AppState::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun epgDao(): EpgDao
    abstract fun appStateDao(): AppStateDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "iptv.db").build()
    }
}
```

- [ ] **Step 4: `App.kt` 挂 db（Repository 到 Task 6 再接入）**

```kotlin
package com.smallpig.iptv

import android.app.Application
import com.smallpig.iptv.data.AppDatabase

class App : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
```

- [ ] **Step 5: 构建验证**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`（kapt 生成 AppDatabase_Impl）。

- [ ] **Step 6: Commit**

```powershell
git add app/src
git commit -m "[feat] Room 数据实体/DAO/Database 与 App 单例"
```

---

### Task 6: Fetcher + Repository（数据流）

**Files:**
- Create: `app/src/main/java/com/smallpig/iptv/net/Fetcher.kt`
- Create: `app/src/main/java/com/smallpig/iptv/data/Repository.kt`
- Modify: `app/src/main/java/com/smallpig/iptv/App.kt`（暴露 repo）

- [ ] **Step 1: `net/Fetcher.kt`**

```kotlin
package com.smallpig.iptv.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class FetchException(message: String, cause: Throwable? = null) : Exception(message, cause)

class Fetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchText(url: String): String = call(url).use { resp ->
        resp.body?.string() ?: throw FetchException("空响应体")
    }

    /** 调用方负责关闭返回流 */
    fun stream(url: String): InputStream = call(url).let { resp ->
        resp.body?.byteStream() ?: run { resp.close(); throw FetchException("空响应体") }
    }

    private fun call(url: String): okhttp3.Response {
        val req = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            throw FetchException("非法 URL：$url", e)
        }
        val resp = try {
            client.newCall(req).execute()
        } catch (e: IOException) {
            throw FetchException(e.message ?: "网络错误", e)
        }
        if (!resp.isSuccessful) {
            val code = resp.code
            resp.close()
            throw FetchException("HTTP $code")
        }
        return resp
    }
}
```

注意：`call(url).use` 里 resp 关闭后 `fetchText` 已读取完毕，安全；`stream` 不关 resp（body 流即内容）。

- [ ] **Step 2: `data/Repository.kt`**

```kotlin
package com.smallpig.iptv.data

import com.smallpig.iptv.net.Fetcher
import com.smallpig.iptv.parse.EpgMatcher
import com.smallpig.iptv.parse.FavoriteKey
import com.smallpig.iptv.parse.M3uParser
import com.smallpig.iptv.parse.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.Calendar

class Repository(private val db: AppDatabase, private val fetcher: Fetcher) {
    private val subs get() = db.subscriptionDao()
    private val chans get() = db.channelDao()
    private val favs get() = db.favoriteDao()
    private val epgs get() = db.epgDao()
    private val state get() = db.appStateDao()

    // ---- 订阅 ----
    suspend fun subscriptions(): List<Subscription> = withContext(Dispatchers.IO) { subs.all() }
    suspend fun subscriptionById(id: Long): Subscription? = withContext(Dispatchers.IO) { subs.byId(id) }
    suspend fun currentSubscription(): Subscription? = withContext(Dispatchers.IO) { subs.current() }

    suspend fun addSubscription(name: String, m3uUrl: String, epgUrl: String?): Long = withContext(Dispatchers.IO) {
        val id = subs.insert(Subscription(name = name.trim(), m3uUrl = m3uUrl.trim(), epgUrl = epgUrl?.trim()?.ifBlank { null }))
        if (subs.current() == null) { subs.clearCurrent(); subs.setCurrent(id) }
        id
    }

    suspend fun updateSubscription(sub: Subscription) = withContext(Dispatchers.IO) { subs.update(sub) }

    suspend fun setSubscriptionCurrent(id: Long) = withContext(Dispatchers.IO) {
        subs.clearCurrent(); subs.setCurrent(id)
    }

    suspend fun deleteSubscription(sub: Subscription) = withContext(Dispatchers.IO) {
        chans.clearFor(sub.id)
        subs.delete(sub)
        if (sub.isCurrent) subs.all().firstOrNull()?.let { setSubscriptionCurrent(it.id) }
    }

    // ---- 频道 ----
    suspend fun refreshChannels(subId: Long): Int = withContext(Dispatchers.IO) {
        val sub = subs.byId(subId) ?: return@withContext 0
        val parsed = M3uParser.parse(fetcher.fetchText(sub.m3uUrl))
        chans.clearFor(subId)
        chans.insertAll(parsed.map {
            Channel(subscriptionId = subId, name = it.name, logoUrl = it.logo,
                groupTitle = it.group, streamUrl = it.url, tvgId = it.tvgId, sortIndex = it.order)
        })
        parsed.size
    }

    suspend fun channels(subId: Long): List<Channel> = withContext(Dispatchers.IO) { chans.forSub(subId) }
    suspend fun groups(subId: Long): List<String> = withContext(Dispatchers.IO) { chans.groupsFor(subId) }
    suspend fun channelCount(subId: Long): Int = withContext(Dispatchers.IO) { chans.countFor(subId) }

    // ---- 收藏（全局） ----
    suspend fun favorites(): List<Favorite> = withContext(Dispatchers.IO) { favs.all() }

    /** 返回 true = 现在是收藏状态 */
    suspend fun toggleFavorite(name: String, logoUrl: String?, streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        val key = FavoriteKey.of(name, streamUrl)
        if (favs.byKey(key) != null) { favs.deleteByKey(key); false }
        else {
            favs.insert(Favorite(name = name.trim(), logoUrl = logoUrl, streamUrl = streamUrl.trim(),
                dedupeKey = key, addedAt = System.currentTimeMillis()))
            true
        }
    }

    suspend fun isFavorited(name: String, streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        favs.byKey(FavoriteKey.of(name, streamUrl)) != null
    }

    // ---- EPG ----
    suspend fun epgCount(): Int = withContext(Dispatchers.IO) { epgs.count() }

    suspend fun refreshEpg(sub: Subscription): Int = withContext(Dispatchers.IO) {
        val url = sub.epgUrl ?: return@withContext 0
        val list = BufferedInputStream(fetcher.stream(url)).use { XmlTvParser.parse(it) }
        epgs.clear()
        list.flatMap { p -> p.channelKeys.map { k -> EpgProgramme(k, p.title, p.description, p.startMillis, p.stopMillis) } }
            .chunked(500).forEach { epgs.insertAll(it) }
        list.size
    }

    suspend fun nowNext(name: String, tvgId: String?): Pair<EpgProgramme?, EpgProgramme?> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (k in EpgMatcher.keyForChannel(tvgId, name)) {
            val rows = epgs.upcoming(k, now)
            if (rows.isNotEmpty()) return@withContext EpgMatcher.nowNext(rows, { it.startTime }, { it.endTime }, now)
        }
        null to null
    }

    suspend fun channelNow(name: String, tvgId: String?): EpgProgramme? = nowNext(name, tvgId).first

    suspend fun todayProgrammes(name: String, tvgId: String?): List<EpgProgramme> = withContext(Dispatchers.IO) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayStart = c.timeInMillis
        val dayEnd = c.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        for (k in EpgMatcher.keyForChannel(tvgId, name)) {
            val rows = epgs.day(k, dayStart, dayEnd)
            if (rows.isNotEmpty()) return@withContext rows
        }
        emptyList()
    }

    // ---- 状态 ----
    suspend fun saveState(key: String, value: String) = withContext(Dispatchers.IO) { state.put(AppState(key, value)) }
    suspend fun readState(key: String): String? = withContext(Dispatchers.IO) { state.get(key) }
}
```

- [ ] **Step 3: `App.kt` 暴露 repo**

```kotlin
package com.smallpig.iptv

import android.app.Application
import com.smallpig.iptv.data.AppDatabase
import com.smallpig.iptv.data.Repository
import com.smallpig.iptv.net.Fetcher

class App : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val repo: Repository by lazy { Repository(db, Fetcher()) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
```

- [ ] **Step 4: 构建 + 单测验证**

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Expected: 全绿。

- [ ] **Step 5: Commit**

```powershell
git add app/src
git commit -m "[feat] Fetcher 与 Repository：订阅/刷新/收藏/EPG 数据流"
```

---

### Task 7: 首页（页签 + 分组/频道列表 + 收藏列表）

**Files:**
- Create: `res/layout/activity_home.xml`、`item_channel.xml`、`item_group.xml`
- Modify: `ui/HomeActivity.kt`（整体替换占位）

- [ ] **Step 1: `res/layout/activity_home.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:padding="24dp">

    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal">
        <TextView android:id="@+id/tabAll" style="@style/TabBtn" android:text="全部频道" android:nextFocusDown="@+id/groupList" />
        <TextView android:id="@+id/tabFav" style="@style/TabBtn" android:text="收藏夹" android:nextFocusDown="@+id/favList" />
        <TextView android:id="@+id/tabSubs" style="@style/TabBtn" android:text="订阅管理" />
    </LinearLayout>

    <FrameLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"
        android:layout_marginTop="16dp">
        <LinearLayout android:id="@+id/panelBrowse" android:layout_width="match_parent" android:layout_height="match_parent"
            android:orientation="horizontal">
            <androidx.recyclerview.widget.RecyclerView android:id="@+id/groupList"
                android:layout_width="260dp" android:layout_height="match_parent"
                android:focusable="false" android:nextFocusUp="@+id/tabAll" />
            <androidx.recyclerview.widget.RecyclerView android:id="@+id/channelList"
                android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"
                android:layout_marginStart="16dp" android:focusable="false" android:nextFocusUp="@+id/tabAll" />
        </LinearLayout>
        <androidx.recyclerview.widget.RecyclerView android:id="@+id/favList"
            android:layout_width="match_parent" android:layout_height="match_parent"
            android:visibility="gone" android:focusable="false" android:nextFocusUp="@+id/tabFav" />
    </FrameLayout>

    <TextView android:id="@+id/statusBar" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:layout_marginTop="12dp" android:textSize="16sp" android:textColor="#8899BB" />
</LinearLayout>
```

`res/values/themes.xml` 里追加页签样式：

```xml
<style name="TabBtn">
    <item name="android:layout_width">wrap_content</item>
    <item name="android:layout_height">wrap_content</item>
    <item name="android:minWidth">160dp</item>
    <item name="android:padding">14dp</item>
    <item name="android:layout_marginEnd">12dp</item>
    <item name="android:background">@drawable/focus_bg</item>
    <item name="android:focusable">true</item>
    <item name="android:clickable">true</item>
    <item name="android:textSize">18sp</item>
    <item name="android:textColor">#FFFFFF</item>
    <item name="android:gravity">center</item>
</style>
```

- [ ] **Step 2: `res/layout/item_group.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/groupName"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:background="@drawable/focus_bg" android:focusable="true" android:clickable="true"
    android:padding="14dp" android:textSize="17sp" android:textColor="#FFFFFF"
    android:maxLines="1" android:ellipsize="end" android:layout_marginBottom="6dp" />
```

- [ ] **Step 3: `res/layout/item_channel.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="horizontal" android:background="@drawable/focus_bg"
    android:focusable="true" android:clickable="true" android:padding="12dp"
    android:layout_marginBottom="6dp" android:gravity="center_vertical">
    <ImageView android:id="@+id/logo" android:layout_width="48dp" android:layout_height="32dp" />
    <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
        android:orientation="vertical" android:layout_marginStart="12dp">
        <TextView android:id="@+id/chName" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:textSize="18sp" android:textColor="#FFFFFF" android:maxLines="1" android:ellipsize="end" />
        <TextView android:id="@+id/chNow" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:textSize="13sp" android:textColor="#8899BB" android:maxLines="1" android:ellipsize="end" />
    </LinearLayout>
    <TextView android:id="@+id/chFav" android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:textSize="16sp" android:textColor="#FFCC44" />
</LinearLayout>
```

- [ ] **Step 4: `ui/HomeActivity.kt`（替换占位，完整实现）**

```kotlin
package com.smallpig.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import com.smallpig.iptv.data.Channel
import com.smallpig.iptv.data.Favorite
import com.smallpig.iptv.data.Subscription
import com.smallpig.iptv.parse.FavoriteKey
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private val repo get() = (application as App).repo

    private lateinit var panelBrowse: View
    private lateinit var panelFav: View
    private lateinit var statusBar: TextView
    private lateinit var groupList: RecyclerView
    private lateinit var channelList: RecyclerView
    private lateinit var favList: RecyclerView
    private lateinit var tabs: List<TextView>

    private var sub: Subscription? = null
    private var channels: List<Channel> = emptyList()
    private var favs: List<Favorite> = emptyList()
    private var favKeys: Set<String> = emptySet()
    private var currentGroup: String? = null
    private var showingFav = false
    private var focusedChannel: Channel? = null
    private var focusedFavorite: Favorite? = null
    private var epgRefreshing = false

    private val groupAdapter by lazy { GroupAdapter { group -> currentGroup = group; channelAdapter.submit(visibleChannels()) } }
    private val channelAdapter by lazy {
        ChannelAdapter(
            onClick = { play(it) },
            onFocus = { focusedChannel = it },
            isFav = { FavoriteKey.of(it.name, it.streamUrl) in favKeys },
            epgFor = { ch -> repo.channelNow(ch.name, ch.tvgId)?.title },
            scope = lifecycleScope,
        )
    }
    private val favAdapter by lazy { FavoriteAdapter(onClick = { playFavorite(it) }, onFocus = { focusedFavorite = it }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        panelBrowse = findViewById(R.id.panelBrowse); panelFav = findViewById(R.id.panelFav)
        statusBar = findViewById(R.id.statusBar)
        groupList = findViewById(R.id.groupList); channelList = findViewById(R.id.channelList); favList = findViewById(R.id.favList)
        tabs = listOf(findViewById(R.id.tabAll), findViewById(R.id.tabFav), findViewById(R.id.tabSubs))
        tabs[0].setOnClickListener { showTab(0) }
        tabs[1].setOnClickListener { showTab(1) }
        tabs[2].setOnClickListener { startActivity(Intent(this, SubsActivity::class.java)) }
        groupList.layoutManager = LinearLayoutManager(this); groupList.adapter = groupAdapter
        channelList.layoutManager = LinearLayoutManager(this); channelList.adapter = channelAdapter
        favList.layoutManager = LinearLayoutManager(this); favList.adapter = favAdapter
        showTab(0)
        findViewById<View>(R.id.tabAll).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { reload() }
    }

    private fun showTab(idx: Int) {
        showingFav = idx == 1
        panelBrowse.visibility = if (showingFav) View.GONE else View.VISIBLE
        panelFav.visibility = if (showingFav) View.VISIBLE else View.GONE
        tabs.forEachIndexed { i, t -> t.isSelected = i == idx }
        (if (showingFav) favList else channelList).requestFocus()
    }

    private suspend fun reload() {
        favs = repo.favorites()
        favKeys = favs.map { it.dedupeKey }.toSet()
        favAdapter.submit(favs)
        val s = repo.currentSubscription()
        sub = s
        if (s == null) {
            statusBar.text = "还没有订阅：请到『订阅管理』添加 m3u 订阅链接"
            channels = emptyList()
            groupAdapter.submit(listOf())
            channelAdapter.submit(emptyList())
            return
        }
        channels = repo.channels(s.id)
        statusBar.text = "当前订阅：${s.name}（${channels.size} 个频道）　菜单键=收藏/取消收藏"
        groupAdapter.submit(listOf<String?>(null) + repo.groups(s.id))
        if (currentGroup != null && visibleChannels().isEmpty()) currentGroup = null
        channelAdapter.submit(visibleChannels())
        if (!epgRefreshing && s.epgUrl != null) {
            epgRefreshing = true
            lifecycleScope.launch {
                try { repo.refreshEpg(s) } catch (_: Exception) {}
                epgRefreshing = false
            }
        }
    }

    private fun visibleChannels(): List<Channel> =
        channels.filter { currentGroup == null || it.groupTitle == currentGroup }

    private fun play(ch: Channel) {
        startActivity(Intent(this, PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_NAME, ch.name)
            putExtra(PlaybackActivity.EXTRA_URL, ch.streamUrl)
            putExtra(PlaybackActivity.EXTRA_TVG_ID, ch.tvgId)
        })
    }

    private fun playFavorite(f: Favorite) {
        startActivity(Intent(this, PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_NAME, f.name)
            putExtra(PlaybackActivity.EXTRA_URL, f.streamUrl)
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && event?.action == KeyEvent.ACTION_DOWN) {
            if (showingFav) {
                val f = focusedFavorite
                if (f != null) {
                    lifecycleScope.launch { repo.toggleFavorite(f.name, f.logoUrl, f.streamUrl); reload() }
                    return true
                }
            } else {
                val ch = focusedChannel
                if (ch != null) {
                    lifecycleScope.launch {
                        val nowFav = repo.toggleFavorite(ch.name, ch.logoUrl, ch.streamUrl)
                        Toast.makeText(this@HomeActivity, if (nowFav) "已收藏 ${ch.name}" else "已取消收藏", Toast.LENGTH_SHORT).show()
                        reload()
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
```

GroupAdapter、ChannelAdapter、FavoriteAdapter 追加到同一个 `HomeActivity.kt` 文件底部：

```kotlin
private class GroupAdapter(val onClick: (String?) -> Unit) : RecyclerView.Adapter<GroupAdapter.VH>() {
    private var items: List<String?> = emptyList()
    fun submit(list: List<String?>) { items = list; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) { val name: TextView = v.findViewById(R.id.groupName) }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        h.name.text = items[pos] ?: "全部"
        h.itemView.setOnClickListener { onClick(items[pos]) }
    }
}

private class ChannelAdapter(
    val onClick: (Channel) -> Unit,
    val onFocus: (Channel?) -> Unit,
    val isFav: (Channel) -> Boolean,
    val epgFor: suspend (Channel) -> String?,
    val scope: kotlinx.coroutines.CoroutineScope,
) : RecyclerView.Adapter<ChannelAdapter.VH>() {
    private var items: List<Channel> = emptyList()
    fun submit(list: List<Channel>) { items = list; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.chName)
        val now: TextView = v.findViewById(R.id.chNow)
        val fav: TextView = v.findViewById(R.id.chFav)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        h.name.text = c.name
        h.now.text = ""
        h.fav.text = if (isFav(c)) "★" else ""
        val ctx = h.itemView.context
        if (c.logoUrl != null) Glide.with(ctx).load(c.logoUrl).into(h.logo) else h.logo.setImageDrawable(null)
        h.itemView.setOnClickListener { onClick(c) }
        h.itemView.setOnFocusChangeListener { _, has -> if (has) onFocus(c) }
        scope.launch {
            val t = epgFor(c)
            if (h.bindingAdapterPosition == pos) h.now.text = t ?: ""
        }
    }
}

private class FavoriteAdapter(
    val onClick: (Favorite) -> Unit,
    val onFocus: (Favorite?) -> Unit,
) : RecyclerView.Adapter<FavoriteAdapter.VH>() {
    private var items: List<Favorite> = emptyList()
    fun submit(list: List<Favorite>) { items = list; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.chName)
        val now: TextView = v.findViewById(R.id.chNow)
        val fav: TextView = v.findViewById(R.id.chFav)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val f = items[pos]
        h.name.text = f.name
        h.now.text = "已收藏"
        h.fav.text = "★"
        val ctx = h.itemView.context
        if (f.logoUrl != null) Glide.with(ctx).load(f.logoUrl).into(h.logo) else h.logo.setImageDrawable(null)
        h.itemView.setOnClickListener { onClick(f) }
        h.itemView.setOnFocusChangeListener { _, has -> if (has) onFocus(f) }
    }
}
```

- [ ] **Step 5: 构建验证**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 6: Commit**

```powershell
git add app/src
git commit -m "[feat] 首页：页签、分组/频道/收藏列表、菜单键收藏"
```

---

### Task 8: 播放页（Media3 + 信息浮层 + 节目条 + 控制行）

**Files:**
- Create: `res/layout/activity_playback.xml`
- Modify: `ui/PlaybackActivity.kt`（替换占位）
- Modify: `res/values/themes.xml`（追加 CtrlBtn 样式）

- [ ] **Step 1: `res/layout/activity_playback.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent">

    <androidx.media3.ui.PlayerView android:id="@+id/playerView"
        android:layout_width="match_parent" android:layout_height="match_parent"
        android:focusable="false"
        app:resize_mode="fit" app:show_buffering="when_playing"
        xmlns:app="http://schemas.android.com/apk/res-auto" />

    <LinearLayout android:id="@+id/infoPanel"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_gravity="top|end" android:layout_margin="32dp"
        android:orientation="vertical" android:background="#B0000000" android:padding="16dp"
        android:visibility="gone">
        <TextView android:id="@+id/infoVideo" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textColor="#FFFFFF" android:textSize="15sp" />
        <TextView android:id="@+id/infoAudio" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textColor="#FFFFFF" android:textSize="15sp" android:layout_marginTop="6dp" />
    </LinearLayout>

    <TextView android:id="@+id/nowBar"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:layout_gravity="bottom" android:background="#80000000"
        android:padding="12dp" android:textColor="#DDDDDD" android:textSize="16sp" />

    <LinearLayout android:id="@+id/controls"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_gravity="bottom|end" android:layout_marginEnd="32dp" android:layout_marginBottom="64dp"
        android:orientation="horizontal" android:visibility="gone">
        <TextView android:id="@+id/ctrlPrev" style="@style/CtrlBtn" android:text="上一台" />
        <TextView android:id="@+id/ctrlNext" style="@style/CtrlBtn" android:text="下一台" />
        <TextView android:id="@+id/ctrlFav" style="@style/CtrlBtn" android:text="收藏" />
        <TextView android:id="@+id/ctrlEpg" style="@style/CtrlBtn" android:text="节目单" />
    </LinearLayout>
</FrameLayout>
```

`themes.xml` 追加：

```xml
<style name="CtrlBtn">
    <item name="android:layout_width">wrap_content</item>
    <item name="android:layout_height">wrap_content</item>
    <item name="android:padding">12dp</item>
    <item name="android:layout_marginStart">10dp</item>
    <item name="android:background">@drawable/focus_bg</item>
    <item name="android:focusable">true</item>
    <item name="android:clickable">true</item>
    <item name="android:textColor">#FFFFFF</item>
    <item name="android:textSize">16sp</item>
</style>
```

- [ ] **Step 2: `ui/PlaybackActivity.kt`（替换占位，完整实现）**

```kotlin
package com.smallpig.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import kotlinx.coroutines.launch

class PlaybackActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_URL = "url"
        const val EXTRA_TVG_ID = "tvgId"
        private const val LAST_CHANNEL_KEY = "lastChannel"
    }

    private val repo get() = (application as App).repo
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var infoPanel: View
    private lateinit var nowBar: TextView
    private lateinit var controls: View
    private var infoShown = false
    private var controlsShown = false

    private var chName: String = ""
    private var chUrl: String = ""
    private var chTvgId: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { setControls(false) }

    private val poll = object : Runnable {
        override fun run() {
            if (infoShown) updateInfo()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chName = intent.getStringExtra(EXTRA_NAME) ?: "频道"
        chUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        chTvgId = intent.getStringExtra(EXTRA_TVG_ID)
        setContentView(R.layout.activity_playback)
        playerView = findViewById(R.id.playerView)
        infoPanel = findViewById(R.id.infoPanel)
        nowBar = findViewById(R.id.nowBar)
        controls = findViewById(R.id.controls)
        nowBar.text = chName

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                nowBar.text = "$chName　播放失败：${error.errorCodeName}（按返回退出或『下一台』切换）"
            }
        })
        player.setMediaItem(MediaItem.fromUri(chUrl))
        player.prepare()
        player.playWhenReady = true

        findViewById<TextView>(R.id.ctrlPrev).setOnClickListener { step(-1) }
        findViewById<TextView>(R.id.ctrlNext).setOnClickListener { step(1) }
        findViewById<TextView>(R.id.ctrlFav).setOnClickListener { toggleFav() }
        findViewById<TextView>(R.id.ctrlEpg).setOnClickListener {
            startActivity(Intent(this, EpgActivity::class.java).apply {
                putExtra(EpgActivity.EXTRA_NAME, chName); putExtra(EpgActivity.EXTRA_TVG_ID, chTvgId)
            })
        }

        loadEpg()
        lifecycleScope.launch {
            repo.saveState(LAST_CHANNEL_KEY, "$chName|$chUrl|${chTvgId ?: ""}")
        }
    }

    override fun onStart() { super.onStart(); player.playWhenReady = true; handler.post(poll) }
    override fun onStop() { super.onStop(); handler.removeCallbacks(poll); player.playWhenReady = false }
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null); player.release() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { setControls(!controlsShown); return true }
            KeyEvent.KEYCODE_MENU -> {
                infoShown = !infoShown; infoPanel.visibility = if (infoShown) View.VISIBLE else View.GONE
                if (infoShown) updateInfo(); return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!controlsShown) { step(-1); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controlsShown) { step(1); return true }
            KeyEvent.KEYCODE_BACK -> {
                if (controlsShown || infoShown) { setControls(false); infoShown = false; infoPanel.visibility = View.GONE; return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setControls(show: Boolean) {
        controlsShown = show
        controls.visibility = if (show) View.VISIBLE else View.GONE
        handler.removeCallbacks(hideControls)
        if (show) {
            handler.postDelayed(hideControls, 5000)
            findViewById<View>(R.id.ctrlNext).requestFocus()
        } else playerView.requestFocus()
    }

    private fun step(dir: Int) {
        lifecycleScope.launch {
            val sub = repo.currentSubscription() ?: return@launch
            val list = repo.channels(sub.id)
            if (list.isEmpty()) return@launch
            val idx = list.indexOfFirst { it.streamUrl == chUrl }
            val next = list[((if (idx < 0) 0 else idx) + dir + list.size) % list.size]
            restartWith(next.name, next.streamUrl, next.tvgId)
        }
    }

    private fun restartWith(name: String, url: String, tvgId: String?) {
        chName = name; chUrl = url; chTvgId = tvgId
        nowBar.text = name
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare(); player.playWhenReady = true
        loadEpg()
    }

    private fun toggleFav() {
        lifecycleScope.launch {
            val nowFav = repo.toggleFavorite(chName, null, chUrl)
            findViewById<TextView>(R.id.ctrlFav).text = if (nowFav) "已收藏" else "收藏"
            Toast.makeText(this@PlaybackActivity, if (nowFav) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadEpg() {
        lifecycleScope.launch {
            try {
                if (repo.epgCount() == 0) repo.currentSubscription()?.epgUrl?.let { repo.refreshEpg(it) }
            } catch (_: Exception) {}
            try {
                val (cur, next) = repo.nowNext(chName, chTvgId)
                if (cur != null || next != null) {
                    nowBar.text = buildString {
                        append(chName)
                        cur?.let { append("　正在：${it.title}") }
                        next?.let { append("　接下：${it.title}") }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateInfo() {
        val vf = player.videoFormat
        val af = player.audioFormat
        findViewById<TextView>(R.id.infoVideo).text = "视频：" + when {
            vf == null -> "未知"
            else -> listOfNotNull(
                vf.width.takeIf { it > 0 }?.let { "${it}x${vf.height}" } ?: "分辨率未知",
                vf.sampleMimeType?.substringAfter('/')?.uppercase() ?: "编码未知",
                if (vf.bitrate > 0) "%.1f Mbps".format(vf.bitrate / 1e6) else "码率未知",
            ).joinToString(" · ")
        }
        findViewById<TextView>(R.id.infoAudio).text = "音频：" + when {
            af == null -> "未知"
            else -> listOfNotNull(
                af.sampleMimeType?.substringAfter('/')?.uppercase() ?: "未知",
                if (af.sampleRate > 0) "${af.sampleRate} Hz" else null,
                if (af.bitrate > 0) "%.0f kbps".format(af.bitrate / 1e3) else null,
            ).joinToString(" · ")
        }
    }
}
```

- [ ] **Step 3: 构建验证**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: Commit**

```powershell
git add app/src
git commit -m "[feat] 播放页：Media3 播放、菜单键信息浮层、EPG 信息条、控制行"
```

---

### Task 9: 节目单页

**Files:**
- Create: `res/layout/activity_epg.xml`、`item_epg.xml`
- Modify: `ui/EpgActivity.kt`（替换占位）

- [ ] **Step 1: `res/layout/activity_epg.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:padding="32dp">
    <TextView android:id="@+id/epgTitle" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:textSize="22sp" android:textColor="#FFFFFF" android:layout_marginBottom="12dp" />
    <androidx.recyclerview.widget.RecyclerView android:id="@+id/epgList"
        android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
</LinearLayout>
```

- [ ] **Step 2: `res/layout/item_epg.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="horizontal" android:padding="10dp" android:layout_marginBottom="4dp"
    android:background="@drawable/focus_bg">
    <TextView android:id="@+id/epgTime" android:layout_width="170dp" android:layout_height="wrap_content"
        android:textColor="#8899BB" android:textSize="16sp" />
    <TextView android:id="@+id/epgName" android:layout_width="0dp" android:layout_height="wrap_content"
        android:layout_weight="1" android:textColor="#FFFFFF" android:textSize="16sp" />
    <TextView android:id="@+id/epgLive" android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:textColor="#E94560" android:textSize="14sp" />
</LinearLayout>
```

- [ ] **Step 3: `ui/EpgActivity.kt`（替换占位）**

```kotlin
package com.smallpig.iptv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import com.smallpig.iptv.data.EpgProgramme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EpgActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_TVG_ID = "tvgId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epg)
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        val tvgId = intent.getStringExtra(EXTRA_TVG_ID)
        findViewById<TextView>(R.id.epgTitle).text = "$name · 今日节目单"
        val list: RecyclerView = findViewById(R.id.epgList)
        list.layoutManager = LinearLayoutManager(this)
        val adapter = EpgAdapter()
        list.adapter = adapter
        (application as App).repo.let { repo ->
            lifecycleScope.launch {
                val rows = repo.todayProgrammes(name, tvgId)
                adapter.submit(rows)
                val now = System.currentTimeMillis()
                val idx = rows.indexOfLast { it.startTime <= now }
                if (idx >= 0) list.scrollToPosition(maxOf(0, idx - 2))
                if (rows.isEmpty()) findViewById<TextView>(R.id.epgTitle).text = "$name · 今日节目单（无节目信息）"
            }
        }
    }
}

private class EpgAdapter : RecyclerView.Adapter<EpgAdapter.VH>() {
    private var items: List<EpgProgramme> = emptyList()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val now = System.currentTimeMillis()
    fun submit(list: List<EpgProgramme>) { items = list; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val time: TextView = v.findViewById(R.id.epgTime)
        val title: TextView = v.findViewById(R.id.epgName)
        val live: TextView = v.findViewById(R.id.epgLive)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_epg, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = items[pos]
        h.time.text = "${fmt.format(Date(p.startTime))} – ${fmt.format(Date(p.endTime))}"
        h.title.text = p.title
        h.live.text = if (p.startTime <= now && p.endTime > now) "正在播出" else ""
    }
}
```

- [ ] **Step 4: 构建验证**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: Commit**

```powershell
git add app/src
git commit -m "[feat] 节目单页：当日节目列表，高亮正在播出"
```

---

### Task 10: 订阅管理 + 订阅编辑

**Files:**
- Create: `res/layout/activity_subs.xml`、`item_sub.xml`、`activity_sub_edit.xml`
- Modify: `ui/SubsActivity.kt`、`ui/SubEditActivity.kt`（替换占位）

- [ ] **Step 1: `res/layout/activity_subs.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:padding="32dp">
    <TextView android:layout_width="match_parent" android:layout_height="wrap_content"
        android:text="订阅管理" android:textSize="24sp" android:textColor="#FFFFFF" android:layout_marginBottom="12dp" />
    <androidx.recyclerview.widget.RecyclerView android:id="@+id/subList"
        android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:layout_marginTop="12dp">
        <TextView android:id="@+id/btnAdd" style="@style/CtrlBtn" android:text="添加订阅" />
        <TextView android:id="@+id/btnEdit" style="@style/CtrlBtn" android:text="编辑" />
        <TextView android:id="@+id/btnCurrent" style="@style/CtrlBtn" android:text="设为当前" />
        <TextView android:id="@+id/btnRefresh" style="@style/CtrlBtn" android:text="刷新频道/EPG" />
        <TextView android:id="@+id/btnDelete" style="@style/CtrlBtn" android:text="删除" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: `res/layout/item_sub.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="vertical" android:background="@drawable/focus_bg"
    android:focusable="true" android:clickable="true" android:padding="14dp" android:layout_marginBottom="6dp">
    <TextView android:id="@+id/subName" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:textSize="18sp" android:textColor="#FFFFFF" />
    <TextView android:id="@+id/subMeta" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:textSize="13sp" android:textColor="#8899BB" />
</LinearLayout>
```

- [ ] **Step 3: `ui/SubsActivity.kt`（替换占位）**

```kotlin
package com.smallpig.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import com.smallpig.iptv.data.Subscription
import kotlinx.coroutines.launch

class SubsActivity : AppCompatActivity() {
    private val repo get() = (application as App).repo
    private var selected: Subscription? = null
    private val adapter by lazy { SubAdapter { s -> selected = s; adapter.notifyDataSetChanged() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subs)
        val list: RecyclerView = findViewById(R.id.subList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<View>(R.id.btnAdd).setOnClickListener { startActivity(Intent(this, SubEditActivity::class.java)) }
        findViewById<View>(R.id.btnEdit).setOnClickListener {
            val s = selected ?: return@setOnClickListener toast("先选中一个订阅")
            startActivity(Intent(this, SubEditActivity::class.java).putExtra(SubEditActivity.EXTRA_SUB_ID, s.id))
        }
        findViewById<View>(R.id.btnCurrent).setOnClickListener {
            val s = selected ?: return@setOnClickListener toast("先选中一个订阅")
            lifecycleScope.launch { repo.setSubscriptionCurrent(s.id); reload() }
        }
        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            val s = selected ?: return@setOnClickListener toast("先选中一个订阅")
            lifecycleScope.launch {
                toast("刷新中…")
                try {
                    val n = repo.refreshChannels(s.id)
                    var e = 0
                    if (s.epgUrl != null) e = repo.refreshEpg(s)
                    toast("频道 $n 个，EPG 节目 $e 个")
                } catch (ex: Exception) { toast("刷新失败：${ex.message}") }
            }
        }
        findViewById<View>(R.id.btnDelete).setOnClickListener {
            val s = selected ?: return@setOnClickListener toast("先选中一个订阅")
            lifecycleScope.launch {
                repo.deleteSubscription(s); selected = null; reload()
            }
        }
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() = lifecycleScope.launch {
        val all = repo.subscriptions()
        val counts = all.associate { it.id to repo.channelCount(it.id) }
        adapter.submit(all, counts)
        if (selected?.let { s -> all.none { it.id == s.id } } == true) selected = null
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

private class SubAdapter(val onClick: (Subscription) -> Unit) : RecyclerView.Adapter<SubAdapter.VH>() {
    private var items: List<Subscription> = emptyList()
    private var counts: Map<Long, Int> = emptyMap()
    fun submit(list: List<Subscription>, counts: Map<Long, Int>) { items = list; this.counts = counts; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.subName)
        val meta: TextView = v.findViewById(R.id.subMeta)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_sub, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = items[pos]
        h.itemView.isSelected = s.isCurrent
        h.name.text = if (s.isCurrent) "★ ${s.name}" else s.name
        h.meta.text = "${counts[s.id] ?: 0} 个频道　" +
            (if (s.epgUrl != null) "EPG 已配置" else "无 EPG") + "　" + s.m3uUrl
        h.itemView.setOnClickListener { onClick(s) }
    }
}
```

- [ ] **Step 4: `res/layout/activity_sub_edit.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:padding="48dp">
    <TextView android:id="@+id/editTitle" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:text="添加订阅" android:textSize="24sp" android:textColor="#FFFFFF" android:layout_marginBottom="16dp" />
    <EditText android:id="@+id/etName" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:hint="订阅名称" android:textColor="#FFFFFF" android:textColorHint="#6677AA" android:maxLines="1" />
    <EditText android:id="@+id/etM3u" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:hint="m3u 频道列表链接（http…）" android:textColor="#FFFFFF" android:textColorHint="#6677AA"
        android:maxLines="1" android:inputType="textUri" android:layout_marginTop="8dp" />
    <EditText android:id="@+id/etEpg" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:hint="EPG 节目单链接（XMLTV，可留空）" android:textColor="#FFFFFF" android:textColorHint="#6677AA"
        android:maxLines="1" android:inputType="textUri" android:layout_marginTop="8dp" />
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:layout_marginTop="16dp">
        <TextView android:id="@+id/btnSave" style="@style/CtrlBtn" android:text="保存并测试连接" />
        <TextView android:id="@+id/btnCancel" style="@style/CtrlBtn" android:text="取消" />
    </LinearLayout>
    <TextView android:id="@+id/editMsg" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:textColor="#FFCC44" android:textSize="15sp" android:layout_marginTop="16dp" />
</LinearLayout>
```

- [ ] **Step 5: `ui/SubEditActivity.kt`（替换占位）**

```kotlin
package com.smallpig.iptv.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smallpig.iptv.App
import com.smallpig.iptv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubEditActivity : AppCompatActivity() {
    companion object { const val EXTRA_SUB_ID = "subId" }

    private val repo get() = (application as App).repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_edit)
        val etName: EditText = findViewById(R.id.etName)
        val etM3u: EditText = findViewById(R.id.etM3u)
        val etEpg: EditText = findViewById(R.id.etEpg)
        val msg: TextView = findViewById(R.id.editMsg)
        val subId = intent.getLongExtra(EXTRA_SUB_ID, -1)
        var existing: com.smallpig.iptv.data.Subscription? = null

        findViewById<TextView>(R.id.editTitle).text = if (subId >= 0) "编辑订阅" else "添加订阅"
        lifecycleScope.launch {
            if (subId >= 0) {
                existing = repo.subscriptionById(subId)
                existing?.let {
                    etName.setText(it.name); etM3u.setText(it.m3uUrl); etEpg.setText(it.epgUrl ?: "")
                }
            }
        }

        findViewById<TextView>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnSave).setOnClickListener {
            val name = etName.text.toString().trim()
            val m3u = etM3u.text.toString().trim()
            val epg = etEpg.text.toString().trim()
            if (name.isEmpty() || m3u.isEmpty()) { msg.text = "名称与 m3u 链接必填"; return@setOnClickListener }
            if (!(m3u.startsWith("http://") || m3u.startsWith("https://"))) { msg.text = "m3u 链接需以 http 开头"; return@setOnClickListener }
            msg.text = "保存中，正在下载 m3u 测试…"
            lifecycleScope.launch {
                try {
                    val id = withContext(Dispatchers.IO) {
                        val cur = existing
                        val newId = if (cur == null) repo.addSubscription(name, m3u, epg)
                        else { repo.updateSubscription(cur.copy(name = name, m3uUrl = m3u, epgUrl = epg.ifBlank { null })); cur.id }
                        newId to repo.refreshChannels(newId)
                    }
                    var epgNote = "（无 EPG）"
                    if (epg.isNotBlank()) {
                        epgNote = try { "，EPG ${repo.refreshEpg(repo.subscriptionById(id.first)!!)} 条节目" }
                        catch (e: Exception) { "，EPG 解析失败：${e.message}" }
                    }
                    msg.text = "成功：解析到 ${id.second} 个频道$epgNote"
                    withContext(Dispatchers.IO) { Thread.sleep(700) }
                    finish()
                } catch (e: Exception) {
                    msg.text = "失败：${e.message}（订阅已保存，可稍后在『刷新频道/EPG』重试）"
                }
            }
        }
    }
}
```

- [ ] **Step 6: 构建 + 单测验证**

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Expected: 全绿。

- [ ] **Step 7: Commit**

```powershell
git add app/src
git commit -m "[feat] 订阅管理与编辑：增删改、设为当前、保存时连通性测试"
```

---

### Task 11: 收尾 —— 完整构建 + 真机安装脚本 + 验收清单

**Files:**
- Create: `install.ps1`（项目根）

- [ ] **Step 1: `install.ps1`（adb 用本机 SDK 内工具，连接电视安装）**

```powershell
# 用法：先查电视 IP（设置-网络），执行 .\install.ps1 192.168.x.x
param([string]$Ip)
$adb = "D:\app\bigtext\android-sdk\platform-tools\adb.exe"
$apk = "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { Write-Error "先执行 .\gradlew.bat assembleDebug"; exit 1 }
if ($Ip) { & $adb connect "${Ip}:5555" }
& $adb install -r $apk
& $adb shell monkey -p com.smallpig.iptv -c android.intent.category.LEANBACK_LAUNCHER 1
```

- [ ] **Step 2: 全量验证**

```powershell
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`，全部单测通过。

- [ ] **Step 3: 真机验收清单（需用户装到 TCL 电视逐项确认，无法在电脑替代）**

| # | 验收项 | 通过 |
|---|---|---|
| 1 | TV 桌面出现「IPTV 电视」图标（横幅正常） | ☐ |
| 2 | 订阅管理：添加真实 m3u，显示正确频道数 | ☐ |
| 3 | 首页左分组右频道，方向键焦点顺畅，OK 进入播放 | ☐ |
| 4 | 播放：画面出图、可切上一台/下一台、返回退出 | ☐ |
| 5 | 播放中菜单键浮层：分辨率/编码/码率有真实值或「未知」 | ☐ |
| 6 | 播放中底部显示正在播出/下一节目（配了 EPG 的订阅） | ☐ |
| 7 | 节目单页当日列表 + 正在播出高亮 | ☐ |
| 8 | 首页菜单键收藏/取消，收藏夹页签可快速播放 | ☐ |
| 9 | 多订阅添加后「设为当前」，首页频道随之变化 | ☐ |
| 10 | 断网播放出错提示 + 「下一台」可继续 | ☐ |

- [ ] **Step 4: Commit**

```powershell
git add install.ps1
git commit -m "[chore] 真机安装脚本与验收清单"
```

---

## Self-Review 结论（已执行）

1. **规格覆盖**：多订阅/切换（T6/T10/T7）、m3u 播放（T8）、收藏全局（T5 dedupeKey 唯一索引 + T7/T8）、格式码率浮层（T8 MENU）、EPG 两态展示（T7 信息条 + T9 日列表）、屏幕键盘输入（T10 EditText/IME）、错误处理（T6 FetchException、T8 错误回调、T4 流式窗口过滤、T2 明文配置）——每项均有对应任务。
2. **占位符扫描**：无 TBD、无“执行时再修正”类说明，所有代码均为可直接落盘的最终版本。
3. **类型一致性**：`ParsedChannel(name,url,logo,group,tvgId,order)` 与 `Channel(...subscriptionId...)` 映射见 T6；`EpgMatcher.keyForChannel` 返回 List<String> 在 T6 nowNext/todayProgrammes 一致；`EXTRA_*` 常量名跨 T7/T8/T9 一致。
