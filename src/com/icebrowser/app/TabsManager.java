package com.icebrowser.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 多标签管理器（v2）。
 *
 * 每个标签持有独立的 WebView，隐藏而非销毁，切换时不重新加载。
 * 相比旧版新增：
 *  - 标签状态持久化（进程被杀后恢复上次打开的网页）；
 *  - 原生「页内查找」（WebView.findAllAsync，替代注入 JS 的 hack）；
 *  - 统一的应用设置下发（JS / 图片 / Cookie / UA / 强制深色 / 广告拦截）；
 *  - 切走标签时抓取真实缩略图；
 *  - 无痕标签不写历史、不存表单；
 *  - 桌面版 / 无痕状态按标签独立保存。
 */
public class TabsManager {

    private static final String TAG = "IceTabs";

    // ---------------- 监听器 ----------------

    public interface TabsListener {
        void onTabsChanged();

        void onTabChanged(int index, Tab tab);

        void onProgress(int progress);

        /** 进入全屏播放视频。 */
        void onFullscreenRequested(View view, WebChromeClient.CustomViewCallback callback);

        void onFullscreenExit();

        /** 网页请求选择文件（<input type=file>）。 */
        void onShowFileChooser(android.webkit.ValueCallback<android.net.Uri[]> callback,
                               WebChromeClient.FileChooserParams params);

        /** 网页请求摄像头 / 麦克风等权限。 */
        void onPermissionRequest(android.webkit.PermissionRequest request);

        /** 网页发起了下载。 */
        void onDownloadRequested(String url, String userAgent,
                                 String contentDisposition, String mimeType);

        /** 页面加载完成（用于记录历史）。 */
        void onPageFinished(Tab tab);
    }

    // ---------------- 标签模型 ----------------

    public static class Tab {
        public long id;
        public WebView webView;
        public String url;
        public String title;
        public Bitmap favicon;
        public Bitmap thumbnail;
        public boolean loading;
        public boolean incognito;
        public boolean desktopMode;
        /** 当前显示的是内置错误页，用于避免把失败地址写进历史。 */
        public boolean errorPage;
        public int progress;
        public long createdAt;
        public long lastActiveAt;

        public Tab(long id, WebView webView, boolean incognito) {
            this.id = id;
            this.webView = webView;
            this.incognito = incognito;
            this.createdAt = System.currentTimeMillis();
            this.lastActiveAt = createdAt;
        }
    }

    // ---------------- 字段 ----------------

    private final Activity activity;
    private final android.widget.FrameLayout container;
    private final List<Tab> tabs = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AdBlocker adBlocker;

    private int currentIndex = -1;
    private long nextId = 1;
    private TabsListener listener;
    private Object jsBridge;
    private boolean desktopMode;
    private boolean restored;

    public TabsManager(Activity activity, android.widget.FrameLayout container) {
        this.activity = activity;
        this.container = container;
        this.adBlocker = new AdBlocker(activity);
        this.desktopMode = IcePrefs.getBool(activity, IcePrefs.KEY_DESKTOP_MODE, false);
    }

    public void setListener(TabsListener l) {
        this.listener = l;
    }

    public TabsListener getListener() {
        return listener;
    }

    public AdBlocker getAdBlocker() {
        return adBlocker;
    }

    public void setJsBridge(Object bridge) {
        this.jsBridge = bridge;
    }

    // ---------------- 查询 ----------------

    public List<Tab> getAllTabs() {
        return new ArrayList<>(tabs);
    }

    public int getTabCount() {
        return tabs.size();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public Tab getCurrentTab() {
        if (currentIndex >= 0 && currentIndex < tabs.size()) return tabs.get(currentIndex);
        return null;
    }

    public Tab getTab(int index) {
        if (index >= 0 && index < tabs.size()) return tabs.get(index);
        return null;
    }

    public int indexOf(Tab tab) {
        return tabs.indexOf(tab);
    }

    public Tab findByWebView(WebView wv) {
        if (wv == null) return null;
        for (Tab t : tabs) {
            if (t.webView == wv) return t;
        }
        return null;
    }

    public boolean isDesktopMode() {
        return desktopMode;
    }

    // ---------------- 创建 / 关闭 / 切换 ----------------

    public Tab createTab(String url, boolean incognito) {
        return createTab(url, incognito, true);
    }

    /**
     * @param makeCurrent 是否立即切到新标签。
     *                    target=_blank 走的 onCreateWindow 需要它保持 true，
     *                    而恢复会话时我们逐个创建但不切换。
     */
    public Tab createTab(String url, boolean incognito, boolean makeCurrent) {
        Tab tab = null;
        try {
            WebView wv = createWebView(incognito);
            tab = new Tab(nextId++, wv, incognito);
            tab.desktopMode = desktopMode;
            tabs.add(tab);

            if (wv != null) {
                injectBridgeTo(wv);
                container.addView(wv, new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                wv.setVisibility(View.GONE);
            }

            if (makeCurrent) {
                Tab old = getCurrentTab();
                captureThumbnail(old);
                if (old != null && old.webView != null) old.webView.setVisibility(View.GONE);
                currentIndex = tabs.size() - 1;
                if (wv != null) wv.setVisibility(View.VISIBLE);
            }

            if (!TextUtils.isEmpty(url) && wv != null) {
                tab.url = url;
                wv.loadUrl(url);
            }
        } catch (Exception e) {
            Log.e(TAG, "createTab", e);
        }
        notifyChanged();
        return tab;
    }

    public void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (currentIndex == index) return;
        Tab old = getCurrentTab();
        captureThumbnail(old);
        if (old != null && old.webView != null) old.webView.setVisibility(View.GONE);

        currentIndex = index;
        Tab next = tabs.get(index);
        if (next.webView != null) {
            next.webView.setVisibility(View.VISIBLE);
            next.webView.requestFocus();
        }
        next.lastActiveAt = System.currentTimeMillis();
        notifyChanged();
        notifyTabChanged();
    }

    public void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        Tab tab = tabs.remove(index);
        destroyTab(tab);

        if (tabs.isEmpty()) {
            currentIndex = -1;
            createTab(Constants.HOME_URL, false);
            notifyTabChanged();
            return;
        }
        if (index < currentIndex) {
            currentIndex--;
        } else if (index == currentIndex) {
            if (currentIndex >= tabs.size()) currentIndex = tabs.size() - 1;
        }
        Tab cur = getCurrentTab();
        if (cur != null && cur.webView != null) cur.webView.setVisibility(View.VISIBLE);
        notifyChanged();
        notifyTabChanged();
    }

    /** 关闭除指定标签外的全部标签。 */
    public void closeOthers(int keepIndex) {
        if (keepIndex < 0 || keepIndex >= tabs.size()) return;
        Tab keep = tabs.get(keepIndex);
        for (int i = tabs.size() - 1; i >= 0; i--) {
            if (tabs.get(i) != keep) {
                destroyTab(tabs.remove(i));
            }
        }
        currentIndex = 0;
        if (keep != null && keep.webView != null) keep.webView.setVisibility(View.VISIBLE);
        notifyChanged();
        notifyTabChanged();
    }

    public void closeAll() {
        for (Tab t : tabs) {
            destroyTab(t);
        }
        tabs.clear();
        currentIndex = -1;
        if (activity != null) {
            CookieManager.getInstance().flush();
        }
        createTab(Constants.HOME_URL, false);
        notifyTabChanged();
    }

    /** 复制一个标签（同一 URL 新开）。 */
    public Tab duplicateTab(int index) {
        Tab src = getTab(index);
        if (src == null) return null;
        return createTab(src.url, src.incognito);
    }

    private void destroyTab(Tab tab) {
        if (tab == null || tab.webView == null) return;
        try {
            container.removeView(tab.webView);
            tab.webView.stopLoading();
            tab.webView.setWebChromeClient(null);
            tab.webView.setWebViewClient(null);
            tab.webView.loadUrl("about:blank");
            tab.webView.destroy();
        } catch (Exception e) {
            Log.w(TAG, "destroyTab", e);
        } finally {
            tab.webView = null;
            if (tab.thumbnail != null && !tab.thumbnail.isRecycled()) {
                tab.thumbnail.recycle();
                tab.thumbnail = null;
            }
        }
    }

    /** 抓取标签缩略图（切走或展示标签页时调用）。 */
    public void captureThumbnail(Tab tab) {
        if (tab == null || tab.webView == null) return;
        try {
            int w = tab.webView.getWidth();
            int h = tab.webView.getHeight();
            if (w <= 0 || h <= 0) return;
            int tw = 240;
            int th = Math.max(1, (int) (h * (tw / (float) w)));
            Bitmap bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bmp);
            float scale = tw / (float) w;
            canvas.scale(scale, scale);
            tab.webView.draw(canvas);
            if (tab.thumbnail != null && !tab.thumbnail.isRecycled()) {
                tab.thumbnail.recycle();
            }
            tab.thumbnail = bmp;
        } catch (Exception e) {
            Log.w(TAG, "captureThumbnail", e);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "captureThumbnail OOM");
        }
    }

    public void captureAllThumbnails() {
        for (Tab t : tabs) captureThumbnail(t);
    }

    // ---------------- 导航 ----------------

    public void loadUrlInCurrent(String url) {
        Tab t = getCurrentTab();
        if (t == null) {
            createTab(url, false);
            return;
        }
        if (t.webView == null) return;
        t.url = url;
        t.webView.loadUrl(url);
        showCurrent();
    }

    /** 在新标签打开（无当前标签时等价于当前标签）。 */
    public void loadUrlInNewTab(String url) {
        Tab t = createTab(url, false);
        showCurrent();
        if (t != null) notifyChanged();
    }

    private void showCurrent() {
        Tab t = getCurrentTab();
        if (t == null || t.webView == null) return;
        if (container.indexOfChild(t.webView) < 0) {
            container.addView(t.webView, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }
        t.webView.setVisibility(View.VISIBLE);
        updateUI();
    }

    public void goBack() {
        Tab t = getCurrentTab();
        if (t != null && t.webView != null && t.webView.canGoBack()) t.webView.goBack();
    }

    public void goForward() {
        Tab t = getCurrentTab();
        if (t != null && t.webView != null && t.webView.canGoForward()) t.webView.goForward();
    }

    public boolean canGoBack() {
        Tab t = getCurrentTab();
        return t != null && t.webView != null && t.webView.canGoBack();
    }

    public boolean canGoForward() {
        Tab t = getCurrentTab();
        return t != null && t.webView != null && t.webView.canGoForward();
    }

    public void reload() {
        Tab t = getCurrentTab();
        if (t != null && t.webView != null) t.webView.reload();
    }

    public void stopLoading() {
        Tab t = getCurrentTab();
        if (t != null && t.webView != null) {
            t.webView.stopLoading();
            t.loading = false;
            notifyChanged();
        }
    }

    public void loadHome() {
        loadUrlInCurrent(IcePrefs.getHomepage(activity));
    }

    // ---------------- 页内查找 ----------------

    public void findAll(String keyword, WebView.FindListener findListener) {
        Tab t = getCurrentTab();
        if (t == null || t.webView == null) return;
        if (TextUtils.isEmpty(keyword)) {
            t.webView.clearMatches();
            return;
        }
        t.webView.setFindListener(findListener);
        t.webView.findAllAsync(keyword);
    }

    public void findNext(boolean forward) {
        Tab t = getCurrentTab();
        if (t == null || t.webView == null) return;
        t.webView.findNext(forward);
    }

    public void clearFind() {
        Tab t = getCurrentTab();
        if (t == null || t.webView == null) return;
        t.webView.clearMatches();
        t.webView.setFindListener(null);
    }

    // ---------------- 设置 ----------------

    /** 切换桌面版 UA（全局 + 所有标签，并重载）。 */
    public void setDesktopMode(boolean value) {
        this.desktopMode = value;
        IcePrefs.setBool(activity, IcePrefs.KEY_DESKTOP_MODE, value);
        for (Tab t : tabs) {
            t.desktopMode = value;
            applyUserAgent(t);
            if (t.webView != null && t.url != null && UrlUtils.isHttp(t.url)) {
                t.webView.reload();
            }
        }
    }

    /** 把首选项中的设置下发到所有 WebView（设置界面改完后调用）。 */
    public void applySettings() {
        boolean adBlock = IcePrefs.getBool(activity, IcePrefs.KEY_ADBLOCK_ENABLED, true);
        if (adBlocker != null) adBlocker.setEnabled(adBlock);
        for (Tab t : tabs) {
            if (t.webView == null) continue;
            applySettingsTo(t);
        }
    }

    private void applySettingsTo(Tab t) {
        if (t == null || t.webView == null) return;
        WebSettings s = t.webView.getSettings();
        try {
            s.setJavaScriptEnabled(IcePrefs.getBool(activity, IcePrefs.KEY_JS_ENABLED, true));
            s.setLoadsImagesAutomatically(IcePrefs.getBool(activity, IcePrefs.KEY_IMAGES_ENABLED, true));
            s.setBlockNetworkImage(!IcePrefs.getBool(activity, IcePrefs.KEY_IMAGES_ENABLED, true));
            s.setSaveFormData(!t.incognito
                    && IcePrefs.getBool(activity, IcePrefs.KEY_SAVE_FORM_DATA, true));
            applyUserAgent(t);
            applyForceDark(t);
        } catch (Exception e) {
            Log.w(TAG, "applySettingsTo", e);
        }
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(!t.incognito
                    && IcePrefs.getBool(activity, IcePrefs.KEY_COOKIES_ENABLED, true));
            cm.setAcceptThirdPartyCookies(t.webView,
                    IcePrefs.getBool(activity, IcePrefs.KEY_COOKIES_ENABLED, true));
        } catch (Exception e) {
            Log.w(TAG, "cookies", e);
        }
    }

    private void applyUserAgent(Tab t) {
        if (t == null || t.webView == null) return;
        try {
            String ua = IcePrefs.getString(activity, IcePrefs.KEY_USER_AGENT, "default");
            String value;
            if ("desktop".equals(ua) || t.desktopMode) {
                value = Constants.UA_DESKTOP;
            } else if ("mobile".equals(ua)) {
                value = Constants.UA_MOBILE;
            } else {
                value = null; // 用系统默认，兼容性最好
            }
            t.webView.getSettings().setUserAgentString(value);
        } catch (Exception e) {
            Log.w(TAG, "applyUserAgent", e);
        }
    }

    /**
     * 让普通网页跟随深色主题。
     * 用反射调用，因为 setForceDark 是 API 29+，而 setAlgorithmicDarkeningAllowed 是 API 33+。
     */
    private void applyForceDark(Tab t) {
        if (t == null || t.webView == null) return;
        if (!IcePrefs.getBool(activity, IcePrefs.KEY_FORCE_DARK, true)) return;
        boolean dark = ThemeManager.isDark(activity);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                java.lang.reflect.Method m = WebSettings.class.getMethod(
                        "setAlgorithmicDarkeningAllowed", boolean.class);
                m.invoke(t.webView.getSettings(), dark);
            } else if (Build.VERSION.SDK_INT >= 29) {
                java.lang.reflect.Method m = WebSettings.class.getMethod(
                        "setForceDark", int.class);
                // FORCE_DARK_OFF=0, FORCE_DARK_ON=1
                m.invoke(t.webView.getSettings(), dark ? 1 : 0);
            }
        } catch (Throwable e) {
            Log.d(TAG, "force dark unavailable: " + e.getMessage());
        }
    }

    // ---------------- WebView 构造 ----------------

    private WebView createWebView(boolean incognito) {
        WebView wv = new WebView(activity);
        try {
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(true);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            s.setSupportZoom(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setCacheMode(incognito ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
            s.setSupportMultipleWindows(true);
            s.setJavaScriptCanOpenWindowsAutomatically(true);
            s.setGeolocationEnabled(false);
            s.setMediaPlaybackRequiresUserGesture(true);
            s.setDefaultTextEncodingName("UTF-8");
            // 混合内容：Android 5.0+ 默认不允许 http 资源混在 https 页面，
            // 这里放开以兼容大量老旧站点，但正式站点仍走 https。
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            if (Build.VERSION.SDK_INT >= 26) {
                s.setSafeBrowsingEnabled(true);
            }

            wv.setVerticalScrollBarEnabled(false);
            wv.setHorizontalScrollBarEnabled(false);
            wv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            wv.setBackgroundColor(0x00000000);
            wv.setVisibility(View.GONE);
            wv.setWebViewClient(new IceWebViewClient(this, adBlocker));
            wv.setWebChromeClient(new IceWebChromeClient(this));
            wv.setDownloadListener(new android.webkit.DownloadListener() {
                @Override
                public void onDownloadStart(String url, String userAgent,
                                            String contentDisposition, String mimeType,
                                            long contentLength) {
                    if (listener != null) {
                        listener.onDownloadRequested(url, userAgent, contentDisposition, mimeType);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "createWebView", e);
        }
        return wv;
    }

    public void injectBridgeToAll() {
        for (Tab t : tabs) {
            if (t.webView != null) injectBridgeTo(t.webView);
        }
    }

    public void injectBridgeTo(WebView wv) {
        if (jsBridge == null || wv == null) return;
        try {
            wv.addJavascriptInterface(jsBridge, "IceJsBridge");
        } catch (Exception e) {
            Log.w(TAG, "injectBridge", e);
        }
    }

    // ---------------- 通知 ----------------

    public void notifyChanged() {
        if (listener == null) return;
        mainHandler.post(new Runnable() {
            @Override public void run() {
                if (listener != null) listener.onTabsChanged();
            }
        });
    }

    public void notifyTabChanged() {
        if (listener == null) return;
        final Tab t = getCurrentTab();
        final int idx = currentIndex;
        mainHandler.post(new Runnable() {
            @Override public void run() {
                if (listener != null) listener.onTabChanged(idx, t);
            }
        });
    }

    /** 立即（同步）刷新工具栏状态，避免异步 post 造成的闪烁。 */
    public void updateUI() {
        if (listener != null) listener.onTabChanged(currentIndex, getCurrentTab());
    }

    /** 页面加载进度（来自 WebChromeClient），仅转发给监听器。 */
    public void notifyProgress(int progress) {
        final int p = progress;
        if (listener == null) return;
        mainHandler.post(new Runnable() {
            @Override public void run() {
                if (listener != null) listener.onProgress(p);
            }
        });
    }

    /** 某个标签页加载完成（来自 WebViewClient），用于记录历史。 */
    public void notifyPageFinished(final Tab tab) {
        if (listener == null || tab == null) return;
        mainHandler.post(new Runnable() {
            @Override public void run() {
                if (listener != null) listener.onPageFinished(tab);
            }
        });
    }

    // ---------------- 持久化 ----------------

    /** 把当前打开的标签写入首选项，供下次启动恢复。 */
    public void saveState() {
        if (!IcePrefs.getBool(activity, IcePrefs.KEY_RESTORE_TABS, true)) return;
        try {
            JSONArray arr = new JSONArray();
            for (Tab t : tabs) {
                if (t.incognito) continue; // 无痕标签绝不落盘
                String url = t.url;
                if (TextUtils.isEmpty(url) || Constants.ABOUT_BLANK.equals(url)) continue;
                JSONObject o = new JSONObject();
                o.put("url", url);
                o.put("title", t.title == null ? "" : t.title);
                o.put("desktop", t.desktopMode);
                arr.put(o);
            }
            IcePrefs.setString(activity, "saved_tabs", arr.toString());
            IcePrefs.setInt(activity, "saved_tab_index", currentIndex);
        } catch (Exception e) {
            Log.w(TAG, "saveState", e);
        }
    }

    /** 恢复上次会话。返回是否真的恢复了标签。 */
    public boolean restoreState() {
        if (restored) return false;
        restored = true;
        if (!IcePrefs.getBool(activity, IcePrefs.KEY_RESTORE_TABS, true)) return false;
        String raw = IcePrefs.getString(activity, "saved_tabs", null);
        if (TextUtils.isEmpty(raw)) return false;
        try {
            JSONArray arr = new JSONArray(raw);
            if (arr.length() == 0) return false;
            boolean first = true;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", null);
                if (TextUtils.isEmpty(url)) continue;
                Tab t = createTab(null, false, first);
                if (t != null && t.webView != null) {
                    t.url = url;
                    t.title = o.optString("title", null);
                    t.desktopMode = o.optBoolean("desktop", false);
                    applySettingsTo(t);
                    t.webView.loadUrl(url);
                }
                first = false;
            }
            int idx = IcePrefs.getInt(activity, "saved_tab_index", 0);
            if (idx > 0 && idx < tabs.size()) {
                switchToTab(idx);
            }
            notifyChanged();
            notifyTabChanged();
            return tabs.size() > 0;
        } catch (Exception e) {
            Log.w(TAG, "restoreState", e);
            return false;
        }
    }

    public void clearSavedState() {
        IcePrefs.setString(activity, "saved_tabs", null);
    }

    /** 求和辅助：当前是否已有主页标签。 */
    public Tab findHomeTab() {
        for (Tab t : tabs) {
            if (t.url == null || Constants.ABOUT_BLANK.equals(t.url)
                    || t.url.startsWith(Constants.HOME_URL)) {
                return t;
            }
        }
        return null;
    }

    public int getTotalTabCountIncludingIncognito() {
        return tabs.size();
    }

    /** Activity 销毁时释放全部 WebView。 */
    public void destroy() {
        for (Tab t : tabs) {
            if (t.webView != null) {
                try {
                    container.removeView(t.webView);
                    t.webView.destroy();
                } catch (Exception ignored) {
                }
                t.webView = null;
            }
        }
        tabs.clear();
        currentIndex = -1;
    }
}