package com.icebrowser.app;

import android.text.TextUtils;

import java.net.URL;
import java.util.regex.Pattern;

/**
 * URL 相关的纯工具方法：判断、规范化、域名提取、JS 转义。
 */
public final class UrlUtils {

    private UrlUtils() {}

    private static final Pattern DOMAIN_LIKE =
        Pattern.compile("^[\\p{L}\\p{N}_-]+(\\.[\\p{L}\\p{N}_-]+)+(/\\S*)?$");

    private static final Pattern HAS_SCHEME =
        Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    public static boolean isHttp(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    public static boolean isHttps(String url) {
        return url != null && url.startsWith("https://");
    }

    /** 是否是我们内部处理、不该跳转外部的特殊协议。 */
    public static boolean isInternalScheme(String url) {
        if (url == null) return false;
        return url.startsWith("file:///android_asset/")
            || url.startsWith("file:///android_res/")
            || url.startsWith("about:")
            || url.startsWith("data:")
            || url.startsWith("javascript:")
            || url.startsWith("blob:");
    }

    /** 是否是应当交给外部 App 的协议。 */
    public static boolean isExternalScheme(String url) {
        if (url == null) return false;
        String[] schemes = {"tel:", "mailto:", "sms:", "smsto:", "geo:", "market:",
            "intent:", "whatsapp:", "weixin:", "alipay:", "tbopen:", "baiduboxapp:",
            "mqqwpa:", "viber:", "skype:", "rtsp:", "magnet:"};
        for (String s : schemes) {
            if (url.startsWith(s)) return true;
        }
        return false;
    }

    /**
     * 判断输入像不像一个网址（而不是搜索关键词）。
     * 支持：显式 scheme、带点的域名、localhost、IP、含端口。
     */
    public static boolean looksLikeUrl(String input) {
        if (TextUtils.isEmpty(input)) return false;
        String s = input.trim();
        if (s.contains(" ")) return false;
        if (HAS_SCHEME.matcher(s).find()) return true;
        if (DOMAIN_LIKE.matcher(s).matches()) return true;
        return false;
    }

    /**
     * 把用户输入规范化成可加载的 URL；若判定为搜索词则返回 null。
     */
    public static String normalize(String input) {
        if (TextUtils.isEmpty(input)) return null;
        String s = input.trim();
        if (HAS_SCHEME.matcher(s).find()) return s;
        if (looksLikeUrl(s)) return "https://" + s;
        return null;
    }

    /** 提取主机名，失败返回空串。 */
    public static String hostOf(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            String host = new URL(url).getHost();
            return host == null ? "" : host;
        } catch (Exception e) {
            return "";
        }
    }

    /** 去掉 www. 前缀的域名，用于展示与去重。 */
    public static String domainOf(String url) {
        String host = hostOf(url);
        if (host.startsWith("www.")) host = host.substring(4);
        return host;
    }

    /** 地址栏展示用的精简形式：去掉 scheme 与结尾斜杠。 */
    public static String prettyUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String s = url;
        if (s.startsWith("https://")) s = s.substring(8);
        else if (s.startsWith("http://")) s = s.substring(7);
        if (s.endsWith("/") && s.length() > 1) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** 转义为可安全嵌入单引号 JS 字符串的内容。 */
    public static String escapeJs(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    /** 转义为可安全嵌入 JSON 字符串的内容。 */
    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    /** 根据扩展名猜测 MIME 类型（无网络请求）。 */
    public static String guessMime(String url) {
        if (url == null) return "application/octet-stream";
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q > 0) u = u.substring(0, q);
        if (u.endsWith(".html") || u.endsWith(".htm")) return "text/html";
        if (u.endsWith(".txt")) return "text/plain";
        if (u.endsWith(".css")) return "text/css";
        if (u.endsWith(".js")) return "application/javascript";
        if (u.endsWith(".json")) return "application/json";
        if (u.endsWith(".xml")) return "application/xml";
        if (u.endsWith(".pdf")) return "application/pdf";
        if (u.endsWith(".png")) return "image/png";
        if (u.endsWith(".jpg") || u.endsWith(".jpeg")) return "image/jpeg";
        if (u.endsWith(".gif")) return "image/gif";
        if (u.endsWith(".webp")) return "image/webp";
        if (u.endsWith(".svg")) return "image/svg+xml";
        if (u.endsWith(".ico")) return "image/x-icon";
        if (u.endsWith(".mp3")) return "audio/mpeg";
        if (u.endsWith(".wav")) return "audio/wav";
        if (u.endsWith(".mp4")) return "video/mp4";
        if (u.endsWith(".webm")) return "video/webm";
        if (u.endsWith(".zip")) return "application/zip";
        if (u.endsWith(".gz") || u.endsWith(".tgz")) return "application/gzip";
        if (u.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (u.endsWith(".doc") || u.endsWith(".docx")) return "application/msword";
        if (u.endsWith(".xls") || u.endsWith(".xlsx")) return "application/vnd.ms-excel";
        if (u.endsWith(".ppt") || u.endsWith(".pptx")) return "application/vnd.ms-powerpoint";
        return "application/octet-stream";
    }

    /** 是否为 webkit 无法直接渲染、需要交给外部播放器/应用的资源。 */
    public static boolean isDownloadable(String url, String mime) {
        if (url == null) return false;
        String u = url.toLowerCase();
        String[] exts = {".zip", ".rar", ".7z", ".tar", ".gz", ".apk", ".exe", ".dmg",
            ".iso", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".mp3", ".wav", ".flac", ".ape", ".mp4", ".mkv", ".avi", ".mov", ".flv",
            ".torrent", ".dwg"};
        for (String e : exts) {
            if (u.endsWith(e)) return true;
        }
        if (mime != null) {
            if (mime.startsWith("application/") &&
                !mime.contains("json") && !mime.contains("javascript") && !mime.contains("xml")) {
                return true;
            }
            if (mime.startsWith("audio/") || mime.startsWith("video/")) return true;
        }
        return false;
    }
}