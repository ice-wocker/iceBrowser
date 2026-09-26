package com.icebrowser.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

/**
 * 主题管理：4 套主题（浅色 / 深色 / 纯黑 / 护眼），外加「跟随系统」。
 *
 * 用法：每个 Activity 必须在 {@code setContentView()} 之前调用
 * {@link #applyTo(Activity)}，这样布局里的 ?attr/iceXxx 才能取到对应主题的颜色。
 * 切换主题时写入首选项并 recreate()，无需重启应用。
 */
public final class ThemeManager {

    private ThemeManager() {}

    public static final String AUTO = "auto";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";
    public static final String AMOLED = "amoled";
    public static final String SEPIA = "sepia";

    /** 设置界面可选值，与 arrays.xml 的 theme_values 顺序一致。 */
    public static final String[] ALL = {AUTO, LIGHT, DARK, AMOLED, SEPIA};

    // ---------------- 解析 ----------------

    /**
     * 把用户设置（可能含 auto）解析成具体主题值。
     * 返回值一定是 LIGHT / DARK / AMOLED / SEPIA 之一。
     */
    public static String resolve(Context c) {
        String t = IcePrefs.getTheme(c);
        if (LIGHT.equals(t) || DARK.equals(t) || AMOLED.equals(t) || SEPIA.equals(t)) {
            return t;
        }
        return isSystemNight(c) ? DARK : LIGHT;
    }

    /** 当前是否处于「深色系」（深色或纯黑）。 */
    public static boolean isDark(Context c) {
        String t = resolve(c);
        return DARK.equals(t) || AMOLED.equals(t);
    }

    /** 供主页 HTML / JS 使用的主题名：light | dark | sepia | contrast。 */
    public static String forWeb(Context c) {
        String t = resolve(c);
        if (SEPIA.equals(t)) return "sepia";
        if (DARK.equals(t)) return "dark";
        if (AMOLED.equals(t)) return "contrast";
        return "light";
    }

    /** 主题的中文显示名。 */
    public static String displayName(String theme) {
        if (LIGHT.equals(theme)) return "浅色";
        if (DARK.equals(theme)) return "深色";
        if (AMOLED.equals(theme)) return "纯黑";
        if (SEPIA.equals(theme)) return "护眼";
        return "跟随系统";
    }

    public static boolean isSystemNight(Context c) {
        int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    // ---------------- 应用 ----------------

    /** 必须在 setContentView 之前调用。 */
    public static void applyTo(Activity activity) {
        activity.setTheme(styleRes(activity));
    }

    /** 解析出主题对应的 style 资源。 */
    public static int styleRes(Context c) {
        String t = resolve(c);
        if (DARK.equals(t)) return R.style.Theme_IceBrowser_Dark;
        if (AMOLED.equals(t)) return R.style.Theme_IceBrowser_Amoled;
        if (SEPIA.equals(t)) return R.style.Theme_IceBrowser_Sepia;
        return R.style.Theme_IceBrowser;
    }

    /** 写入主题，返回是否真的发生了变化（用于决定要不要 recreate）。 */
    public static boolean set(Context c, String theme) {
        String old = IcePrefs.getTheme(c);
        if (theme == null) theme = AUTO;
        if (theme.equals(old)) return false;
        IcePrefs.setTheme(c, theme);
        return true;
    }

    /** 循环切到下一套主题（供工具栏一键切换）。返回新主题值。 */
    public static String cycle(Context c) {
        String cur = resolve(c);
        String next;
        if (LIGHT.equals(cur)) next = DARK;
        else if (DARK.equals(cur)) next = AMOLED;
        else if (AMOLED.equals(cur)) next = SEPIA;
        else next = LIGHT;
        IcePrefs.setTheme(c, next);
        return next;
    }
}