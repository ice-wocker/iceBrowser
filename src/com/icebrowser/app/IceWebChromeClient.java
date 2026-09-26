package com.icebrowser.app;

import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/**
 * WebChromeClient（v2）。
 *
 * 把「进度 / 文件选择 / 权限 / 全屏视频 / target=_blank」这些必须由 Activity
 * 介入的事件统一转成 {@link TabsManager.TabsListener} 回调，
 * 避免客户端里直接持有 Activity 造成内存泄漏。
 */
public class IceWebChromeClient extends WebChromeClient {

    private final TabsManager tabsManager;

    public IceWebChromeClient(TabsManager tabsManager) {
        this.tabsManager = tabsManager;
    }

    private boolean isCurrent(WebView view) {
        if (tabsManager == null) return false;
        TabsManager.Tab cur = tabsManager.getCurrentTab();
        return cur != null && cur.webView == view;
    }

    /** target=_blank / window.open 时新建标签。 */
    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
        if (tabsManager == null || resultMsg == null) return false;
        TabsManager.Tab newTab = tabsManager.createTab(null, false);
        if (newTab != null && newTab.webView != null) {
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(newTab.webView);
            resultMsg.sendToTarget();
            return true;
        }
        return false;
    }

    @Override
    public void onCloseWindow(WebView window) {
        if (tabsManager == null) return;
        TabsManager.Tab tab = tabsManager.findByWebView(window);
        if (tab != null) tabsManager.closeTab(tabsManager.indexOf(tab));
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        if (!isCurrent(view)) return;
        TabsManager.Tab tab = tabsManager.findByWebView(view);
        if (tab != null) tab.progress = newProgress;
        tabsManager.notifyProgress(newProgress);
    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        super.onReceivedTitle(view, title);
        if (tabsManager == null) return;
        TabsManager.Tab tab = tabsManager.findByWebView(view);
        if (tab != null) {
            tab.title = title;
            tabsManager.notifyTabChanged();
        }
    }

    @Override
    public void onReceivedIcon(WebView view, android.graphics.Bitmap icon) {
        super.onReceivedIcon(view, icon);
        if (tabsManager == null) return;
        TabsManager.Tab tab = tabsManager.findByWebView(view);
        if (tab != null) tab.favicon = icon;
    }

    // ---------------- 文件选择 ----------------

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<android.net.Uri[]> filePathCallback,
                                     FileChooserParams fileChooserParams) {
        if (tabsManager == null || tabsManager.getListener() == null) return false;
        tabsManager.getListener().onShowFileChooser(filePathCallback, fileChooserParams);
        return true;
    }

    // ---------------- 网页权限（摄像头 / 麦克风） ----------------

    @Override
    public void onPermissionRequest(PermissionRequest request) {
        if (tabsManager == null || tabsManager.getListener() == null) {
            request.deny();
            return;
        }
        tabsManager.getListener().onPermissionRequest(request);
    }

    // ---------------- 全屏视频 ----------------

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (tabsManager == null || tabsManager.getListener() == null)
            return;
        tabsManager.getListener().onFullscreenRequested(view, callback);
    }

    @Override
    public void onHideCustomView() {
        if (tabsManager == null || tabsManager.getListener() == null) return;
        tabsManager.getListener().onFullscreenExit();
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage cm) {
        // 控制台噪音直接忽略，避免拖慢页面
        return true;
    }
}