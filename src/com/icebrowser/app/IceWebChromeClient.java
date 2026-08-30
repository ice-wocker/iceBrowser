package com.icebrowser.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Toast;

public class IceWebChromeClient extends WebChromeClient {
    private final TabsManager tabsManager;
    private final Context context;
    private View customView;
    private android.webkit.WebChromeClient.CustomViewCallback customViewCallback;
    
    public IceWebChromeClient(TabsManager tabsManager, Context context) {
        this.tabsManager = tabsManager;
        this.context = context;
    }
    
    /**
     * target=_blank 链接 → 创建新 tab
     */
    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        if (tabsManager == null) return false;
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
        if (tabsManager != null) {
            TabsManager.Tab tab = tabsManager.findByWebView(window);
            if (tab != null) tabsManager.closeTab(tabsManager.indexOf(tab));
        }
    }
    
    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
    }
    
    @Override
    public void onReceivedTitle(WebView view, String title) {
        if (tabsManager != null) {
            TabsManager.Tab tab = tabsManager.findByWebView(view);
            if (tab != null) {
                tab.title = title;
                tabsManager.notifyTabChanged();
            }
        }
    }
    
    @Override
    public void onReceivedIcon(WebView view, android.graphics.Bitmap icon) {
        if (tabsManager != null) {
            TabsManager.Tab tab = tabsManager.findByWebView(view);
            if (tab != null) {
                tab.favicon = icon;
            }
        }
    }
    
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        return false;
    }
    
    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            String[] resources = request.getResources();
            // 直接授权, 简化体验
            request.grant(resources);
        }
    }
    
    @Override
    public boolean onConsoleMessage(ConsoleMessage cm) {
        return true;
    }
}
