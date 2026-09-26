package com.icebrowser.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.util.Log;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * WebView 客户端（v2）。
 *
 * 职责：URL 分发、广告拦截、页面状态同步、离线错误页、历史记录上报。
 * 所有 UI 相关动作都通过 {@link TabsManager} 的监听器抛出，客户端本身不持有 Activity。
 */
public class IceWebViewClient extends WebViewClient {

    private static final String TAG = "IceWebViewClient";

    private final TabsManager tabsManager;
    private final AdBlocker adBlocker;

    public IceWebViewClient(TabsManager tabsManager, AdBlocker adBlocker) {
        this.tabsManager = tabsManager;
        this.adBlocker = adBlocker;
    }

    // ---------------- URL 分发 ----------------

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return false;
        return handleUrl(view, request.getUrl().toString());
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return handleUrl(view, url);
    }

    private boolean handleUrl(WebView view, String url) {
        if (url == null) return false;
        // 内部协议与本地页面交给 WebView 自己处理
        if (UrlUtils.isInternalScheme(url)) return false;
        if (url.startsWith("file:///android_asset/") || url.startsWith("file:///android_res/")) return false;

        if (url.startsWith("intent://")) {
            return handleIntentUrl(view, url);
        }
        // 不跳应用商店，留在浏览器里
        if (url.startsWith("market://") || url.startsWith("play.google.com")) {
            return true;
        }
        if (url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("smsto:")
                || url.startsWith("mailto:") || url.startsWith("geo:")) {
            openExternally(view, url);
            return true;
        }
        if (UrlUtils.isHttp(url) || url.startsWith("file:") || url.startsWith("content:")) {
            return false;
        }
        // 其它未知协议：尝试交给系统
        openExternally(view, url);
        return true;
    }

    private void openExternally(WebView view, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            view.getContext().startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "无法外部打开: " + url);
        }
    }

    /** 解析 {@code intent://...#Intent;scheme=...;package=...;S.browser_fallback_url=...;end}。 */
    private boolean handleIntentUrl(WebView view, String url) {
        try {
            int intentIdx = url.indexOf("#Intent");
            if (intentIdx > 0) {
                String data = url.substring("intent://".length(), intentIdx);
                String intentPart = url.substring(intentIdx + "#Intent".length());
                Intent intent = new Intent();
                intent.setData(Uri.parse(data));
                String pkg = null;
                for (String p : intentPart.split(";")) {
                    if (p.startsWith("action=")) intent.setAction(p.substring(7));
                    else if (p.startsWith("package=")) pkg = p.substring(8);
                    else if (p.startsWith("scheme=")) intent.setData(Uri.parse(p.substring(7) + ":" + data));
                    else if (p.startsWith("S.browser_fallback_url=")) {
                        String fallback = Uri.decode(p.substring("S.browser_fallback_url=".length()));
                        if (fallback.startsWith("http")) view.loadUrl(fallback);
                        return true;
                    }
                }
                if (pkg != null) intent.setPackage(pkg);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    view.getContext().startActivity(intent);
                } catch (Exception e) {
                    // 目标 App 未安装：退回普通网址
                    if (data.startsWith("http")) view.loadUrl(data);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "handleIntentUrl", e);
        }
        return true;
    }

    // ---------------- 广告拦截 ----------------

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (adBlocker != null && adBlocker.shouldBlock(request)) {
            return adBlocker.createEmptyResponse();
        }
        return super.shouldInterceptRequest(view, request);
    }

    // ---------------- 页面状态 ----------------

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        TabsManager.Tab tab = tabsManager != null ? tabsManager.findByWebView(view) : null;
        if (tab != null) {
            tab.url = url;
            tab.loading = true;
            tab.errorPage = false;
        }
        if (tabsManager != null) tabsManager.notifyTabChanged();
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        TabsManager.Tab tab = tabsManager != null ? tabsManager.findByWebView(view) : null;
        if (tab == null) return;
        // 错误页只是本地兜底 HTML，不能覆盖用户真实地址，也不能写进历史
        if (!tab.errorPage) {
            tab.url = url;
            tab.title = view.getTitle();
            tabsManager.notifyPageFinished(tab);
        }
        tab.loading = false;
        tabsManager.notifyTabChanged();
        // 首页需要知道当前是否已收藏
        if (url != null && url.startsWith("file:///android_asset/home.html")) {
            view.evaluateJavascript(
                    "if(window.updateBookmarkState)window.updateBookmarkState();", null);
        }
    }

    // ---------------- 错误处理 ----------------

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (Build.VERSION.SDK_INT < 23) return;
        if (request == null || !request.isForMainFrame()) return;
        String description = error != null && error.getDescription() != null
                ? error.getDescription().toString() : "";
        int code = error != null ? error.getErrorCode() : WebViewClient.ERROR_UNKNOWN;
        showErrorPage(view, request.getUrl().toString(), description, code);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        // API 23+ 走上面的新回调，这里只兜底老系统
        if (Build.VERSION.SDK_INT >= 23) return;
        showErrorPage(view, failingUrl, description, errorCode);
    }

    private void showErrorPage(WebView view, String failingUrl, String description, int errorCode) {
        TabsManager.Tab tab = tabsManager != null ? tabsManager.findByWebView(view) : null;
        if (tab != null) {
            tab.errorPage = true;
            tab.loading = false;
        }
        try {
            Context ctx = view.getContext();
            String html = IceErrorPage.build(ctx, failingUrl, description, errorCode);
            view.loadDataWithBaseURL(failingUrl, html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            Log.w(TAG, "showErrorPage", e);
        }
        if (tabsManager != null) tabsManager.notifyTabChanged();
    }

    @Override
    public void onReceivedSslError(final WebView view, final SslErrorHandler handler, SslError error) {
        // 证书错误不能静默放行，交给用户决定
        String host = error != null ? error.getUrl() : "";
        try {
            new AlertDialog.Builder(view.getContext())
                    .setTitle("安全连接有问题")
                    .setMessage("该站点的证书无法验证：\n" + host + "\n\n继续访问可能泄露你的数据。")
                    .setPositiveButton("继续访问", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { handler.proceed(); }
                    })
                    .setNegativeButton("返回", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { handler.cancel(); }
                    })
                    .show();
        } catch (Exception e) {
            handler.cancel();
        }
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                          String host, String realm) {
        // 不猜测凭据，交给用户取消，避免钓鱼框
        handler.cancel();
    }
}