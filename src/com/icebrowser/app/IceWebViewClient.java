package com.icebrowser.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;

public class IceWebViewClient extends WebViewClient {
    private final TabsManager tabsManager;
    private final AdBlocker adBlocker;
    
    public IceWebViewClient(TabsManager tabsManager, AdBlocker adBlocker) {
        this.tabsManager = tabsManager;
        this.adBlocker = adBlocker;
    }
    
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        return handleUrl(view, url);
    }
    
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return handleUrl(view, url);
    }
    
    private boolean handleUrl(WebView view, String url) {
        if (url == null) return false;
        if (url.startsWith("javascript:")) return false;
        if (url.startsWith("data:") || url.startsWith("about:")) return false;
        if (url.startsWith("file:///android_asset/") || url.startsWith("file:///android_res/")) return false;
        
        // 拦截系统跳转协议 - 这些应该由应用处理
        if (url.startsWith("intent://")) {
            return handleIntentUrl(view, url);
        }
        if (url.startsWith("market://") || url.startsWith("play.google.com")) {
            // 不跳 Google Play
            return true;
        }
        if (url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("mailto:")) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { view.getContext().startActivity(i); } catch (Exception e) {}
            return true;
        }
        
        // http/https 在当前 webview 加载
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:") || url.startsWith("content:")) {
            return false;
        }
        
        // 其他协议: 尝试启动 Activity
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            view.getContext().startActivity(i);
        } catch (Exception e) {}
        return true;
    }
    
    private boolean handleIntentUrl(WebView view, String url) {
        // intent://...#Intent;scheme=...;package=...;end
        try {
            int intentIdx = url.indexOf("#Intent");
            if (intentIdx > 0) {
                String intentPart = url.substring(intentIdx + 8);
                String action = null;
                String data = url.substring(8, intentIdx);
                String pkg = null;
                
                // 解析参数
                String[] parts = intentPart.split(";");
                Intent intent = new Intent();
                intent.setData(Uri.parse(data));
                for (String p : parts) {
                    if (p.startsWith("action=")) intent.setAction(p.substring(7));
                    else if (p.startsWith("package=")) pkg = p.substring(8);
                    else if (p.startsWith("S.browser_fallback_url=")) {
                        String fallback = p.substring(26);
                        view.loadUrl(fallback);
                        return true;
                    }
                }
                if (pkg != null) {
                    intent.setPackage(pkg);
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    view.getContext().startActivity(intent);
                } catch (Exception e) {
                    // app not installed, try fallback
                    if (data != null && data.startsWith("http")) {
                        view.loadUrl(data);
                    }
                }
            }
        } catch (Exception e) {}
        return true;
    }
    
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (adBlocker != null && adBlocker.shouldBlock(request)) {
            return adBlocker.createEmptyResponse();
        }
        return super.shouldInterceptRequest(view, request);
    }
    
    @Override
    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        if (tabsManager != null) {
            TabsManager.Tab tab = tabsManager.findByWebView(view);
            if (tab != null) {
                tab.url = url;
                tab.loading = true;
            }
            tabsManager.notifyTabChanged();
        }
    }
    
    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (tabsManager != null) {
            TabsManager.Tab tab = tabsManager.findByWebView(view);
            if (tab != null) {
                tab.url = url;
                tab.loading = false;
                tab.title = view.getTitle();
            }
            tabsManager.notifyTabChanged();
        }
    }
    
    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        // 用户选择继续浏览 (生产环境应弹窗)
        handler.proceed();
    }
}
