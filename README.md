# MBox 直播定制版

> 仓库/工程名：MBox-Live（基于 [q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS) 的直播定制版）

**下载安装**：直接到 [Releases](https://github.com/wnhhh/MBox-Live/releases/latest) 下载最新 APK 安装即可，内置直播源，装完就能看。

> 精力有限，未必会及时维护，仅用于学习。

## 这个版本定制了什么

- **内置直播源，开箱即看**：不用自己找源、不用配订阅，安装后直接进直播
- **地方频道按省份/国家分组**：找地方台不用在几百个频道里翻
- **全屏三分区手势**：竖向滑动按屏幕宽度三等分 —— 左 1/3 调亮度、中间 1/3 上下滑换台、右 1/3 调音量；横向滑动切线路
- **换台预加载（秒切）**：下一个台提前建连起流，换台时直接扶正，上下切台明显更快
- **直播起播调优**：HLS/FLV/TS 流的起播探测参数按直播场景收紧，黑屏等待更短
- **更新入口更直接**：「我的」页底部有「检查更新」按钮；「关于」页会显示当前版本，检查失败会说清原因

## 功能特性（继承自上游）

- **多源订阅**：远程 JSON 配置，兼容 BOM/注释/图片+base64/AES 等非标准套路（见 `doc/接口解析.md`）；支持本地 `.txt`/`.json` 文件导入（SAF `content://` 流）
- **点播 / 直播 / 快捷搜索**：多线路选集、EPG、倍速、字幕、投屏桩
- **内置下载**：并发调度（1-5）、断点续传、m3u8 分段下载合并 mp4、磁盘空间预检、断网自动续传、来源/剧名分目录；无存储权限时弹授权引导
- **自适应卡片**：剧集卡片统一 宽:高 = 3:4，列数随屏幕宽度自适应（单卡 ≤190dp），大屏旋转自动刷新
- **主题**：浅色/深色两套，构建期由 JSON 生成颜色资源，界面配色统一走 `@color/*`；播放控制抽屉固定深色白字
- **局域网服务**：默认仅本机回环（订阅/本地播放/爬虫代理/m3u8 代理）；开启后局域网设备可访问 web 控制台与文件共享，管理型请求带进程令牌鉴权
- **检查更新**：读本仓库的 GitHub Releases，APK 下载代理优先、直连兜底；接口直连不通时自动走加速通道重试
- **崩溃兜底 + 运行日志**：崩溃页与 logcat 完整捕获查看（设置开关）

## 下载与安装

| 项 | 说明 |
| --- | --- |
| 下载地址 | [Releases 最新版](https://github.com/wnhhh/MBox-Live/releases/latest)（APK 在附件里） |
| 包名 | `com.github.tvbox.osc.mbox`（与 TVBox 系通用的 `com.github.tvbox.osc` 区分，可与其它开源 TVBox 应用共存） |
| 系统要求 | Android 7.0（API 24）及以上 |
| 安装 | 首次安装需允许「安装未知应用」，App 启动时会引导 |
| 升级 | 同一签名可直接覆盖安装，数据保留 |

> **签名提示**：`v3.6.2` 起改用固定签名。从更早的版本（如 `v3.6.1`）升级到本版需要**先卸载再安装**（卸载会清除应用数据，建议先在「设置 → 备份」导出配置）。从本版开始，之后同一签名的版本都能直接覆盖升级。

## 构建环境（核心版本）

| 项                         | 版本                                                                                |
| -------------------------- | ----------------------------------------------------------------------------------- |
| **JDK**                    | **17**（AGP 8.x 必需）                                                              |
| **Gradle**                 | **8.4**（Wrapper 自带，见 `gradle/wrapper/gradle-wrapper.properties`）              |
| **Android Gradle Plugin**  | **8.2.2**                                                                           |
| **Kotlin**                 | **1.9.22**                                                                          |
| **compileSdk / targetSdk** | **34**                                                                              |
| **minSdk**                 | **24**                                                                              |
| Java 兼容级别              | 1.8（source/target）                                                                |
| 关键依赖                   | AndroidX、Material 1.9.0、OkHttp 4.12.0、ExoPlayer 2.18.7、Picasso 2.71828、QuickJS |

> `local.properties`（本机 SDK 路径）不入库，首次构建需用 Android Studio 打开或自行配置。

## 本地构建

Windows：

```bat
set JAVA_HOME=D:\path\to\jdk17
gradlew.bat assembleRelease
```

macOS / Linux：

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew assembleRelease
```

产物路径：`app/build/outputs/apk/release/`（如 `MBox_v3.6.2_release_YYYYMMDD.apk`；debug 包版本号末段 -1，便于自测更新）。
应用名/版本号/图标统一在 `app/app_config.properties` 维护，改完重新构建即可。

## 发布新版本

1. 改 `app/app_config.properties`：`versionName` 升一档（如 `3.6.2` → `3.6.3`），`versionCode` 单调 +1
2. 写 `doc/release-notes-v<版本>.md`：面向用户的更新说明（新增功能 / 功能变更 / 问题修复 / 体验与性能 / 兼容性注意），不要写提交号、文件名、依赖与构建细节
3. 本地自检：`node scripts/check-release-notes.js doc/release-notes-v3.6.3.md`
4. 打标签并推送：

   ```bash
   git tag v3.6.3
   git push origin v3.6.3
   ```

5. 工作流会自动构建并创建 Release，APK 挂到附件；App 内「检查更新」读的就是这个 Releases 列表

## GitHub Actions 打包

仓库内置工作流 [.github/workflows/build-apk.yml](.github/workflows/build-apk.yml)：

- **推送 `v*` 标签**：构建 → 创建 GitHub Release → 挂上 APK（正文取 `doc/release-notes-<tag>.md`）
- **推送 main / 手动触发**：只构建并上传 Artifacts，不发版

签名与包名说明：

- **正式签名**为项目专属 keystore（**不入库**）。本地构建读取 `app/keystore.properties`；CI 通过仓库 Secrets 注入：
  `KEYSTORE_BASE64`（keystore 的 base64）、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
  未配置 Secrets 时产物退回 debug 签名（**仅测试用**，且每次构建的 debug 证书不同，无法互相覆盖升级）。
- ⚠️ keystore 一旦丢失，就无法再给已发布的版本做覆盖升级，请务必离线备份 keystore 与口令。

## 模块结构

按 `改进.txt` 目标架构分层，依赖自上而下：

```
:app / feature
    ↓
业务契约(spiderapi/player 契约包 + DownloadFacade,现与实现同模块)
    ↓
业务实现: :spider、:player、:download
    ↓
基础设施: :core-network、:core-storage、:log、:common(工具+共享模型+系统状态)
    ↓
纯模型: :common 的 com.github.tvbox.osc.bean.*
```

**现状 9 个模块**：`:app`、`:common`、`:core-network`、`:core-storage`、`:log`、`:player`、`:spider`、`:download`、`:thirdparty`。
契约模块已回并业务模块（`:spider-api`→`:spider`、`:player-api`→`:player`），原 `:core-model`/`:core-utils`/`:state` 已并入 `:common`，`:crash`/`:TabLayout`/`:ViewPager1Delegate`/`:quickjs` 已并入 `:thirdparty`，主题/通用资源（原 `:ui-common`）已并入 `:app`。边界改由 `checkModuleDependencies` 源码层门禁守护（见 AGENTS.md §二）。

## 目录结构与文档

- `doc/` — 项目文档：
    - [项目目录结构.md](doc/项目目录结构.md) — 模块/包/关键类总览
    - [项目状态速查.md](doc/项目状态速查.md) — 版本、主题、组件、功能、已知注意速查
    - [接口解析.md](doc/接口解析.md) — 订阅解析机制与排查经验
    - [直播说明.md](doc/直播说明.md) — 直播源说明
    - [Git提交规范.md](doc/Git提交规范.md) — 提交信息格式要求
    - `release-notes-v*.md` — 各版本的用户可读更新说明（发版时由 CI 贴到 Release）

## 致谢

- 上游：[q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS)
- 直播源：[vbskycn/iptv](https://github.com/vbskycn/iptv)
- 推荐使用：[takagen99/Box](https://github.com/takagen99/Box)、[FongMi/TV](https://github.com/FongMi/TV)

## License

见 [LICENSE](LICENSE)
