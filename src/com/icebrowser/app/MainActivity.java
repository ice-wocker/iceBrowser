package com.icebrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity implements TabsManager.TabsListener {
    private static final String TAG = "iceBrowser";
    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    
    private FrameLayout webContainer;
    private EditText urlEdit;
    private ProgressBar progressBar;
    private ImageButton btnBack, btnForward, btnRefresh, btnTabs, btnMenu;
    private android.widget.LinearLayout btnBookmarks, btnHistory, btnDownloads, btnSettings;
    private TabsManager tabsManager;
    public static TabsManager staticTabsManager;
    public static MainActivity instance;
    private IceSearchService searchService;
    private SharedPreferences prefs;
    private ValueCallback<Uri[]> filePathCallback;
    
    // === 真正的多 WebView tab 管理 ===
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            prefs = getSharedPreferences("ice_prefs", MODE_PRIVATE);
            searchService = new IceSearchService();
            
            setContentView(R.layout.activity_main);
            
            webContainer = (FrameLayout) findViewById(R.id.web_container);
            urlEdit = (EditText) findViewById(R.id.url_edit);
            progressBar = (ProgressBar) findViewById(R.id.progress);
            btnBack = (ImageButton) findViewById(R.id.btn_back);
            btnForward = (ImageButton) findViewById(R.id.btn_forward);
            btnRefresh = (ImageButton) findViewById(R.id.btn_refresh);
            btnTabs = (ImageButton) findViewById(R.id.btn_tabs);
            btnMenu = (ImageButton) findViewById(R.id.btn_menu);
            
            // 底栏 4 个按钮
            btnBookmarks = (android.widget.LinearLayout) findViewById(R.id.bottom_bookmarks);
            btnHistory = (android.widget.LinearLayout) findViewById(R.id.bottom_history);
            btnDownloads = (android.widget.LinearLayout) findViewById(R.id.bottom_downloads);
            btnSettings = (android.widget.LinearLayout) findViewById(R.id.bottom_settings);
            
            if (btnBookmarks != null) btnBookmarks.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startActivitySafely(BookmarksActivity.class); } });
            if (btnHistory != null) btnHistory.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startActivitySafely(HistoryActivity.class); } });
            if (btnDownloads != null) btnDownloads.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startActivitySafely(DownloadsActivity.class); } });
            if (btnSettings != null) btnSettings.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startActivitySafely(SettingsActivity.class); } });
            
            if (btnBack != null) btnBack.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { goBack(); } });
            if (btnForward != null) btnForward.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { goForward(); } });
            if (btnRefresh != null) btnRefresh.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { reload(); } });
            if (btnTabs != null) btnTabs.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openTabsList(); } });
            if (btnMenu != null) btnMenu.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showMenu(); } });

            // 初始化 TabsManager
            try {
                if (tabsManager == null) {
                    android.widget.FrameLayout container = (android.widget.FrameLayout) findViewById(R.id.web_container);
                    tabsManager = new TabsManager(this, container);
                    tabsManager.setListener(this);
                    tabsManager.setJsBridge(new IceJsBridge(this));
                    tabsManager.createTab("file:///android_asset/home.html", false);
                    tabsManager.injectBridgeToAll();
                    staticTabsManager = tabsManager;
                    instance = this;
                } else {
                    tabsManager.setListener(this);
                }
            } catch (Exception e) {
                android.util.Log.e("iceBrowser", "TabsManager init error", e);
            }

            if (urlEdit != null) {
                urlEdit.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
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

            if (savedInstanceState == null) {
                handleIntent(getIntent());
            } else {
                // 恢复后: 已经有 tabsManager, 切到当前 tab
                TabsManager.Tab cur = tabsManager.getCurrentTab();
                if (cur != null) {
                    onTabChanged(tabsManager.getCurrentIndex(), cur);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "onCreate", t);
            Toast.makeText(this, "启动失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private void startActivitySafely(Class<? extends Activity> cls) {
        try {
            startActivity(new Intent(this, cls));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleIntent(Intent intent) {
        if (intent == null) {
            createOrSwitchToHomeTab();
            return;
        }
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String url = data.toString();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                TabsManager.Tab tab = tabsManager.createTab(url, false);
                showCurrentTab();
                return;
            }
        }
        if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !TextUtils.isEmpty(text)) {
                loadUrlOrSearch(text);
                return;
            }
        }
        createOrSwitchToHomeTab();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }
    
    /**
     * 创建或切到 home tab. 
     * 如果当前有 home tab, 切过去, 否则新建
     */
    private void createOrSwitchToHomeTab() {
        // 找一个 URL 是 home 的 tab
        TabsManager.Tab current = tabsManager.getCurrentTab();
        if (current != null && (current.url == null || current.url.equals(HOME_URL) || current.url.equals("about:blank"))) {
            // 当前就是 home, 加载
            if (current.webView != null) current.webView.loadUrl(HOME_URL);
            showCurrentTab();
            return;
        }
        // 创建新 home tab
        TabsManager.Tab tab = tabsManager.createTab(HOME_URL, false);
        showCurrentTab();
    }
    
    /**
     * 智能判断 URL 还是搜索关键词
     */
    private void loadUrlOrSearch(String input) {
        if (TextUtils.isEmpty(input)) return;
        String url = input.trim();
        
        // 已是 http/https
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
            tabsManager.loadUrlInCurrent(url);
            showCurrentTab();
            return;
        }
        
        // 网址模式: 域名加点
        if (url.matches("^[\\w-]+(\\.[\\w-]+)+(/.*)?$") && !url.contains(" ")) {
            String fullUrl = url.startsWith("http") ? url : "https://" + url;
            tabsManager.loadUrlInCurrent(fullUrl);
            showCurrentTab();
            return;
        }
        
        // 搜索: 跳到 Bing
        try {
            String searchUrl = "https://www.bing.com/search?q=" + java.net.URLEncoder.encode(url, "UTF-8");
            tabsManager.loadUrlInCurrent(searchUrl);
            showCurrentTab();
        } catch (Exception e) {
            Toast.makeText(this, "搜索失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 显示当前 tab (其他 tab 隐藏)
     */
    private void showCurrentTab() {
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab == null) return;
        if (tab.webView != null) {
            tab.webView.setVisibility(View.VISIBLE);
        }
        if (webContainer != null && webContainer.indexOfChild(tab.webView) < 0) {
            webContainer.addView(tab.webView);
        }
        updateUI();
    }
    
    private void updateUI() {
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab == null) return;
        if (urlEdit != null) {
            urlEdit.setText(tab.url != null ? tab.url : "");
        }
        if (tab.webView != null) {
            if (btnBack != null) btnBack.setAlpha(tab.webView.canGoBack() ? 1.0f : 0.3f);
            if (btnForward != null) btnForward.setAlpha(tab.webView.canGoForward() ? 1.0f : 0.3f);
        }
    }
    
    private void goBack() {
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab != null && tab.webView != null && tab.webView.canGoBack()) {
            tab.webView.goBack();
        }
    }
    
    private void goForward() {
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab != null && tab.webView != null && tab.webView.canGoForward()) {
            tab.webView.goForward();
        }
    }
    
    private void reload() {
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab != null && tab.webView != null) tab.webView.reload();
    }
    
    private void openTabsList() {
        startActivitySafely(TabsActivity.class);
    }
    
    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View v = getCurrentFocus();
            if (v != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception e) {}
    }
    
    // === TabsManager.TabsListener ===
    @Override
    public void onTabsChanged() {
        updateUI();
    }
    
    @Override
    public void onTabChanged(int index, TabsManager.Tab tab) {
        updateUI();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 重新 attach 当前 tab
        TabsManager.Tab tab = tabsManager != null ? tabsManager.getCurrentTab() : null;
        if (tab != null && webContainer != null) {
            if (webContainer.indexOfChild(tab.webView) < 0) {
                webContainer.addView(tab.webView);
            }
            tab.webView.setVisibility(View.VISIBLE);
            updateUI();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            TabsManager.Tab tab = tabsManager.getCurrentTab();
            if (tab != null && tab.webView != null && tab.webView.canGoBack()) {
                tab.webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    
    // === IceJsBridge (webview 调用) ===
    public class IceJsBridge {
        MainActivity activity;
        public IceJsBridge(MainActivity a) { this.activity = a; }
        
        @android.webkit.JavascriptInterface
        public void loadUrl(String url) {
            runOnUiThread(new Runnable() {
                @Override public void run() { activity.loadUrlOrSearch(url); }
            });
        }
        
        @android.webkit.JavascriptInterface
        public void newTab(String url) {
            runOnUiThread(new Runnable() {
    @Override public void run() {
                TabsManager.Tab t = tabsManager.createTab(url != null && !url.isEmpty() ? url : HOME_URL, false);
                if (t.webView != null) t.webView.setVisibility(View.VISIBLE);
                activity.showCurrentTab();
                activity.updateUI();
            }
        });
    }
        
        @android.webkit.JavascriptInterface
        public void closeTab() {
            runOnUiThread(new Runnable() {
    @Override public void run() {
                if (tabsManager.getTabCount() <= 1) {
                    activity.finish();
                } else {
                    tabsManager.closeTab(tabsManager.getCurrentIndex());
                    activity.showCurrentTab();
                }
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
            TabsManager.Tab t = tabsManager.getCurrentTab();
            return t != null && t.webView != null ? t.webView.getUrl() : "";
        }
        
        @android.webkit.JavascriptInterface
        public String getCurrentTitle() {
            TabsManager.Tab t = tabsManager.getCurrentTab();
            return t != null ? t.title : "";
        }
        
        @android.webkit.JavascriptInterface
        public int getTabCount() {
            return tabsManager != null ? tabsManager.getTabCount() : 0;
        }
        
        @android.webkit.JavascriptInterface
        public void search(final String query, final String callbackId) {
            searchService.search(query, new IceSearchService.SearchCallback() {
                @Override
                public void onResults(final String json) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            TabsManager.Tab tab = tabsManager.getCurrentTab();
                            if (tab != null && tab.webView != null) {
                                String js = "if(window.iceOnSearchResults)window.iceOnSearchResults('" + MainActivity.escapeJsStatic(json) + "', '" + MainActivity.escapeJsStatic(callbackId) + "');";
                                tab.webView.evaluateJavascript(js, null);
                            }
                        }
                    });
                }
            });
        }
        
        @android.webkit.JavascriptInterface
        public void getSuggestions(final String prefix, final String callbackId) {
            searchService.getSuggestions(prefix, new IceSearchService.SuggestionCallback() {
                @Override
                public void onSuggestions(final java.util.List<String> suggestions) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            TabsManager.Tab tab = tabsManager.getCurrentTab();
                            if (tab != null && tab.webView != null) {
                                StringBuilder json = new StringBuilder("[");
                                for (int i = 0; i < suggestions.size(); i++) {
                                    if (i > 0) json.append(",");
                                    json.append("\"").append(MainActivity.escapeJsStatic(suggestions.get(i))).append("\"");
                                }
                                json.append("]");
                                String js = "if(window.iceOnSuggestions)window.iceOnSuggestions('" + MainActivity.escapeJsStatic(json.toString()) + "', '" + MainActivity.escapeJsStatic(callbackId) + "');";
                                tab.webView.evaluateJavascript(js, null);
                            }
                        }
                    });
                }
            });
        }
        
        @android.webkit.JavascriptInterface
        public void addBookmark() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    TabsManager.Tab tab = tabsManager.getCurrentTab();
                    if (tab != null && tab.webView != null) {
                        String url = tab.webView.getUrl();
                        String title = tab.webView.getTitle();
                        if (url == null) url = "";
                        if (title == null) title = url;
                        DatabaseHelper db = new DatabaseHelper(activity);
                        db.addBookmark(url, title, "root");
                        db.close();
                        Toast.makeText(activity, "已添加书签", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        @android.webkit.JavascriptInterface
        public void share() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    TabsManager.Tab tab = tabsManager.getCurrentTab();
                    if (tab != null && tab.webView != null) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, tab.webView.getUrl());
                        activity.startActivity(Intent.createChooser(intent, "分享到"));
                    }
                }
            });
        }
        
        @android.webkit.JavascriptInterface
        public void openHistory() { runOnUiThread(new Runnable() {
            @Override public void run() { startActivitySafely(HistoryActivity.class); }
        }); }
        @android.webkit.JavascriptInterface
        public void openBookmarks() { runOnUiThread(new Runnable() {
            @Override public void run() { startActivitySafely(BookmarksActivity.class); }
        }); }
        @android.webkit.JavascriptInterface
        public void openDownloads() { runOnUiThread(new Runnable() {
            @Override public void run() { startActivitySafely(DownloadsActivity.class); }
        }); }
        @android.webkit.JavascriptInterface
        public void openSettings() { runOnUiThread(new Runnable() {
            @Override public void run() { startActivitySafely(SettingsActivity.class); }
        }); }
        @android.webkit.JavascriptInterface
        public void openTabs() { runOnUiThread(new Runnable() {
            @Override public void run() { openTabsList(); }
        }); }
        
        @android.webkit.JavascriptInterface
        public void showToast(String msg) {
            runOnUiThread(new Runnable() {
                @Override public void run() { Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show(); }
            });
        }
        
        @android.webkit.JavascriptInterface
        public String getCurrentTheme() {
            int night = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return night == Configuration.UI_MODE_NIGHT_YES ? "dark" : "light";
        }
        
        private void startActivitySafely(Class<? extends Activity> cls) {
            try {
                activity.startActivity(new Intent(activity, cls));
            } catch (Exception e) {}
        }
        
        private String escapeJs(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\n", " ")
                  .replace("\r", " ");
        }
    }
    
    public static String escapeJsStatic(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
              .replace("'", "\\'")
              .replace("\n", " ")
              .replace("\r", " ");
    }
    public void showMenu() {
        final String[] items = {
            "新建标签", "页面查找", "分享", "复制链接", "添加到书签",
            "阅读模式", "桌面版", "无痕模式", "下载", "设置", "关于"
        };
        final int[] icons = {
            R.drawable.ic_add, R.drawable.ic_find, R.drawable.ic_share, R.drawable.ic_share, R.drawable.ic_bookmark,
            R.drawable.ic_reader, R.drawable.ic_desktop, R.drawable.ic_incognito, R.drawable.ic_download,
            R.drawable.ic_settings, R.drawable.ic_info
        };
        try {
            int density = (int) getResources().getDisplayMetrics().density;
            android.widget.LinearLayout menuView = new android.widget.LinearLayout(this);
            menuView.setOrientation(android.widget.LinearLayout.VERTICAL);
            menuView.setBackgroundResource(R.drawable.menu_background);
            
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                row.setBackgroundColor(0x00000000);
                row.setPadding(20 * density, 14 * density, 20 * density, 14 * density);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setClickable(true);
                row.setFocusable(true);
                
                android.widget.ImageView icon = new android.widget.ImageView(this);
                icon.setImageResource(icons[i]);
                android.widget.LinearLayout.LayoutParams ip = new android.widget.LinearLayout.LayoutParams(36 * density, 36 * density);
                row.addView(icon, ip);
                
                android.widget.TextView text = new android.widget.TextView(this);
                text.setText(items[i]);
                text.setTextSize(14);
                text.setTextColor(0xFF202124);
                text.setPadding(20 * density, 0, 0, 0);
                android.widget.LinearLayout.LayoutParams tp = new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                row.addView(text, tp);
                
                final android.widget.PopupWindow popup = new android.widget.PopupWindow(this);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        popup.dismiss();
                        handleMenuClick(idx);
                    }
                });
                
                menuView.addView(row);
            }
            
            android.widget.PopupWindow popup = new android.widget.PopupWindow(this);
            popup.setContentView(menuView);
            popup.setWidth(220 * density);
            popup.setHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            popup.setBackgroundDrawable(getResources().getDrawable(R.drawable.menu_background));
            popup.setOutsideTouchable(true);
            popup.setFocusable(true);
            if (btnMenu != null) popup.showAsDropDown(btnMenu, 0, 0);
        } catch (Exception e) {
            Log.e(TAG, "showMenu", e);
        }
    }
    
    private void handleMenuClick(int index) {
        switch (index) {
            case 0: // 新建标签
                TabsManager.Tab t = tabsManager.createTab(HOME_URL, false);
                showCurrentTab();
                break;
            case 1: showFindBar(); break;
            case 2: shareCurrent(); break;
            case 3: copyUrl(); break;
            case 4: addBookmark(); break;
            case 5: enterReaderMode(); break;
            case 6: toggleDesktop(); break;
            case 7: openIncognito(); break;
            case 8: startActivitySafely(DownloadsActivity.class); break;
            case 9: startActivitySafely(SettingsActivity.class); break;
            case 10: showAbout(); break;
        }
    }
    
    private void showFindBar() {
        if (tabsManager == null) return;
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab == null || tab.webView == null) return;
        // 通过 JS 注入顶部 find 栏
        try {
            String js = "(function(){" +
                "var old = document.getElementById('__ice_find_bar');" +
                "if (old) { old.remove(); return; }" +
                "var bar = document.createElement('div');" +
                "bar.id = '__ice_find_bar';" +
                "bar.style.cssText = 'position:fixed;top:0;left:0;right:0;height:48px;background:rgba(0,0,0,0.9);color:white;z-index:99999;display:flex;align-items:center;padding:0 8px;gap:8px';" +
                "bar.innerHTML = '<input id=\"__ice_find_input\" style=\"flex:1;height:36px;padding:0 12px;border-radius:18px;border:none;background:rgba(255,255,255,0.2);color:white;font-size:14px;outline:none\" placeholder=\"查找\"/>" +
                "<span id=\"__ice_find_count\" style=\"color:rgba(255,255,255,0.7);font-size:13px;min-width:48px;text-align:center\">0/0</span>" +
                "<button onclick=\"window.__iceFindUp()\" style=\"background:transparent;color:white;border:none;padding:8px;font-size:18px\">↑</button>" +
                "<button onclick=\"window.__iceFindDown()\" style=\"background:transparent;color:white;border:none;padding:8px;font-size:18px\">↓</button>" +
                "<button onclick=\"document.getElementById(\\'__ice_find_bar\\').remove();window.__iceFindClear()\" style=\"background:transparent;color:white;border:none;padding:8px;font-size:18px\">×</button>';" +
                "document.body.appendChild(bar);" +
                "var input = document.getElementById('__ice_find_input');" +
                "var countEl = document.getElementById('__ice_find_count');" +
                "window.__iceFindMatches = [];" +
                "window.__iceFindIdx = 0;" +
                "function doFind() {" +
                "  window.__iceFindClear();" +
                "  var q = input.value;" +
                "  if (!q) { countEl.textContent = '0/0'; return; }" +
                "  if (window.find) {" +
                "    var n = 0;" +
                "    while (window.find(q)) { n++; }" +
                "    window.__iceFindMatches = [n];" +
                "  }" +
                "  countEl.textContent = n + ' 处';" +
                "}" +
                "input.oninput = doFind;" +
                "input.onkeydown = function(e) {" +
                "  if (e.keyCode == 13) { if (window.find(input.value)) {} }" +
                "  if (e.keyCode == 27) { bar.remove(); window.__iceFindClear(); }" +
                "};" +
                "window.__iceFindUp = function() { if (window.find && input.value) { window.find(input.value, false, true); } };" +
                "window.__iceFindDown = function() { if (window.find && input.value) { window.find(input.value, false, false); } };" +
                "window.__iceFindClear = function() { if (window.find && input.value) { window.find(input.value, true, false); } };" +
                "input.focus();" +
                "})()";
            tab.webView.evaluateJavascript(js, null);
        } catch (Exception e) {
            Log.e(TAG, "find", e);
        }
    }
    
    private void shareCurrent() {
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab == null || tab.webView == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, tab.webView.getUrl());
            startActivity(Intent.createChooser(intent, "分享"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void copyUrl() {
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab == null || tab.webView == null) return;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", tab.webView.getUrl()));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void addBookmark() {
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab == null || tab.webView == null) return;
        try {
            String url = tab.webView.getUrl();
            String title = tab.webView.getTitle();
            if (url == null) url = "";
            if (title == null) title = url;
            DatabaseHelper db = new DatabaseHelper(this);
            db.addBookmark(url, title, "root");
            db.close();
            Toast.makeText(this, "已添加书签", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void enterReaderMode() {
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab == null || tab.webView == null) return;
        try {
            String script = "(function(){" +
                "var a=document.querySelector('article')||document.querySelector('main')||document.body;" +
                "var t=document.title;" +
                "var text=a?a.innerText:document.body.innerText;" +
                "var html='<html><head><meta charset=\"utf-8\"><style>body{font-size:18px;line-height:1.7;padding:30px;max-width:720px;margin:0 auto;font-family:sans-serif;color:#222}h1{font-size:26px;margin-bottom:20px;color:#1A73E8}</style></head><body><h1>'+t+'</h1><div>'+text.replace(/\\n/g,'<br>')+'</div></body></html>';" +
                "document.write(html);})()";
            tab.webView.evaluateJavascript(script, null);
        } catch (Exception e) {
            Log.e(TAG, "reader", e);
        }
    }
    
    private void toggleDesktop() {
        TabsManager.Tab tab = tabsManager.getCurrentTab();
        if (tab == null || tab.webView == null) return;
        try {
            WebSettings s = tab.webView.getSettings();
            String cur = s.getUserAgentString();
            if (cur.contains("Mobile")) {
                s.setUserAgentString(cur.replace("Mobile", "").replace("Android", "X11"));
            } else {
                s.setUserAgentString("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 iceBrowser/4.0");
            }
            tab.webView.reload();
        } catch (Exception e) {
            Log.e(TAG, "desktop", e);
        }
    }
    
    private void openIncognito() {
        TabsManager.Tab tab = tabsManager.createTab(HOME_URL + "?mode=incognito", true);
        showCurrentTab();
        Toast.makeText(this, "无痕模式", Toast.LENGTH_SHORT).show();
    }
    
    private void showAbout() {
        new AlertDialog.Builder(this)
            .setTitle("ice 浏览器")
            .setMessage("版本 4.0.0\n\n" +
                "极全面升级 + 真正自研 ice 搜索引擎\n\n" +
                "• 真正多 WebView 标签页管理\n" +
                "• 4 个底栏按钮直达 Activity\n" +
                "• ice 自研搜索引擎 (DDG 端点)\n" +
                "• 异步实时搜索 (UI 立即返回)\n" +
                "• 智能建议 (24 类 144 条)\n" +
                "• 4 主题 (浅/深/护眼/黑白)\n" +
                "• 阅读模式 / 桌面版 / 翻译\n" +
                "• 无痕模式 (不记录历史)\n" +
                "• 系统 DownloadManager\n" +
                "• 完整书签/历史/下载管理\n\n" +
                "技术: 纯 Java (零依赖) · APK 95KB\n\n" +
                "© 2026 ice-wocker · MIT License")
            .setPositiveButton("确定", null)
            .show();
    }
}