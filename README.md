<p align="center">
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="License">
  <img src="https://img.shields.io/badge/Android-24%2B-green" alt="Android 24+">
  <img src="https://img.shields.io/badge/Java-8-orange" alt="Java 8">
  <img src="https://img.shields.io/badge/size-81KB-brightgreen" alt="81KB">
  <img src="https://img.shields.io/badge/dependencies-0-success" alt="0 deps">
  <img src="https://img.shields.io/github/stars/ice-wocker/iceBrowser?style=social" alt="Stars">
</p>


**v4.0 - 真正的浏览器 · 自研搜索引擎 · 单 dex 110KB · 零依赖**

一款极简但功能强大的 Android 浏览器。纯 Java 编写，单 dex APK，**无任何第三方库依赖**。

## 核心特性

### 🌐 真正的浏览器
- **真正的多 Tab 系统** - 每个 tab 独立 WebView，OS 级别隔离
- **target=_blank 拦截** - 所有新窗口在 ice 浏览器内打开新 tab，不会跳系统浏览器
- **intent:// market:// 拦截** - 不跳 Google Play / 第三方 app
- **tel: mailto: 处理** - 弹系统选择器（必要场景）
- **AdBlocker** - 30+ 域名/正则规则拦截

### 🔍 自研 ice 搜索引擎
- **本地 Spider** - 不依赖任何第三方 API
- **DuckDuckGo HTML 端点** + Bing/Google fallback
- **异步线程池** - UI 线程不卡顿
- **LRU 缓存** - 重复查询 < 100ms
- **智能建议** - 输入提示 + 静态数据库
- **JS 桥推送** - 流式回传结果

### 🏠 自研主页 (assets/home.html, 41KB)
- **DuckDuckGo 极简风格**
- 渐变色大标题 (蓝→绿)
- 6 引擎一键切换 (Bing/Google/DDG/百度/搜狗/ice 自研)
- 实时搜索建议 + 智能高亮
- 8 快捷方式 + 自定义添加
- 4 主题 (浅/深/护眼/黑白) - 自适应系统
- 抽屉导航 (历史/书签/下载/设置/标签页)

### 🪟 多 Tab 管理
- 创建/关闭/切换 tab
- 标签页网格视图 (3x2 缩略图)
- 关闭其他/全部
- Tab 切换动画

### 📚 数据管理
- 书签 (SQLite)
- 浏览历史
- 下载管理 (系统 DownloadManager)
- Cookie/Cache 管理

### ⚙️ 高级功能
- 阅读模式 (JS 注入提取正文)
- 整页翻译 (Bing 翻译 API)
- 桌面版/移动版 UA 切换
- 无痕模式
- 文件下载
- HTTPS 升级
- 长按图片保存/分享
- 文本选择 → 搜索/分享
- 全屏视频

## 技术栈

| 项目 | 详情 |
|------|------|
| 语言 | 纯 Java (无 Kotlin) |
| 平台 | Android 7.0+ (API 24+) |
| 构建 | Termux + aapt + dx + apksigner |
| 存储 | SQLite (历史/书签/下载) |
| 首选项 | SharedPreferences |
| 网络 | java.net.HttpURLConnection |
| 渲染 | WebView (Chrome 内核) |
| 依赖 | **0** 第三方库 |
| APK | 110KB |

## 项目结构

```
icebrowser/
├── src/com/icebrowser/app/    # Java 源码
│   ├── MainActivity.java       # 主 Activity + IceJsBridge
│   ├── TabsManager.java        # 多 Tab 管理 (核心)
│   ├── IceWebViewClient.java   # URL 拦截 (防止系统跳转)
│   ├── IceWebChromeClient.java # target=_blank → 新 tab
│   ├── IceSearchService.java   # 自研 ice 搜索
│   ├── AdBlocker.java          # 广告拦截
│   ├── DatabaseHelper.java     # SQLite
│   ├── DownloadService.java    # 系统 DownloadManager 包装
│   ├── BookmarksActivity.java
│   ├── HistoryActivity.java
│   ├── DownloadsActivity.java
│   ├── SettingsActivity.java
│   ├── ReaderActivity.java
│   ├── TabsActivity.java       # 标签页网格
│   ├── IceApp.java
│   └── IceApp.java
├── res/
│   ├── layout/                 # 11 个布局
│   ├── drawable/               # 28 个图标
│   └── values/                 # 颜色/字符串/主题
├── assets/
│   └── home.html               # 自研主页 (41KB)
├── AndroidManifest.xml
├── build.sh                    # Termux 一键构建
└── publish.sh                  # 一键发布到 GitHub
```

## 编译

```bash
cd icebrowser
bash build.sh
# 产物: icebrowser.apk (~110KB)
```

需要环境：
- Termux (Android)
- `android-tools` 包
- `apkbuild` 工具链

## 安装

```bash
adb install icebrowser.apk
# 或通过 Shizuku:
pm install -r /data/local/tmp/icebrowser.apk
```

## 权限

- `INTERNET` - 网络访问
- `ACCESS_NETWORK_STATE` - 网络状态
- `WRITE_EXTERNAL_STORAGE` - 下载文件

## 版本

- **v4.0** (current) - 真正的多 Tab + 自研 ice 搜索 + URL 拦截
- v3.0 - 自研搜索引擎 (套壳 Bing)
- v2.0 - 修复闪退 + assets 本地主页
- v1.0 - 初次发布

## 开源协议

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

## 致谢

- 启发自 [DuckDuckGo](https://duckduckgo.com/) 的极简设计
- 启发自 [Iceweasel](https://www.mozilla.org/) 的设计哲学
