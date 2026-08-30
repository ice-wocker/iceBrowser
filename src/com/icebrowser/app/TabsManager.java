package com.icebrowser.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class TabsManager {
    private final Context appContext;
    private final android.widget.FrameLayout container;
    private final List<Tab> tabs = new ArrayList<>();
    private int currentIndex = -1;
    private long nextId = 1;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TabsListener listener;
    private AdBlocker adBlocker;
    
    public interface TabsListener {
        void onTabsChanged();
        void onTabChanged(int index, Tab tab);
    }
    
    public static class Tab {
        public long id;
        public WebView webView;
        public String url;
        public String title;
        public Bitmap favicon;
        public boolean loading;
        public boolean incognito;
        public long createdAt;
        public WeakReference<android.graphics.Bitmap> thumbnail;
        
        public Tab(long id, WebView wv, boolean incognito) {
            this.id = id;
            this.webView = wv;
            this.incognito = incognito;
            this.createdAt = System.currentTimeMillis();
        }
    }
    
    public TabsManager(Context context, android.widget.FrameLayout container) {
        this.appContext = context.getApplicationContext();
        this.container = container;
        this.adBlocker = new AdBlocker(appContext);
    }
    
    public void setListener(TabsListener l) { this.listener = l; }
    public TabsListener getListener() { return listener; }
    public AdBlocker getAdBlocker() { return adBlocker; }
    
    public List<Tab> getAllTabs() { return new ArrayList<>(tabs); }
    public int getTabCount() { return tabs.size(); }
    public int getCurrentIndex() { return currentIndex; }
    public Tab getCurrentTab() {
        if (currentIndex >= 0 && currentIndex < tabs.size()) return tabs.get(currentIndex);
        return null;
    }
    public Tab getTab(int index) {
        if (index >= 0 && index < tabs.size()) return tabs.get(index);
        return null;
    }
    public int indexOf(Tab tab) { return tabs.indexOf(tab); }
    
    public Tab findByWebView(WebView wv) {
        if (wv == null) return null;
        for (Tab t : tabs) {
            if (t.webView == wv) return t;
        }
        return null;
    }
    
    public Tab createTab(String url, boolean incognito) {
        Tab tab = new Tab(nextId++, createWebView(incognito), incognito);
        tabs.add(tab);
        // 隐藏旧 tab
        if (currentIndex >= 0 && currentIndex < tabs.size() - 1) {
            Tab old = tabs.get(currentIndex);
            if (old.webView != null) old.webView.setVisibility(View.GONE);
        }
        currentIndex = tabs.size() - 1;
        if (tab.webView != null) {
            container.addView(tab.webView, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            tab.webView.setVisibility(View.VISIBLE);
        }
        if (url != null && tab.webView != null) {
            tab.url = url;
            tab.webView.loadUrl(url);
        }
        notifyChanged();
        return tab;
    }
    
    public void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (currentIndex == index) return;
        if (currentIndex >= 0 && currentIndex < tabs.size()) {
            Tab cur = tabs.get(currentIndex);
            if (cur.webView != null) cur.webView.setVisibility(View.GONE);
        }
        currentIndex = index;
        Tab next = tabs.get(index);
        if (next.webView != null) next.webView.setVisibility(View.VISIBLE);
        notifyChanged();
        notifyTabChanged();
    }
    
    public void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        Tab tab = tabs.remove(index);
        if (tab.webView != null) {
            container.removeView(tab.webView);
            tab.webView.stopLoading();
            tab.webView.destroy();
        }
        if (tabs.isEmpty()) {
            currentIndex = -1;
            // 创建新 home tab
            createTab("file:///android_asset/home.html", false);
            return;
        }
        if (currentIndex >= tabs.size()) currentIndex = tabs.size() - 1;
        if (currentIndex >= 0) {
            Tab cur = tabs.get(currentIndex);
            if (cur != null && cur.webView != null) cur.webView.setVisibility(View.VISIBLE);
        }
        notifyChanged();
        notifyTabChanged();
    }
    
    public void closeAll() {
        for (Tab tab : tabs) {
            if (tab.webView != null) {
                container.removeView(tab.webView);
                tab.webView.stopLoading();
                tab.webView.destroy();
            }
        }
        tabs.clear();
        currentIndex = -1;
        createTab("file:///android_asset/home.html", false);
    }
    
    public void loadUrlInCurrent(String url) {
        Tab t = getCurrentTab();
        if (t != null && t.webView != null) {
            t.url = url;
            t.webView.loadUrl(url);
        }
    }
    
    public void goBack() {
        Tab t = getCurrentTab();
        if (t != null && t.webView != null && t.webView.canGoBack()) t.webView.goBack();
    }
    
    public void goForward() {
        Tab t = getCurrentTab();
        if (t != null && t.webView != null && t.webView.canGoForward()) t.webView.goForward();
    }
    
    public void reloadCurrent() {
        Tab t = getCurrentTab();
        if (t != null && t.webView != null) t.webView.reload();
    }
    
    private WebView createWebView(boolean incognito) {
        WebView wv = null;
        try {
            wv = new WebView(appContext);
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(true);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setSupportZoom(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            s.setUserAgentString("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 iceBrowser/4.0");
            s.setSupportMultipleWindows(true);
            s.setJavaScriptCanOpenWindowsAutomatically(true);
            
            wv.setVerticalScrollBarEnabled(false);
            wv.setHorizontalScrollBarEnabled(false);
            wv.setNetworkAvailable(true);
            wv.setVisibility(View.GONE);
            
            // 关键: 设置 WebViewClient 防止系统浏览器跳转
            wv.setWebViewClient(new IceWebViewClient(this, adBlocker));
            wv.setWebChromeClient(new IceWebChromeClient(this, appContext));
        } catch (Exception e) {
            android.util.Log.e("TabsManager", "createWebView error", e);
        }
        return wv;
    }
    
    public void notifyTabChanged() {
        if (listener != null) {
            final Tab t = getCurrentTab();
            final int idx = currentIndex;
            mainHandler.post(new Runnable() {
                @Override public void run() { listener.onTabChanged(idx, t); }
            });
        }
    }
    
    public void notifyChanged() {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override public void run() { listener.onTabsChanged(); }
            });
        }
    }
    
    public void destroy() {
        for (Tab tab : tabs) {
            if (tab.webView != null) {
                tab.webView.destroy();
            }
        }
        tabs.clear();
    }
    
    private Object jsBridge;
    public void setJsBridge(Object bridge) { this.jsBridge = bridge; }
    
    public void injectBridgeToAll() {
        if (jsBridge == null) return;
        for (Tab t : tabs) {
            if (t.webView != null) {
                try { t.webView.addJavascriptInterface(jsBridge, "IceJsBridge"); } catch (Exception e) {}
            }
        }
    }
    
    public void injectBridgeTo(WebView wv) {
        if (jsBridge == null || wv == null) return;
        try { wv.addJavascriptInterface(jsBridge, "IceJsBridge"); } catch (Exception e) {}
    }
}
