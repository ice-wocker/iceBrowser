# ice 浏览器 (ice Browser)

一款极简但功能强大的 Android 浏览器。纯 Java 实现，零第三方依赖，单 dex，APK 小于 200KB。

A minimalist yet powerful Android browser. Pure Java, zero third-party dependencies, single dex, APK < 200KB.

## 特性 Features

### 核心浏览
- 多标签页管理（支持无痕模式）
- 前进/后退导航
- 主页快捷键
- URL 栏智能识别（网址直接访问，关键词搜索）
- 进度条加载指示
- 全屏视频播放支持
- 长按图片/链接支持

### 隐私与安全
- **广告拦截** - 内置 24000+ 广告规则
- 无痕浏览模式
- HTTPS 强制
- 阻止弹出窗口
- 不跟踪 (Do Not Track) 请求
- 隐私数据一键清除

### 阅读体验
- **阅读模式** - 自动提取正文，去除广告
- 字体大小可调
- 干净简洁的阅读界面
- 分享按钮

### 下载管理
- 后台多任务下载
- 断点续传
- 暂停/恢复
- 通知栏进度
- 文件类型自动识别
- 点击直接打开

### 数据管理
- 书签管理（增删改查）
- 浏览历史记录
- 多种搜索引擎（Bing/Google/DuckDuckGo/百度/搜狗）
- 桌面版/移动版切换

### 个性化
- 主题（跟随系统/浅色/深色）
- 用户代理切换
- 默认下载目录配置
- 主页自定义

## 技术栈 Tech Stack

- **语言**: 纯 Java (无 Kotlin)
- **平台**: Android 7.0+ (API 24+)
- **构建**: Termux + aapt + dx + apksigner (无 Gradle)
- **存储**: SQLite (历史/书签/下载)
- **首选项**: SharedPreferences
- **网络**: HttpURLConnection (无 OkHttp)
- **渲染**: WebView (Chrome 引擎)
- **依赖**: 0 第三方库

## 项目结构 Project Structure

```
icebrowser/
├── src/com/icebrowser/app/    # Java 源码
│   ├── MainActivity.java       # 主 Activity
│   ├── Tab.java                # 标签页封装
│   ├── TabManager.java         # 标签管理
│   ├── AdBlocker.java          # 广告拦截
│   ├── DatabaseHelper.java     # SQLite
│   ├── DownloadService.java    # 下载服务
│   ├── BookmarksActivity.java  # 书签
│   ├── HistoryActivity.java    # 历史
│   ├── DownloadsActivity.java  # 下载
│   ├── SettingsActivity.java   # 设置
│   ├── ReaderActivity.java     # 阅读模式
│   ├── TabsActivity.java       # 标签页网格
│   └── ...                     # WebView 客户端等
├── res/                        # Android 资源
│   ├── layout/                 # 13 个布局
│   ├── drawable/               # 28 个图标
│   └── values/                 # 颜色/字符串/主题
├── AndroidManifest.xml
└── build.sh                    # 一键构建脚本
```

## 编译 Build

```bash
cd icebrowser
bash build.sh
# 产物: icebrowser.apk (~150KB)
```

需要环境：
- Termux (Android)
- `android-tools` 包
- `apkbuild` 工具链 (含 android.jar)

## 安装 Install

```bash
adb install icebrowser.apk
# 或通过 Shizuku:
pm install -r /data/local/tmp/icebrowser.apk
```

## 权限 Permissions

- `INTERNET` - 网络访问
- `ACCESS_NETWORK_STATE` - 网络状态
- `WRITE_EXTERNAL_STORAGE` - 下载文件 (API 28)
- `READ_EXTERNAL_STORAGE` - 读取下载
- `FOREGROUND_SERVICE` - 下载后台服务

## 设计哲学 Design Philosophy

- **极简** - 没有花哨的功能，只有该有的
- **快速** - 启动快，切换快，加载快
- **隐私** - 默认拦截广告，不跟踪用户
- **轻量** - APK 150KB，内存占用低
- **透明** - 完全开源，无遥测，无后门

## 路线图 Roadmap

- [ ] 同步书签到云端
- [ ] 密码管理器集成
- [ ] 视频下载器
- [ ] 扩展支持 (用户脚本)
- [ ] 黑暗模式自动切换
- [ ] 翻译功能
- [ ] 截图工具

## 开源协议 License

MIT License - 详见 [LICENSE](LICENSE)

## 贡献 Contributing

欢迎提交 Issue 和 Pull Request！

## 致谢 Acknowledgments

- 启发自 [Iceweasel](https://www.mozilla.org/) 的设计哲学
- 广告规则基于 [EasyList](https://easylist.to/)
