package com.icebrowser.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 统一的首选项读写入口。
 * 设置界面写入、WebView 与各 Activity 读取，全部走这里，避免键名散落各处。
 */
public final class IcePrefs {

    private IcePrefs() {}

    // ---------------- 键名 ----------------
    public static final String KEY_HOMEPAGE = "homepage";
    public static final String KEY_SEARCH_ENGINE = "search_engine";
    public static final String KEY_THEME = "theme";
    public static final String KEY_READER_FONT_SIZE = "reader_font_size";
    public static final String KEY_USER_AGENT = "user_agent";
    public static final String KEY_JS_ENABLED = "js_enabled";
    public static final String KEY_IMAGES_ENABLED = "images_enabled";
    public static final String KEY_COOKIES_ENABLED = "cookies_enabled";
    public static final String KEY_ADBLOCK_ENABLED = "adblock_enabled";
    public static final String KEY_DNT_ENABLED = "dnt_enabled";
    public static final String KEY_FORCE_DARK = "force_dark_web";
    public static final String KEY_SWIPE_NAV = "swipe_nav";
    public static final String KEY_OPEN_LINKS_NEW_TAB = "open_links_new_tab";
    public static final String KEY_RESTORE_TABS = "restore_tabs";
    public static final String KEY_DESKTOP_MODE = "desktop_mode";
    public static final String KEY_SAVE_FORM_DATA = "save_form_data";

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE);
    }

    // ---------------- 主题 ----------------
    public static String getTheme(Context c) {
        return get(c).getString(KEY_THEME, ThemeManager.AUTO);
    }

    public static void setTheme(Context c, String theme) {
        get(c).edit().putString(KEY_THEME, theme).apply();
    }

    // ---------------- 主页 ----------------
    public static String getHomepage(Context c) {
        String h = get(c).getString(KEY_HOMEPAGE, Constants.HOME_URL);
        return (h == null || h.trim().isEmpty()) ? Constants.HOME_URL : h.trim();
    }

    public static void setHomepage(Context c, String url) {
        get(c).edit().putString(KEY_HOMEPAGE, url).apply();
    }

    // ---------------- 搜索引擎 ----------------
    public static String getSearchEngine(Context c) {
        String e = get(c).getString(KEY_SEARCH_ENGINE, "Bing");
        for (String n : Constants.ENGINE_NAMES) {
            if (n.equals(e)) return e;
        }
        return "Bing";
    }

    public static void setSearchEngine(Context c, String engine) {
        get(c).edit().putString(KEY_SEARCH_ENGINE, engine).apply();
    }

    // ---------------- 通用开关 ----------------
    public static boolean getBool(Context c, String key, boolean def) {
        return get(c).getBoolean(key, def);
    }

    public static void setBool(Context c, String key, boolean value) {
        get(c).edit().putBoolean(key, value).apply();
    }

    public static int getInt(Context c, String key, int def) {
        return get(c).getInt(key, def);
    }

    public static void setInt(Context c, String key, int value) {
        get(c).edit().putInt(key, value).apply();
    }

    public static String getString(Context c, String key, String def) {
        return get(c).getString(key, def);
    }

    public static void setString(Context c, String key, String value) {
        get(c).edit().putString(key, value).apply();
    }
}