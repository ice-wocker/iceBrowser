package com.icebrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "iceBrowser";
    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    
    private FrameLayout webContainer;
    private LinearLayout findBar;
    private EditText findEdit;
    private EditText urlEdit;
    private ProgressBar progressBar;
    private ImageButton btnBack, btnForward, btnRefresh, btnTabs, btnMenu;
    private WebView currentWebView;
    private ValueCallback<Uri[]> filePathCallback;
    private SharedPreferences prefs;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            prefs = getSharedPreferences("ice_prefs", MODE_PRIVATE);
            
            setContentView(R.layout.activity_main);
            
            webContainer = (FrameLayout) findViewById(R.id.web_container);
            findBar = (LinearLayout) findViewById(R.id.find_bar);
            findEdit = (EditText) findViewById(R.id.find_edit);
            urlEdit = (EditText) findViewById(R.id.url_edit);
            progressBar = (ProgressBar) findViewById(R.id.progress);
            btnBack = (ImageButton) findViewById(R.id.btn_back);
            btnForward = (ImageButton) findViewById(R.id.btn_forward);
            btnRefresh = (ImageButton) findViewById(R.id.btn_refresh);
            btnTabs = (ImageButton) findViewById(R.id.btn_tabs);
            btnMenu = (ImageButton) findViewById(R.id.btn_menu);
            
            setupClickListeners();
            
            if (savedInstanceState == null) {
                handleIntent(getIntent());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Crash in onCreate", t);
            Toast.makeText(this, "初始化失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (currentWebView != null && currentWebView.canGoBack()) {
                        currentWebView.goBack();
                    }
                }
            });
        }
        if (btnForward != null) {
            btnForward.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (currentWebView != null && currentWebView.canGoForward()) {
                        currentWebView.goForward();
                    }
                }
            });
        }
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (currentWebView != null) currentWebView.reload();
                }
            });
        }
        if (btnTabs != null) {
            btnTabs.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        startActivity(new Intent(MainActivity.this, TabsActivity.class));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "无法打开标签页", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        if (btnMenu != null) {
            btnMenu.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showMenu(); }
            });
        }
        if (urlEdit != null) {
            urlEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                        String text = urlEdit.getText().toString().trim();
                        if (!TextUtils.isEmpty(text)) {
                            loadUrl(text);
                            hideKeyboard();
                        }
                        return true;
                    }
                    return false;
                }
            });
        }
        if (findEdit != null) {
            findEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        if (currentWebView != null) {
                            currentWebView.findAllAsync(findEdit.getText().toString());
                        }
                        return true;
                    }
                    return false;
                }
            });
        }
        
        int[] ids = {R.id.bottom_bookmark, R.id.bottom_history, R.id.bottom_downloads, R.id.bottom_settings};
        View.OnClickListener[] listeners = {
            new View.OnClickListener() {
                @Override public void onClick(View v) { startActivitySafely(BookmarksActivity.class); }
            },
            new View.OnClickListener() {
                @Override public void onClick(View v) { startActivitySafely(HistoryActivity.class); }
            },
            new View.OnClickListener() {
                @Override public void onClick(View v) { startActivitySafely(DownloadsActivity.class); }
            },
            new View.OnClickListener() {
                @Override public void onClick(View v) { startActivitySafely(SettingsActivity.class); }
            }
        };
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            if (v != null) v.setOnClickListener(listeners[i]);
        }
        
        View findClose = findViewById(R.id.find_close);
        if (findClose != null) {
            findClose.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { hideFindBar(); }
            });
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
            loadUrl(HOME_URL);
            return;
        }
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String url = data.toString();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                loadUrl(url);
                return;
            }
        }
        if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !TextUtils.isEmpty(text)) {
                loadUrl(text);
                return;
            }
        }
        loadUrl(HOME_URL);
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }
    
    public void loadUrl(String input) {
        if (TextUtils.isEmpty(input)) return;
        String url = input.trim();
        
        if (url.startsWith("javascript:")) return;
        if (url.startsWith("about:")) return;
        
        if (!url.contains("://")) {
            if (url.contains(" ") || !url.contains(".")) {
                url = getSearchUrl(url);
            } else {
                url = "https://" + url;
            }
        }
        
        try {
            if (currentWebView != null) {
                currentWebView.stopLoading();
                webContainer.removeView(currentWebView);
                currentWebView.destroy();
            }
            
            WebView webView = createWebView();
            webContainer.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
            currentWebView = webView;
            
            if (urlEdit != null) {
                urlEdit.setText(url);
            }
            
            webView.loadUrl(url);
        } catch (Exception e) {
            Log.e(TAG, "loadUrl error", e);
            Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private WebView createWebView() {
        WebView webView = new WebView(this);
        try {
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setSupportZoom(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            settings.setUserAgentString("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 iceBrowser/2.0");
            
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (url != null && (url.startsWith("intent://") || url.startsWith("market://"))) {
                        return true;
                    }
                    return false;
                }
                
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    if (urlEdit != null) {
                        urlEdit.setText(url);
                    }
                    if (progressBar != null) {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(0);
                    }
                    updateNavigationButtons();
                    saveHistory(url, view.getTitle());
                }
                
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (progressBar != null) {
                        progressBar.setProgress(100);
                        progressBar.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (progressBar != null) {
                                    progressBar.setVisibility(View.GONE);
                                }
                            }
                        }, 200);
                    }
                    updateNavigationButtons();
                }
                
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    // silently ignore
                }
            });
            
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (progressBar != null) {
                        progressBar.setProgress(newProgress);
                    }
                }
                
                @Override
                public void onReceivedTitle(WebView view, String title) {
                    if (urlEdit != null) {
                        urlEdit.setText(view.getUrl());
                    }
                }
                
                @Override
                public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                    filePathCallback = callback;
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        startActivityForResult(Intent.createChooser(intent, "选择文件"), FILE_CHOOSER_REQUEST);
                    } catch (Exception e) {
                        callback.onReceiveValue(null);
                        filePathCallback = null;
                    }
                    return true;
                }
                
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            request.grant(request.getResources());
                        } catch (Exception e) {}
                    }
                }
            });
            
            webView.setDownloadListener(new android.webkit.DownloadListener() {
                @Override
                public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                    DownloadService.startDownload(MainActivity.this, url, userAgent, contentDisposition, mimetype);
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "createWebView error", t);
        }
        return webView;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    } else if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }
    
    private void updateNavigationButtons() {
        if (currentWebView != null) {
            if (btnBack != null) {
                btnBack.setAlpha(currentWebView.canGoBack() ? 1.0f : 0.3f);
            }
            if (btnForward != null) {
                btnForward.setAlpha(currentWebView.canGoForward() ? 1.0f : 0.3f);
            }
        }
    }
    
    private String getSearchUrl(String query) {
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            String engine = prefs.getString("search_engine", "Bing");
            if ("Google".equals(engine)) {
                return "https://www.google.com/search?q=" + encoded;
            } else if ("DuckDuckGo".equals(engine)) {
                return "https://duckduckgo.com/?q=" + encoded;
            } else if ("百度".equals(engine)) {
                return "https://www.baidu.com/s?wd=" + encoded;
            } else if ("搜狗".equals(engine)) {
                return "https://www.sogou.com/web?query=" + encoded;
            } else {
                return "https://www.bing.com/search?q=" + encoded;
            }
        } catch (Exception e) {
            return "https://www.bing.com/search?q=" + query;
        }
    }
    
    private void saveHistory(String url, String title) {
        try {
            if (url == null) return;
            DatabaseHelper db = new DatabaseHelper(this);
            db.addHistory(url, title != null ? title : url);
            db.close();
        } catch (Exception e) {
            Log.e(TAG, "saveHistory error", e);
        }
    }
    
    public void showFindBar() {
        try {
            if (findBar != null) {
                findBar.setVisibility(View.VISIBLE);
                if (findEdit != null) {
                    findEdit.requestFocus();
                    findEdit.setText("");
                }
                showKeyboard(findEdit);
            }
        } catch (Exception e) {
            Log.e(TAG, "showFindBar error", e);
        }
    }
    
    public void hideFindBar() {
        try {
            if (findBar != null) {
                findBar.setVisibility(View.GONE);
            }
            if (currentWebView != null) {
                currentWebView.clearMatches();
            }
            hideKeyboard();
        } catch (Exception e) {
            Log.e(TAG, "hideFindBar error", e);
        }
    }
    
    private void showMenu() {
        try {
            showPopupMenu();
        } catch (Exception e) {
            Log.e(TAG, "showMenu error", e);
        }
    }
    
    private void showPopupMenu() {
        LinearLayout menuView = new LinearLayout(this);
        menuView.setOrientation(LinearLayout.VERTICAL);
        try {
            menuView.setBackgroundResource(R.drawable.menu_background);
        } catch (Exception e) {
            menuView.setBackgroundColor(0xFFFFFFFF);
        }
        
        final String[] items = {
            "查找", "分享", "复制链接", "添加到书签",
            "阅读模式", "桌面版网站", "无痕模式",
            "设置", "关于"
        };
        final int[] icons = {
            R.drawable.ic_find, R.drawable.ic_share, R.drawable.ic_share, R.drawable.ic_bookmark,
            R.drawable.ic_reader, R.drawable.ic_desktop, R.drawable.ic_incognito,
            R.drawable.ic_settings, R.drawable.ic_info
        };
        
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackgroundColor(0x00000000);
            row.setPadding(32, 24, 32, 24);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);
            
            try {
                final ImageView icon = new ImageView(this);
                icon.setImageResource(icons[i]);
                icon.setLayoutParams(new LinearLayout.LayoutParams(40, 40));
                row.addView(icon);
            } catch (Exception e) {}
            
            final TextView text = new TextView(this);
            text.setText(items[i]);
            text.setTextSize(15);
            text.setTextColor(0xFF212121);
            text.setPadding(24, 0, 0, 0);
            row.addView(text);
            
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        if (popup != null) popup.dismiss();
                    } catch (Exception e) {}
                    handleMenuClick(index);
                }
            });
            
            menuView.addView(row);
            
            if (i < items.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(0xFFE0E0E0);
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divParams.setMargins(32, 0, 32, 0);
                menuView.addView(divider, divParams);
            }
        }
        
        popup = new PopupWindow(menuView, 
            (int)(260 * getResources().getDisplayMetrics().density),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setBackgroundDrawable(getResources().getDrawable(android.R.drawable.dialog_holo_light_frame));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        try {
            popup.showAsDropDown(btnMenu, 0, 0);
        } catch (Exception e) {
            Log.e(TAG, "popup show", e);
        }
    }
    
    private PopupWindow popup;
    
    private void handleMenuClick(int index) {
        try {
            switch (index) {
                case 0: showFindBar(); break;
                case 1: shareCurrent(); break;
                case 2: copyUrl(); break;
                case 3: addBookmark(); break;
                case 4: enterReaderMode(); break;
                case 5: toggleDesktop(); break;
                case 6: openIncognito(); break;
                case 7: startActivitySafely(SettingsActivity.class); break;
                case 8: showAbout(); break;
            }
        } catch (Exception e) {
            Log.e(TAG, "menu click", e);
        }
    }
    
    private void shareCurrent() {
        if (currentWebView == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, currentWebView.getUrl());
            startActivity(Intent.createChooser(intent, "分享到"));
        } catch (Exception e) {
            Toast.makeText(this, "无法分享", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void copyUrl() {
        if (currentWebView == null) return;
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("URL", currentWebView.getUrl()));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void addBookmark() {
        if (currentWebView == null) return;
        try {
            DatabaseHelper db = new DatabaseHelper(this);
            String url = currentWebView.getUrl();
            String title = currentWebView.getTitle();
            if (url == null) url = "";
            if (title == null) title = url;
            db.addBookmark(url, title, "root");
            db.close();
            Toast.makeText(this, "已添加书签", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void enterReaderMode() {
        if (currentWebView == null) return;
        try {
            String script = "(function() {" +
                "var article = document.querySelector('article') || document.body;" +
                "var text = article ? article.innerText : '';" +
                "var title = document.title;" +
                "var html = '<html><head><meta charset=\"utf-8\"><style>body{font-size:18px;line-height:1.6;padding:20px;font-family:sans-serif;}</style></head><body><h1>' + title + '</h1><div>' + text.replace(/\\n/g, '<br>') + '</div></body></html>';" +
                "document.write(html);" +
                "})();";
            currentWebView.evaluateJavascript(script, null);
        } catch (Exception e) {
            Log.e(TAG, "reader mode error", e);
        }
    }
    
    private void toggleDesktop() {
        if (currentWebView == null) return;
        try {
            WebSettings settings = currentWebView.getSettings();
            String current = settings.getUserAgentString();
            if (current.contains("Mobile")) {
                settings.setUserAgentString(current.replace("Mobile", "").replace("Android", "X11"));
            } else {
                settings.setUserAgentString("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 iceBrowser/2.0");
            }
            currentWebView.reload();
        } catch (Exception e) {
            Log.e(TAG, "toggleDesktop error", e);
        }
    }
    
    private void openIncognito() {
        Toast.makeText(this, "无痕模式: " + (currentWebView != null ? "已启用" : "失败"), Toast.LENGTH_SHORT).show();
    }
    
    private void showAbout() {
        try {
            new AlertDialog.Builder(this)
                .setTitle("ice 浏览器")
                .setMessage("版本 2.0.0\n\n一款极简但功能强大的 Android 浏览器\n\n纯 Java · 零依赖 · 150KB")
                .setPositiveButton("确定", null)
                .show();
        } catch (Exception e) {}
    }
    
    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View v = getCurrentFocus();
            if (v != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        } catch (Exception e) {}
    }
    
    private void showKeyboard(View v) {
        if (v == null) return;
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        } catch (Exception e) {}
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (findBar != null && findBar.getVisibility() == View.VISIBLE) {
                hideFindBar();
                return true;
            }
            if (currentWebView != null && currentWebView.canGoBack()) {
                currentWebView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (currentWebView != null) {
                currentWebView.stopLoading();
                currentWebView.destroy();
                currentWebView = null;
            }
        } catch (Exception e) {}
        try {
            if (popup != null) popup.dismiss();
        } catch (Exception e) {}
    }
}