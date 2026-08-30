# 编译错误修复清单

## 1. 核心类修复
- [ ] AdBlocker: 构造函数接受 Context，IceApp 传 this
- [ ] IceApp: 添加 getTabManager(), getDownloadService()
- [ ] IceFileProvider: 添加静态 getUriForFile 方法
- [ ] Settings: SearchEngine 类引用修复

## 2. 移除 ENDOFFILE 语法错误
- [ ] IceWebViewClient.java
- [ ] MainActivity.java
- [ ] TabsActivity.java
- [ ] BookmarksActivity.java
- [ ] HistoryActivity.java
- [ ] DownloadsActivity.java

## 3. 缺失导入
- [ ] ValueCallback import (MainActivity, IceWebChromeClient)
- [ ] Pair import (SettingsActivity)

## 4. R.id 资源引用修复 - 检查 layout XML
- [ ] browser_container, find_bar, find_edit, find_count
- [ ] custom_view_container
- [ ] tabs_list, bookmarks_list, downloads_list, history_list
- [ ] tab_close, bookmark_favicon, download_name, download_url, download_action

## 5. 字符串资源补全 strings.xml
- [ ] share_link, copied, load_error, select_file
- [ ] duplicate_tab, edit_bookmark, delete_bookmark, bookmark_deleted, untitled
- [ ] clear_history, clear_history_confirm, clear, delete_history_item, deleted
- [ ] cannot_open_file, file_not_found, open_file, delete_download
- [ ] open, pause, download_failed, download_pending

## 6. TabManager 回调接口 & MainActivity 实现
- [ ] 定义 TabManagerListener 接口
- [ ] MainActivity 实现所有回调方法
- [ ] 修复参数匹配问题

## 7. Tab 类缺失方法
- [ ] isDesktopMode(), setDesktopMode(), print()
- [ ] onRequestPermissionsResult()

## 8. DownloadService resume() 方法重命名

## 9. SettingsActivity lambda & Pair 问题
