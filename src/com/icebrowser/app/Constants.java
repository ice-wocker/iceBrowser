package com.icebrowser.app;

/**
 * 全局常量。
 * 版本号、主页面地址、请求码、搜索引擎定义等集中在这里，避免多处硬编码打架。
 */
public final class Constants {

    private Constants() {}

    /** 应用版本：与 AndroidManifest 的 versionName 保持一致。 */
    public static final String VERSION_NAME = "5.0.0";
    public static final int VERSION_CODE = 5;

    public static final String TAG = "iceBrowser";

    /** 内置主页（assets 内，离线可用）。 */
    public static final String HOME_URL = "file:///android_asset/home.html";
    public static final String ABOUT_BLANK = "about:blank";

    /** SharedPreferences 文件名。 */
    public static final String PREFS = "ice_prefs";

    // ---------------- 请求码 ----------------
    public static final int REQ_FILE_CHOOSER = 1001;
    public static final int REQ_PERMISSIONS = 1002;
    public static final int REQ_PICK_BOOKMARK = 1003;
    public static final int REQ_PICK_HISTORY = 1004;

    // ---------------- 搜索引擎 ----------------
    /** 自研 ice 引擎标识。 */
    public static final String ENGINE_ICE = "ice";

    public static final String[] ENGINE_NAMES = {
        "ice", "Bing", "Google", "DuckDuckGo", "百度", "搜狗"
    };

    /**
     * 返回搜索引擎的查询 URL 模板（{@code %s} 为已编码关键词）。
     * ice 引擎返回 null —— 它走 IceSearchService 的异步桥接，而不是直接跳转。
     */
    public static String searchUrlTemplate(String engine) {
        if (engine == null) return null;
        if ("Bing".equals(engine)) return "https://www.bing.com/search?q=%s";
        if ("Google".equals(engine)) return "https://www.google.com/search?q=%s";
        if ("DuckDuckGo".equals(engine)) return "https://duckduckgo.com/?q=%s";
        if ("百度".equals(engine)) return "https://www.baidu.com/s?wd=%s";
        if ("搜狗".equals(engine)) return "https://www.sogou.com/web?query=%s";
        return null; // ice
    }

    /** 拼接搜索 URL；engine 为 ice 或不认识时返回 null。 */
    public static String buildSearchUrl(String engine, String query) {
        String tpl = searchUrlTemplate(engine);
        if (tpl == null) return null;
        try {
            return String.format(tpl, java.net.URLEncoder.encode(query, "UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 移动端 UA。 */
    public static final String UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
        + "Chrome/120.0.0.0 Mobile Safari/537.36 iceBrowser/" + VERSION_NAME;

    /** 桌面端 UA。 */
    public static final String UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
        + "Chrome/120.0.0.0 Safari/537.36";
}