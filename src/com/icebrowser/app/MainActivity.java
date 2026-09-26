package com.icebrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 主界面（v2）。
 *
 * 集成了：多标签 TabsManager、边缘滑动导航、原生页内查找、全屏视频、
 * 文件选择、下载、错误页、历史入库、4 套主题与完整 JS 桥。
 */
public class MainActivity extends Activity implements TabsManager.TabsListener {

    private static final String TAG = "iceBrowser";
    private static final String HOME_URL = Constants.HOME_URL;

    /** 供 TabsActivity / 其它界面访问当前标签管理器。 */
    public static TabsManager staticTabsManager;

    private IceSwipeLayout webContainer;
    private EditText urlEdit;
    private ProgressBar progressBar;
    private ImageButton btnBack, btnForward, btnRefresh, btnTabs, btnMenu;
    private LinearLayout bottomBar;
    private View topBar;
    private FrameLayout fullscreenContainer;

    private View findBar;
    private EditText findEdit;
    private TextView findCount;

    private TabsManager tabsManager;
    private IceSearchService searchService;

    private ValueCallback<Uri[]> filePathCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    /** 记录已应用的主题，用于从设置页返回时判断是否需要重建。 */
    private String appliedTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ThemeManager.applyTo(this);
            appliedTheme = ThemeManager.resolve(this);

            searchService = new IceSearchService();
            setContentView(R.layout.activity_main);

            bindViews();
            wireToolbar();

            tabsManager = new TabsManager(this, webContainer);
            tabsManager.setListener(this);
            tabsManager.setJsBridge(new IceJsBridge(this));
            staticTabsManager = tabsManager;

            // 恢复上次会话；restoreState 自带幂等标志，重复调用无副作用。
            // 若没有可恢复的标签，下面会开一个主页。
            tabsManager.restoreState();

            handleIntent(getIntent());
            if (tabsManager.getTabCount() == 0) {
                tabsManager.createTab(IcePrefs.getHomepage(this), false);
            }
            tabsManager.injectBridgeToAll();
            updateUI();
        } catch (Throwable t) {
            Log.e(TAG, "onCreate", t);
            Toast.makeText(this, "启动失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void bindViews() {
        webContainer = (IceSwipeLayout) findViewById(R.id.web_container);
        urlEdit = (EditText) findViewById(R.id.url_edit);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        btnBack = (ImageButton) findViewById(R.id.btn_back);
        btnForward = (ImageButton) findViewById(R.id.btn_forward);
        btnRefresh = (ImageButton) findViewById(R.id.btn_refresh);
        btnTabs = (ImageButton) findViewById(R.id.btn_tabs);
        btnMenu = (ImageButton) findViewById(R.id.btn_menu);
        topBar = findViewById(R.id.top_bar);
        bottomBar = (LinearLayout) findViewById(R.id.bottom_bar);
        fullscreenContainer = (FrameLayout) findViewById(R.id.fullscreen_container);
        findBar = findViewById(R.id.find_bar);
        findEdit = (EditText) findViewById(R.id.find_edit);
        findCount = (TextView) findViewById(R.id.find_count);

        // 底栏 4 个入口
        wireBottom(R.id.bottom_bookmarks, BookmarksActivity.class);
        wireBottom(R.id.bottom_history, HistoryActivity.class);
        wireBottom(R.id.bottom_downloads, DownloadsActivity.class);
        wireBottom(R.id.bottom_settings, SettingsActivity.class);
    }

    private void wireBottom(int id, final Class<? extends Activity> cls) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startActivitySafely(cls); }
        });
    }

    private void wireToolbar() {
        if (btnBack != null) btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tabsManager.goBack(); updateUI(); }
        });
        if (btnForward != null) btnForward.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tabsManager.goForward(); updateUI(); }
        });
        if (btnRefresh != null) btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tabsManager.reload(); }
        });
        if (btnTabs != null) btnTabs.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openTabsList(); }
        });
        if (btnMenu != null) btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(); }
        });

        if (urlEdit != null) {
            urlEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                        String text = urlEdit.getText().toString().trim();
                        if (!TextUtils.isEmpty(text)) {
                            loadUrlOrSearch(text);
                            hideKeyboard();
                        }
                        return true;
                    }
                    return false;
                }
            });
        }

        if (webContainer != null) {
            webContainer.setCallback(new IceSwipeLayout.Callback() {
                @Override public boolean canGoBack() { return tabsManager != null && tabsManager.canGoBack(); }
                @Override public boolean canGoForward() { return tabsManager != null && tabsManager.canGoForward(); }
                @Override public void onSwipeBack() { if (tabsManager != null) { tabsManager.goBack(); updateUI(); } }
                @Override public void onSwipeForward() { if (tabsManager != null) { tabsManager.goForward(); updateUI(); } }
            });
            webContainer.setGestureEnabled(IcePrefs.getBool(this, IcePrefs.KEY_SWIPE_NAV, true));
        }

        wireFindBar();
    }

    private void wireFindBar() {
        if (findBar == null) return;
        View close = findViewById(R.id.btn_find_close);
        View prev = findViewById(R.id.btn_find_prev);
        View next = findViewById(R.id.btn_find_next);
        if (close != null) close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hideFindBar(); }
        });
        if (prev != null) prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (tabsManager != null) tabsManager.findNext(false); }
        });
        if (next != null) next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (tabsManager != null) tabsManager.findNext(true); }
        });
        if (findEdit != null) {
            findEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        doFind(v.getText().toString());
                        return true;
                    }
                    return false;
                }
            });
            findEdit.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { doFind(s.toString()); }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    // ---------------- 输入 / 导航 ----------------

    private void startActivitySafely(Class<? extends Activity> cls) {
        try {
            startActivity(new Intent(this, cls));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null || tabsManager == null) return;
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String url = data.toString();
            if (UrlUtils.isHttp(url)) {
                tabsManager.createTab(url, false);
                return;
            }
        }
        if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!TextUtils.isEmpty(text)) loadUrlOrSearch(text);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        updateUI();
    }

    /** 智能区分网址与搜索词。 */
    private void loadUrlOrSearch(String input) {
        if (TextUtils.isEmpty(input) || tabsManager == null) return;
        String text = input.trim();

        String url = UrlUtils.normalize(text);
        if (url != null) {
            tabsManager.loadUrlInCurrent(url);
            return;
        }

        // 搜索：ice 引擎走内置主页异步搜索，其它引擎直接跳转
        String engine = IcePrefs.getSearchEngine(this);
        if (Constants.ENGINE_ICE.equals(engine)) {
            try {
                String home = IcePrefs.getHomepage(this);
                if (home.startsWith("file:///android_asset/home.html")) {
                    tabsManager.loadUrlInCurrent(home + "?q=" + Uri.encode(text));
                    return;
                }
            } catch (Exception ignored) {}
        }
        String searchUrl = Constants.buildSearchUrl(engine, text);
        if (searchUrl == null) {
            searchUrl = Constants.buildSearchUrl("Bing", text);
        }
        tabsManager.loadUrlInCurrent(searchUrl);
    }

    private void updateUI() {
        if (tabsManager == null) return;
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab == null) return;
        if (urlEdit != null && !urlEdit.hasFocus()) {
            urlEdit.setText(tab.url != null ? UrlUtils.prettyUrl(tab.url) : "");
        }
        if (tab.webView != null) {
            if (btnBack != null) btnBack.setAlpha(tab.webView.canGoBack() ? 1f : 0.3f);
            if (btnForward != null) btnForward.setAlpha(tab.webView.canGoForward() ? 1f : 0.3f);
        }
    }

    private void openTabsList() {
        startActivitySafely(TabsActivity.class);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View v = getCurrentFocus();
            if (v != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    // ---------------- 页内查找（原生） ----------------

    private void showFindBar() {
        if (findBar == null || tabsManager == null) return;
        findBar.setVisibility(View.VISIBLE);
        if (findEdit != null) {
            findEdit.requestFocus();
            doFind(findEdit.getText().toString());
        }
    }

    private void hideFindBar() {
        if (findBar == null) return;
        findBar.setVisibility(View.GONE);
        if (tabsManager != null) tabsManager.clearFind();
        hideKeyboard();
    }

    private void doFind(String keyword) {
        if (tabsManager == null) return;
        if (TextUtils.isEmpty(keyword)) {
            tabsManager.clearFind();
            setFindCount(0, 0);
            return;
        }
        tabsManager.findAll(keyword, new WebView.FindListener() {
            @Override
            public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches, boolean isDoneCounting) {
                setFindCount(activeMatchOrdinal, numberOfMatches);
            }
        });
    }

    private void setFindCount(int active, int total) {
        if (findCount == null) return;
        findCount.setText(total <= 0 ? "0/0" : active + "/" + total);
    }

    // ---------------- TabsListener ----------------

    @Override
    public void onTabsChanged() {
        updateUI();
    }

    @Override
    public void onTabChanged(int index, TabsManager.Tab tab) {
        updateUI();
    }

    @Override
    public void onProgress(int progress) {
        if (progressBar == null) return;
        if (progress >= 100) {
            progressBar.setVisibility(View.GONE);
            progressBar.setProgress(100);
            updateUI();
        } else {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(progress);
        }
    }

    @Override
    public void onFullscreenRequested(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            if (callback != null) callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        if (fullscreenContainer != null) {
            fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fullscreenContainer.setVisibility(View.VISIBLE);
        }
        if (topBar != null) topBar.setVisibility(View.GONE);
        if (bottomBar != null) bottomBar.setVisibility(View.GONE);
    }

    @Override
    public void onFullscreenExit() {
        if (customView == null) return;
        try {
            if (fullscreenContainer != null) fullscreenContainer.removeView(customView);
            if (fullscreenContainer != null) fullscreenContainer.setVisibility(View.GONE);
        } catch (Exception ignored) {}
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        if (topBar != null) topBar.setVisibility(View.VISIBLE);
        if (bottomBar != null) bottomBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onShowFileChooser(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
        }
        filePathCallback = callback;
        try {
            Intent intent = params != null ? params.createIntent() : null;
            if (intent == null) {
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
            }
            startActivityForResult(intent, Constants.REQ_FILE_CHOOSER);
        } catch (Exception e) {
            filePathCallback = null;
            if (callback != null) callback.onReceiveValue(null);
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPermissionRequest(PermissionRequest request) {
        if (request == null) return;
        // 只放行摄像头 / 麦克风，其它权限一律拒绝
        String[] resources = request.getResources();
        for (String r : resources) {
            if (!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)
                    && !PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                request.deny();
                return;
            }
        }
        request.grant(resources);
    }

    @Override
    public void onDownloadRequested(String url, String userAgent, String contentDisposition, String mimeType) {
        long row = DownloadService.startDownload(this, url, userAgent, contentDisposition, mimeType);
        Toast.makeText(this, row > 0 ? "已开始下载" : "下载失败", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPageFinished(TabsManager.Tab tab) {
        if (tab == null || tab.incognito) return;
        if (UrlUtils.isHttp(tab.url)) {
            DatabaseHelper db = new DatabaseHelper(this);
            try {
                db.addHistory(tab.url, tab.title);
            } finally {
                db.close();
            }
        }
    }

    // ---------------- 文件选择结果 ----------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQ_FILE_CHOOSER) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    results = new Uri[n];
                    for (int i = 0; i < n; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onResume() {
        super.onResume();
        if (tabsManager == null) return;
        // 设置页可能改了主题：重建以套用新配色
        if (appliedTheme != null && !appliedTheme.equals(ThemeManager.resolve(this))) {
            recreate();
            return;
        }
        // 下发最新设置（JS / 图片 / Cookie / UA / 强制深色 / 广告拦截）
        tabsManager.applySettings();
        if (webContainer != null) {
            webContainer.setGestureEnabled(IcePrefs.getBool(this, IcePrefs.KEY_SWIPE_NAV, true));
        }
        tabsManager.updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tabsManager != null) tabsManager.saveState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (tabsManager != null) {
                tabsManager.saveState();
                tabsManager.destroy();
            }
        } catch (Exception ignored) {}
        // 搜索服务持有常驻线程池，必须随 Activity 一起释放，否则每次重建都泄漏 3 个线程
        if (searchService != null) searchService.shutdown();
        tabsManager = null;
        staticTabsManager = null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (findBar != null && findBar.getVisibility() == View.VISIBLE) {
                hideFindBar();
                return true;
            }
            if (customView != null) {
                onFullscreenExit();
                return true;
            }
            if (tabsManager != null) {
                if (tabsManager.canGoBack()) {
                    tabsManager.goBack();
                    updateUI();
                    return true;
                }
                if (tabsManager.getTabCount() > 1) {
                    tabsManager.closeTab(tabsManager.getCurrentIndex());
                    updateUI();
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------- 菜单 ----------------

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(attr, tv, true)) return tv.data;
        return 0xFF202124;
    }

    private void showMenu() {
        final String[] items = {
            "新建标签", "页面查找", "分享", "复制链接", "添加到书签",
            "阅读模式", "桌面版", "无痕标签", "下载", "切换主题", "设置", "关于"
        };
        final int[] icons = {
            R.drawable.ic_add, R.drawable.ic_find, R.drawable.ic_share, R.drawable.ic_copy,
            R.drawable.ic_bookmark, R.drawable.ic_reader, R.drawable.ic_desktop,
            R.drawable.ic_incognito, R.drawable.ic_download, R.drawable.ic_globe,
            R.drawable.ic_settings, R.drawable.ic_info
        };
        try {
            int density = (int) getResources().getDisplayMetrics().density;
            LinearLayout menuView = new LinearLayout(this);
            menuView.setOrientation(LinearLayout.VERTICAL);
            menuView.setBackgroundResource(R.drawable.menu_background);

            final PopupWindow popup = new PopupWindow(this);
            popup.setContentView(menuView);
            popup.setWidth(230 * density);
            popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            // menu_background 里用了 ?attr/iceToolbarBg 等主题属性，
            // getResources().getDrawable(int) 不套用 Activity 主题，会解析失败，
            // 异常被下面的 catch 吞掉后 showAsDropDown 就永远执行不到 —— 菜单点不开。
            // 直接复用 menuView 已经按主题设好的背景。
            popup.setBackgroundDrawable(menuView.getBackground());
            popup.setOutsideTouchable(true);
            popup.setFocusable(true);

            int fg = themeColor(R.attr.iceToolbarFg);
            int iconTint = themeColor(R.attr.iceIconTint);
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(20 * density, 13 * density, 20 * density, 13 * density);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setClickable(true);
                row.setFocusable(true);

                ImageView icon = new ImageView(this);
                icon.setImageResource(icons[i]);
                icon.setColorFilter(iconTint);
                row.addView(icon, new LinearLayout.LayoutParams(22 * density, 22 * density));

                TextView text = new TextView(this);
                text.setText(menuLabel(i, items[i]));
                text.setTextSize(14);
                text.setTextColor(fg);
                text.setPadding(18 * density, 0, 0, 0);
                row.addView(text, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        popup.dismiss();
                        handleMenuClick(idx);
                    }
                });
                menuView.addView(row);
            }
            if (btnMenu != null) popup.showAsDropDown(btnMenu, 0, 0);
        } catch (Exception e) {
            Log.e(TAG, "showMenu", e);
        }
    }

    /** 部分菜单项显示当前状态（桌面版 / 收藏）。 */
    private String menuLabel(int index, String def) {
        if (index == 6 && tabsManager != null) {
            return tabsManager.isDesktopMode() ? "桌面版（已开启）" : "请求桌面版";
        }
        return def;
    }

    private void handleMenuClick(int index) {
        switch (index) {
            case 0:
                tabsManager.createTab(IcePrefs.getHomepage(this), false);
                break;
            case 1: showFindBar(); break;
            case 2: shareCurrent(); break;
            case 3: copyUrl(); break;
            case 4: addBookmark(); break;
            case 5: enterReaderMode(); break;
            case 6: toggleDesktop(); break;
            case 7: openIncognito(); break;
            case 8: startActivitySafely(DownloadsActivity.class); break;
            case 9: cycleTheme(); break;
            case 10: startActivitySafely(SettingsActivity.class); break;
            case 11: showAbout(); break;
        }
    }

    private void shareCurrent() {
        TabsManager.Tab tab = tabsManager != null ? tabsManager.getCurrentTab() : null;
        if (tab == null || tab.webView == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, tab.url);
            startActivity(Intent.createChooser(intent, "分享"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyUrl() {
        TabsManager.Tab tab = tabsManager != null ? tabsManager.getCurrentTab() : null;
        if (tab == null || tab.url == null) return;
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", tab.url));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void addBookmark() {
        TabsManager.Tab tab = tabsManager != null ? tabsManager.getCurrentTab() : null;
        if (tab == null || tab.url == null) return;
        DatabaseHelper db = new DatabaseHelper(this);
        try {
            boolean ok = db.addBookmark(tab.url, tab.title != null ? tab.title : tab.url,
                    DatabaseHelper.FOLDER_ROOT);
            Toast.makeText(this, ok ? "已添加书签" : "添加失败", Toast.LENGTH_SHORT).show();
        } finally {
            db.close();
        }
    }

    /** 阅读模式：抽取正文后交给 ReaderActivity 渲染（可调字号、跟随主题）。 */
    private void enterReaderMode() {
        TabsManager.Tab tab = tabsManager != null ? tabsManager.getCurrentTab() : null;
        if (tab == null || tab.webView == null) return;
        String js = "(function(){"
                + "var a=document.querySelector('article')||document.querySelector('main')||document.body;"
                + "var t=document.title||'';"
                + "var c=a?(a.innerText||''):(document.body?document.body.innerText:'');"
                + "c=(c||'').replace(/\\n{3,}/g,'\\n\\n').trim();"
                + "if(c.length>40){IceJsBridge.openReader(t,c);}else{IceJsBridge.showToast('无法提取正文');}"
                + "})()";
        try {
            tab.webView.evaluateJavascript(js, null);
        } catch (Exception e) {
            Log.e(TAG, "reader", e);
        }
    }

    private void toggleDesktop() {
        if (tabsManager == null) return;
        boolean next = !tabsManager.isDesktopMode();
        tabsManager.setDesktopMode(next);
        Toast.makeText(this, next ? "已切换到桌面版" : "已切换到移动版", Toast.LENGTH_SHORT).show();
    }

    private void cycleTheme() {
        String next = ThemeManager.cycle(this);
        Toast.makeText(this, "主题：" + ThemeManager.displayName(next), Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void openIncognito() {
        if (tabsManager == null) return;
        tabsManager.createTab(HOME_URL + "?mode=incognito", true);
        Toast.makeText(this, "已开启无痕标签", Toast.LENGTH_SHORT).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
            .setTitle("ice 浏览器")
            .setMessage("版本 " + Constants.VERSION_NAME + "\n\n"
                + "极简、便携、零第三方依赖的 Android 浏览器\n\n"
                + "• 真正的多 WebView 标签页管理\n"
                + "• 自研 ice 搜索引擎（异步 HTML 解析）\n"
                + "• 4 套主题（浅色 / 深色 / 纯黑 / 护眼）\n"
                + "• 广告与追踪器拦截（assets/adblock.txt）\n"
                + "• 阅读模式 · 页内查找 · 桌面版 · 无痕\n"
                + "• 边缘滑动返回 / 前进\n"
                + "• 系统 DownloadManager + 下载记录\n\n"
                + "技术：纯 Java（零第三方库）· Termux aapt/dx 单 dex 构建\n\n"
                + "© 2026 ice-wocker · MIT License")
            .setPositiveButton("确定", null)
            .show();
    }

    // ---------------- JS 桥 ----------------

    public class IceJsBridge {
        private final Activity activity;

        public IceJsBridge(Activity a) { this.activity = a; }

        private TabsManager.Tab current() {
            return tabsManager != null ? tabsManager.getCurrentTab() : null;
        }

        @android.webkit.JavascriptInterface
        public void loadUrl(String url) {
            runOnUiThread(new Runnable() {
                @Override public void run() { loadUrlOrSearch(url); }
            });
        }

        @android.webkit.JavascriptInterface
        public void newTab(final String url) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (tabsManager == null) return;
                    boolean incognito = url != null && url.contains("incognito");
                    String target;
                    if (url == null || url.isEmpty() || "about:blank".equals(url) || incognito) {
                        target = HOME_URL + (incognito ? "?mode=incognito" : "");
                    } else {
                        target = url;
                    }
                    tabsManager.createTab(target, incognito);
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void closeTab() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (tabsManager == null) return;
                    if (tabsManager.getTabCount() <= 1) activity.finish();
                    else tabsManager.closeTab(tabsManager.getCurrentIndex());
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void showTabs() {
            runOnUiThread(new Runnable() {
                @Override public void run() { openTabsList(); }
            });
        }

        @android.webkit.JavascriptInterface
        public String getCurrentUrl() {
            TabsManager.Tab t = current();
            return t != null && t.url != null ? t.url : "";
        }

        @android.webkit.JavascriptInterface
        public String getCurrentTitle() {
            TabsManager.Tab t = current();
            return t != null && t.title != null ? t.title : "";
        }

        @android.webkit.JavascriptInterface
        public int getTabCount() {
            return tabsManager != null ? tabsManager.getTabCount() : 0;
        }

        @android.webkit.JavascriptInterface
        public boolean isIncognito() {
            TabsManager.Tab t = current();
            return t != null && t.incognito;
        }

        @android.webkit.JavascriptInterface
        public String getCurrentTheme() {
            return ThemeManager.forWeb(activity);
        }

        @android.webkit.JavascriptInterface
        public void setTheme(String theme) {
            final String value;
            if ("contrast".equals(theme) || "amoled".equals(theme)) value = ThemeManager.AMOLED;
            else if ("sepia".equals(theme)) value = ThemeManager.SEPIA;
            else if ("dark".equals(theme)) value = ThemeManager.DARK;
            else value = ThemeManager.LIGHT;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (ThemeManager.set(activity, value)) recreate();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public String getSearchEngine() {
            return IcePrefs.getSearchEngine(activity);
        }

        @android.webkit.JavascriptInterface
        public void setSearchEngine(String name) {
            IcePrefs.setSearchEngine(activity, name);
        }

        @android.webkit.JavascriptInterface
        public boolean isBookmarked() {
            TabsManager.Tab t = current();
            if (t == null || t.url == null) return false;
            DatabaseHelper db = new DatabaseHelper(activity);
            try { return db.isBookmarked(t.url); } finally { db.close(); }
        }

        @android.webkit.JavascriptInterface
        public void removeBookmark() {
            TabsManager.Tab t = current();
            if (t == null || t.url == null) return;
            DatabaseHelper db = new DatabaseHelper(activity);
            try { db.removeBookmark(t.url); } finally { db.close(); }
        }

        @android.webkit.JavascriptInterface
        public void addBookmark() {
            runOnUiThread(new Runnable() {
                @Override public void run() { MainActivity.this.addBookmark(); }
            });
        }

        @android.webkit.JavascriptInterface
        public String getHistory() {
            DatabaseHelper db = new DatabaseHelper(activity);
            try {
                java.util.List<DatabaseHelper.HistoryItem> list = db.getHistory(8);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append("{\"title\":\"").append(UrlUtils.escapeJson(list.get(i).title))
                      .append("\",\"url\":\"").append(UrlUtils.escapeJson(list.get(i).url)).append("\"}");
                }
                return sb.append(']').toString();
            } finally {
                db.close();
            }
        }

        @android.webkit.JavascriptInterface
        public String getBookmarks() {
            DatabaseHelper db = new DatabaseHelper(activity);
            try {
                java.util.List<DatabaseHelper.Bookmark> list = db.getBookmarks();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append("{\"title\":\"").append(UrlUtils.escapeJson(list.get(i).title))
                      .append("\",\"url\":\"").append(UrlUtils.escapeJson(list.get(i).url)).append("\"}");
                }
                return sb.append(']').toString();
            } finally {
                db.close();
            }
        }

        @android.webkit.JavascriptInterface
        public void openReader(final String title, final String content) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try {
                        Intent i = new Intent(activity, ReaderActivity.class);
                        i.putExtra("title", title);
                        String body = content == null ? "" : content;
                        if (body.length() > 200000) body = body.substring(0, 200000);
                        i.putExtra("content", body);
                        activity.startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(activity, "无法打开阅读模式", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void readerMode() {
            runOnUiThread(new Runnable() {
                @Override public void run() { enterReaderMode(); }
            });
        }

        @android.webkit.JavascriptInterface
        public void toggleDesktop() {
            runOnUiThread(new Runnable() {
                @Override public void run() { MainActivity.this.toggleDesktop(); }
            });
        }

        @android.webkit.JavascriptInterface
        public void findInPage() {
            runOnUiThread(new Runnable() {
                @Override public void run() { showFindBar(); }
            });
        }

        @android.webkit.JavascriptInterface
        public void share() {
            runOnUiThread(new Runnable() {
                @Override public void run() { shareCurrent(); }
            });
        }

        @android.webkit.JavascriptInterface
        public void openHistory() { open(HistoryActivity.class); }

        @android.webkit.JavascriptInterface
        public void openBookmarks() { open(BookmarksActivity.class); }

        @android.webkit.JavascriptInterface
        public void openDownloads() { open(DownloadsActivity.class); }

        @android.webkit.JavascriptInterface
        public void openSettings() { open(SettingsActivity.class); }

        @android.webkit.JavascriptInterface
        public void openTabs() {
            runOnUiThread(new Runnable() {
                @Override public void run() { openTabsList(); }
            });
        }

        private void open(final Class<? extends Activity> cls) {
            runOnUiThread(new Runnable() {
                @Override public void run() { startActivitySafely(cls); }
            });
        }

        @android.webkit.JavascriptInterface
        public void showToast(final String msg) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void search(final String query, final String callbackId) {
            // 搜索是真异步的（后台线程 + 最长 8s）。必须记住「发起搜索的那个标签」：
            // 若在结果返回前用户切了标签，旧的写法会把结果注入到另一个网页上，
            // 而 iceOnSearchResults 只定义在主页 —— 那次搜索就永远转圈不出结果。
            final TabsManager.Tab origin = current();
            if (origin == null || origin.webView == null) return;
            final WebView originView = origin.webView;
            searchService.search(query, new IceSearchService.SearchCallback() {
                @Override
                public void onResults(final String json) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            // 发起搜索的标签可能已被关闭，此时丢弃结果
                            if (tabsManager == null || tabsManager.indexOf(origin) < 0) return;
                            String js = "if(window.iceOnSearchResults)window.iceOnSearchResults('"
                                    + MainActivity.escapeJsStatic(json) + "','"
                                    + MainActivity.escapeJsStatic(callbackId) + "');";
                            originView.evaluateJavascript(js, null);
                        }
                    });
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void getSuggestions(final String prefix, final String callbackId) {
            final TabsManager.Tab origin = current();
            if (origin == null || origin.webView == null) return;
            final WebView originView = origin.webView;
            searchService.getSuggestions(prefix, new IceSearchService.SuggestionCallback() {
                @Override
                public void onSuggestions(final java.util.List<String> suggestions) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (tabsManager == null || tabsManager.indexOf(origin) < 0) return;
                            StringBuilder json = new StringBuilder("[");
                            for (int i = 0; i < suggestions.size(); i++) {
                                if (i > 0) json.append(',');
                                json.append('"').append(MainActivity.escapeJsStatic(suggestions.get(i))).append('"');
                            }
                            json.append(']');
                            String js = "if(window.iceOnSuggestions)window.iceOnSuggestions('"
                                    + MainActivity.escapeJsStatic(json.toString()) + "','"
                                    + MainActivity.escapeJsStatic(callbackId) + "');";
                            originView.evaluateJavascript(js, null);
                        }
                    });
                }
            });
        }
    }

    public static String escapeJsStatic(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}