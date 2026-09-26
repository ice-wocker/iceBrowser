package com.icebrowser.app;

import android.content.Context;

/**
 * 内置错误页（离线可用的本地 HTML）。
 *
 * 旧版加载失败时是一片空白，用户不知道发生了什么。现在展示一个
 * 友好的错误页，并带「重试 / 返回主页」按钮，通过 JS 桥回调原生。
 */
final class IceErrorPage {

    private IceErrorPage() {}

    static String build(Context ctx, String failedUrl, String description, int errorCode) {
        boolean dark = ThemeManager.isDark(ctx);
        String bg = dark ? "#1F1F1F" : "#FFFFFF";
        String fg = dark ? "#E8EAED" : "#202124";
        String sub = dark ? "#9AA0A6" : "#5F6368";
        String accent = dark ? "#8AB4F8" : "#1A73E8";
        String card = dark ? "#2D2D2D" : "#F8F9FA";
        String divider = dark ? "#3C3C3C" : "#DADCE0";

        String title = friendlyTitle(errorCode);
        String hint = friendlyHint(errorCode);
        String safeUrl = UrlUtils.escapeJs(failedUrl == null ? "" : failedUrl);
        String safeDesc = escapeHtml(description == null ? "" : description);
        String shownUrl = escapeHtml(UrlUtils.prettyUrl(failedUrl));

        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>"
            + "*{box-sizing:border-box}"
            + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
            + "background:" + bg + ";color:" + fg + ";font-family:-apple-system,'Noto Sans CJK SC',sans-serif;"
            + "-webkit-user-select:none;user-select:none;padding:24px}"
            + ".wrap{width:100%;max-width:420px;text-align:center}"
            + ".badge{width:64px;height:64px;border-radius:50%;background:" + card + ";border:1px solid " + divider + ";"
            + "display:flex;align-items:center;justify-content:center;margin:0 auto 20px}"
            + ".badge svg{width:32px;height:32px;fill:" + accent + "}"
            + "h1{font-size:20px;margin:0 0 10px;font-weight:600}"
            + "p{margin:0 0 8px;font-size:14px;line-height:1.6;color:" + sub + "}"
            + ".url{display:block;margin:14px 0 22px;padding:10px 14px;background:" + card + ";"
            + "border:1px solid " + divider + ";border-radius:10px;font-size:12px;color:" + sub + ";"
            + "word-break:break-all;text-align:left}"
            + ".row{display:flex;gap:12px;justify-content:center;flex-wrap:wrap}"
            + "button{flex:1 1 140px;min-height:44px;border-radius:22px;border:1px solid " + divider + ";"
            + "background:transparent;color:" + fg + ";font-size:14px;font-family:inherit;cursor:pointer}"
            + "button.primary{background:" + accent + ";border-color:" + accent + ";"
            + "color:" + (dark ? "#1F1F1F" : "#FFFFFF") + ";font-weight:600}"
            + "button:active{opacity:.8}"
            + ".code{margin-top:20px;font-size:11px;color:" + sub + ";opacity:.7}"
            + "</style></head><body>"
            + "<div class=\"wrap\">"
            + "<div class=\"badge\"><svg viewBox=\"0 0 24 24\">"
            + "<path d=\"M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zm1,15h-2v-2h2v2zm0,-4h-2V7h2v6z\"/>"
            + "</svg></div>"
            + "<h1>" + escapeHtml(title) + "</h1>"
            + "<p>" + escapeHtml(hint) + "</p>"
            + "<span class=\"url\">" + shownUrl + "</span>"
            + "<div class=\"row\">"
            + "<button class=\"primary\" onclick=\"iceRetry()\">重试</button>"
            + "<button onclick=\"iceHome()\">回到主页</button>"
            + "</div>"
            + "<div class=\"code\">" + safeDesc + " (code " + errorCode + ")</div>"
            + "</div>"
            + "<script>"
            + "function iceRetry(){if(window.IceJsBridge&&IceJsBridge.loadUrl){IceJsBridge.loadUrl('" + safeUrl + "');}"
            + "else{location.href='" + safeUrl + "';}}"
            + "function iceHome(){if(window.IceJsBridge&&IceJsBridge.loadUrl){IceJsBridge.loadUrl('"
            + Constants.HOME_URL + "');}else{location.href='" + Constants.HOME_URL + "';}}"
            + "</" + "script></body></html>";
    }

    /** 是否为网络层错误码（用于区分「页面不存在」和「连不上网」）。 */
    static boolean isNetworkError(int code) {
        switch (code) {
            case android.webkit.WebViewClient.ERROR_HOST_LOOKUP:
            case android.webkit.WebViewClient.ERROR_CONNECT:
            case android.webkit.WebViewClient.ERROR_TIMEOUT:
            case android.webkit.WebViewClient.ERROR_IO:
            case android.webkit.WebViewClient.ERROR_UNKNOWN:
                return true;
            default:
                return false;
        }
    }

    private static String friendlyTitle(int code) {
        switch (code) {
            case android.webkit.WebViewClient.ERROR_HOST_LOOKUP: return "找不到服务器";
            case android.webkit.WebViewClient.ERROR_CONNECT:     return "无法连接到服务器";
            case android.webkit.WebViewClient.ERROR_TIMEOUT:     return "连接超时";
            case android.webkit.WebViewClient.ERROR_IO:          return "网络读取失败";
            case android.webkit.WebViewClient.ERROR_UNSUPPORTED_SCHEME: return "不支持的网址协议";
            case android.webkit.WebViewClient.ERROR_FILE_NOT_FOUND:      return "文件不存在";
            case android.webkit.WebViewClient.ERROR_FILE:        return "读取文件失败";
            case android.webkit.WebViewClient.ERROR_REDIRECT_LOOP: return "重定向次数过多";
            case android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE: return "安全连接失败";
            case android.webkit.WebViewClient.ERROR_BAD_URL:     return "网址格式不正确";
            default: return "网页打不开";
        }
    }

    private static String friendlyHint(int code) {
        switch (code) {
            case android.webkit.WebViewClient.ERROR_HOST_LOOKUP:
                return "请检查网址是否正确，或切换网络后重试。";
            case android.webkit.WebViewClient.ERROR_CONNECT:
            case android.webkit.WebViewClient.ERROR_IO:
                return "网络可能已断开，请检查 Wi-Fi 或移动数据。";
            case android.webkit.WebViewClient.ERROR_TIMEOUT:
                return "服务器响应太慢，请稍后重试。";
            case android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:
                return "该站点的证书存在问题，安全连接无法建立。";
            default:
                return "请稍后重试，或换一个网址。";
        }
    }

    static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}