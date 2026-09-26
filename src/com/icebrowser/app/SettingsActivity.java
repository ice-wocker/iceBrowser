package com.icebrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 设置界面（v2，完全重写）。
 *
 * 旧版只有 7 个入口、主题只有 3 套、且直接读写裸 SharedPreferences，和
 * IcePrefs / ThemeManager 的键名与取值范围都对不上。
 *
 * 现在所有读写统一走 IcePrefs，主题 / 引擎选项与 ThemeManager、Constants 保持同一份定义，
 * 覆盖：外观、搜索、隐私拦截、网页行为、数据清理，共 17 项。
 */
public class SettingsActivity extends Activity {

    // 每一行的唯一标识（不依赖位置，避免增删条目后错位）
    private static final int ID_HOMEPAGE = 1;
    private static final int ID_ENGINE = 2;
    private static final int ID_THEME = 3;
    private static final int ID_FONT = 4;
    private static final int ID_UA = 5;
    private static final int ID_ADBLOCK = 6;
    private static final int ID_JS = 7;
    private static final int ID_IMAGES = 8;
    private static final int ID_COOKIES = 9;
    private static final int ID_FORCE_DARK = 10;
    private static final int ID_SWIPE = 11;
    private static final int ID_RESTORE = 12;
    private static final int ID_FORM_DATA = 13;
    private static final int ID_RULES = 14;
    private static final int ID_DL_DIR = 15;
    private static final int ID_CLEAR = 16;
    private static final int ID_ABOUT = 17;

    private static final int KIND_ACTION = 0;
    private static final int KIND_TOGGLE = 1;

    private static final String[] THEME_ENTRIES =
            {"跟随系统", "浅色", "深色", "纯黑", "护眼"};
    private static final String[] ENGINE_ENTRIES =
            {"ice（内置）", "Bing", "Google", "DuckDuckGo", "百度", "搜狗"};
    private static final String[] ENGINE_VALUES = Constants.ENGINE_NAMES;
    private static final String[] UA_ENTRIES = {"默认", "移动版", "桌面版"};
    private static final String[] UA_VALUES = {"default", "mobile", "desktop"};

    private final List<Row> rows = new ArrayList<>();
    private SettingAdapter adapter;
    private ListView listView;

    /** 一行设置项。 */
    private static class Row {
        int id;
        int kind;
        String title;
        String sub;
        String value;      // 右侧当前值
        String prefKey;    // 开关项用的首选项键
        boolean def;       // 开关项默认值

        Row(int id, int kind, String title) {
            this.id = id;
            this.kind = kind;
            this.title = title;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ThemeManager.applyTo(this);
            setContentView(R.layout.activity_list);

            TextView title = (TextView) findViewById(R.id.title);
            if (title != null) title.setText(R.string.settings);

            ImageButton back = (ImageButton) findViewById(R.id.btn_back);
            if (back != null) back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });

            listView = (ListView) findViewById(R.id.list);
            if (listView == null) {
                finish();
                return;
            }

            buildRows();
            adapter = new SettingAdapter();
            listView.setAdapter(adapter);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (position < 0 || position >= rows.size()) return;
                    onRowClick(rows.get(position));
                }
            });
        } catch (Throwable t) {
            android.util.Log.e("Settings", "onCreate", t);
            Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /** 组装全部设置项（开关项在这里读取当前值）。 */
    private void buildRows() {
        rows.clear();

        Row homepage = new Row(ID_HOMEPAGE, KIND_ACTION, "主页");
        homepage.sub = "启动时打开的页面";
        homepage.value = shorten(IcePrefs.getHomepage(this));
        rows.add(homepage);

        Row engine = new Row(ID_ENGINE, KIND_ACTION, "搜索引擎");
        engine.sub = "地址栏输入关键词时使用";
        engine.value = displayEngine(IcePrefs.getSearchEngine(this));
        rows.add(engine);

        Row theme = new Row(ID_THEME, KIND_ACTION, "主题");
        theme.sub = "浅色 / 深色 / 纯黑 / 护眼";
        theme.value = ThemeManager.displayName(ThemeManager.resolve(this));
        rows.add(theme);

        Row font = new Row(ID_FONT, KIND_ACTION, "阅读模式字号");
        font.sub = "只影响阅读模式正文";
        font.value = IcePrefs.getInt(this, IcePrefs.KEY_READER_FONT_SIZE, 18) + " px";
        rows.add(font);

        Row ua = new Row(ID_UA, KIND_ACTION, "用户代理 (UA)");
        ua.sub = "部分网站需要桌面版 UA 才能正常显示";
        ua.value = displayUa(IcePrefs.getString(this, IcePrefs.KEY_USER_AGENT, "default"));
        rows.add(ua);

        rows.add(toggle(ID_ADBLOCK, "广告与追踪器拦截", "基于 assets/adblock.txt 规则",
                IcePrefs.KEY_ADBLOCK_ENABLED, true));
        rows.add(toggle(ID_JS, "JavaScript", "关闭后部分网页无法正常显示",
                IcePrefs.KEY_JS_ENABLED, true));
        rows.add(toggle(ID_IMAGES, "加载图片", "关闭可省流量、提速",
                IcePrefs.KEY_IMAGES_ENABLED, true));
        rows.add(toggle(ID_COOKIES, "允许 Cookie", "关闭后无法保持登录状态",
                IcePrefs.KEY_COOKIES_ENABLED, true));
        rows.add(toggle(ID_FORCE_DARK, "网页深色适配", "深色主题下自动压暗网页背景",
                IcePrefs.KEY_FORCE_DARK, true));
        rows.add(toggle(ID_SWIPE, "边缘滑动导航", "从屏幕左/右边缘滑动前进后退",
                IcePrefs.KEY_SWIPE_NAV, true));
        rows.add(toggle(ID_RESTORE, "恢复上次标签", "重新打开时还原上次的网页",
                IcePrefs.KEY_RESTORE_TABS, true));
        rows.add(toggle(ID_FORM_DATA, "保存表单数据", "记住输入过的表单内容",
                IcePrefs.KEY_SAVE_FORM_DATA, true));

        Row rules = new Row(ID_RULES, KIND_ACTION, "拦截规则");
        rules.sub = "查看内置规则库规模";
        rules.value = "查看";
        rows.add(rules);

        Row dir = new Row(ID_DL_DIR, KIND_ACTION, "下载目录");
        dir.sub = "应用私有目录，无需存储权限";
        dir.value = "查看";
        rows.add(dir);

        Row clear = new Row(ID_CLEAR, KIND_ACTION, "清除浏览数据");
        clear.sub = "历史 / Cookie / 缓存 / 下载";
        rows.add(clear);

        Row about = new Row(ID_ABOUT, KIND_ACTION, "关于");
        about.sub = "版本与项目信息";
        about.value = "v" + Constants.VERSION_NAME;
        rows.add(about);
    }

    private Row toggle(int id, String title, String sub, String key, boolean def) {
        Row r = new Row(id, KIND_TOGGLE, title);
        r.sub = sub;
        r.prefKey = key;
        r.def = def;
        return r;
    }

    private void onRowClick(final Row row) {
        if (row.kind == KIND_TOGGLE) {
            boolean next = !IcePrefs.getBool(this, row.prefKey, row.def);
            IcePrefs.setBool(this, row.prefKey, next);
            showToast(next ? "已开启「" + row.title + "」" : "已关闭「" + row.title + "」");
            notifyChanged();
            return;
        }
        switch (row.id) {
            case ID_HOMEPAGE: chooseHomepage(); break;
            case ID_ENGINE: chooseEngine(); break;
            case ID_THEME: chooseTheme(); break;
            case ID_FONT: chooseFontSize(); break;
            case ID_UA: chooseUserAgent(); break;
            case ID_RULES: showRules(); break;
            case ID_DL_DIR: showDownloadDir(); break;
            case ID_CLEAR: clearData(); break;
            case ID_ABOUT: showAbout(); break;
        }
    }

    // ---------------- 各项处理 ----------------

    private void chooseHomepage() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        et.setText(IcePrefs.getHomepage(this));
        new AlertDialog.Builder(this)
                .setTitle("主页")
                .setView(et)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String v = et.getText().toString().trim();
                        if (v.isEmpty()) {
                            IcePrefs.setHomepage(SettingsActivity.this, Constants.HOME_URL);
                        } else if (UrlUtils.isHttp(v) || v.startsWith("file:")) {
                            IcePrefs.setHomepage(SettingsActivity.this, v);
                        } else {
                            IcePrefs.setHomepage(SettingsActivity.this, "https://" + v);
                        }
                        notifyChanged();
                    }
                })
                .setNeutralButton("恢复默认", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        IcePrefs.setHomepage(SettingsActivity.this, Constants.HOME_URL);
                        notifyChanged();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseEngine() {
        String cur = IcePrefs.getSearchEngine(this);
        int idx = 0;
        for (int i = 0; i < ENGINE_VALUES.length; i++) {
            if (ENGINE_VALUES[i].equals(cur)) { idx = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle("搜索引擎")
                .setSingleChoiceItems(ENGINE_ENTRIES, idx, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        IcePrefs.setSearchEngine(SettingsActivity.this, ENGINE_VALUES[which]);
                        d.dismiss();
                        notifyChanged();
                    }
                })
                .show();
    }

    private void chooseTheme() {
        int idx = 0;
        String cur = IcePrefs.getTheme(this);
        for (int i = 0; i < ThemeManager.ALL.length; i++) {
            if (ThemeManager.ALL[i].equals(cur)) { idx = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle("主题")
                .setSingleChoiceItems(THEME_ENTRIES, idx, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        // 立即生效：写入首选项后重建本界面
                        if (ThemeManager.set(SettingsActivity.this, ThemeManager.ALL[which])) {
                            recreate();
                        }
                    }
                })
                .show();
    }

    private void chooseFontSize() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(IcePrefs.getInt(this, IcePrefs.KEY_READER_FONT_SIZE, 18)));
        new AlertDialog.Builder(this)
                .setTitle("阅读模式字号 (12 - 32)")
                .setView(et)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            int size = Integer.parseInt(et.getText().toString().trim());
                            if (size < 12) size = 12;
                            if (size > 32) size = 32;
                            IcePrefs.setInt(SettingsActivity.this, IcePrefs.KEY_READER_FONT_SIZE, size);
                            notifyChanged();
                        } catch (Exception e) {
                            showToast("请输入 12 - 32 之间的数字");
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseUserAgent() {
        String cur = IcePrefs.getString(this, IcePrefs.KEY_USER_AGENT, "default");
        int idx = 0;
        for (int i = 0; i < UA_VALUES.length; i++) {
            if (UA_VALUES[i].equals(cur)) { idx = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle("用户代理")
                .setSingleChoiceItems(UA_ENTRIES, idx, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        IcePrefs.setString(SettingsActivity.this, IcePrefs.KEY_USER_AGENT, UA_VALUES[which]);
                        d.dismiss();
                        notifyChanged();
                    }
                })
                .show();
    }

    private void showRules() {
        // 构造一次 AdBlocker 触发规则加载，ruleStats() 才有数据
        new AdBlocker(this);
        new AlertDialog.Builder(this)
                .setTitle("拦截规则")
                .setMessage("规则来自 assets/adblock.txt，可自行增删后重新打包。\n\n"
                        + "当前：" + AdBlocker.ruleStats()
                        + "\n\n语法：\n"
                        + "• 裸域名：匹配该域名及其子域名\n"
                        + "• path:xxx：URL 子串匹配\n"
                        + "• re:xxx：URL 正则匹配\n"
                        + "• @@xxx：例外，永远放行")
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showDownloadDir() {
        String path = IceFileProvider.downloadDir(this).getAbsolutePath();
        new AlertDialog.Builder(this)
                .setTitle("下载目录")
                .setMessage(path + "\n\n该目录位于应用私有外部存储，"
                        + "读写不需要申请存储权限；在「下载」页面可长按文件进行打开或分享。")
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void clearData() {
        final String[] labels = {"历史记录", "Cookie 与登录状态", "网页缓存", "下载记录与文件"};
        final boolean[] checked = {true, true, true, false};
        new AlertDialog.Builder(this)
                .setTitle("清除浏览数据")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton(R.string.clear, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        StringBuilder done = new StringBuilder();
                        if (checked[0]) {
                            DatabaseHelper db = new DatabaseHelper(SettingsActivity.this);
                            try { db.deleteHistoryAll(); } finally { db.close(); }
                            done.append("历史 ");
                        }
                        if (checked[1]) {
                            try {
                                CookieManager cm = CookieManager.getInstance();
                                cm.removeAllCookies(null);
                                cm.flush();
                            } catch (Exception ignored) {}
                            done.append("Cookie ");
                        }
                        if (checked[2]) {
                            clearWebCache();
                            done.append("缓存 ");
                        }
                        if (checked[3]) {
                            DownloadService.removeAll(SettingsActivity.this);
                            done.append("下载 ");
                        }
                        showToast(done.length() == 0 ? "未选择任何项目" : ("已清除：" + done.toString().trim()));
                        notifyChanged();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 用临时 WebView 清空 WebView 自身缓存，并清掉缓存目录。 */
    private void clearWebCache() {
        try {
            WebView wv = new WebView(this);
            wv.clearCache(true);
            wv.clearFormData();
            wv.clearHistory();
            wv.destroy();
        } catch (Exception ignored) {
        }
        try {
            File cache = getCacheDir();
            deleteRecursively(cache);
        } catch (Exception ignored) {
        }
    }

    private void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursively(c);
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("ice 浏览器")
                .setMessage("版本 " + Constants.VERSION_NAME + "\n\n"
                        + "极简、便携、零第三方依赖的 Android 浏览器。\n\n"
                        + "• 多 WebView 真标签页 + 状态恢复\n"
                        + "• 自研 ice 搜索引擎（异步 HTML 解析）\n"
                        + "• 4 套主题：浅色 / 深色 / 纯黑 / 护眼\n"
                        + "• 广告与追踪器拦截（可自定义规则）\n"
                        + "• 阅读模式 · 页内查找 · 桌面版 · 无痕\n"
                        + "• 边缘滑动导航 · 全屏视频 · 文件选择\n"
                        + "• 系统 DownloadManager + 下载记录\n\n"
                        + "技术：纯 Java（零第三方库）\n"
                        + "构建：Termux aapt + dx 单 dex\n\n"
                        + "© 2026 ice-wocker · MIT License")
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    // ---------------- 辅助 ----------------

    private String displayEngine(String engine) {
        if (Constants.ENGINE_ICE.equals(engine)) return "ice（内置）";
        return engine;
    }

    private String displayUa(String ua) {
        if ("desktop".equals(ua)) return "桌面版";
        if ("mobile".equals(ua)) return "移动版";
        return "默认";
    }

    private String shorten(String url) {
        if (url == null) return "";
        String s = UrlUtils.prettyUrl(url);
        return s.length() > 28 ? s.substring(0, 28) + "…" : s;
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void notifyChanged() {
        buildRows();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // ---------------- 列表适配 ----------------

    private class SettingAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }

        @Override public Object getItem(int position) { return rows.get(position); }

        @Override public long getItemId(int position) { return rows.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(SettingsActivity.this)
                        .inflate(R.layout.item_setting, parent, false);
            }
            Row row = rows.get(position);
            TextView title = (TextView) convertView.findViewById(R.id.setting_title);
            TextView sub = (TextView) convertView.findViewById(R.id.setting_sub);
            TextView value = (TextView) convertView.findViewById(R.id.setting_value);
            CheckBox check = (CheckBox) convertView.findViewById(R.id.setting_check);

            title.setText(row.title);

            if (row.sub == null || row.sub.isEmpty()) {
                sub.setVisibility(View.GONE);
            } else {
                sub.setVisibility(View.VISIBLE);
                sub.setText(row.sub);
            }

            if (row.kind == KIND_TOGGLE) {
                value.setVisibility(View.GONE);
                check.setVisibility(View.VISIBLE);
                check.setChecked(IcePrefs.getBool(SettingsActivity.this, row.prefKey, row.def));
            } else {
                check.setVisibility(View.GONE);
                if (row.value == null || row.value.isEmpty()) {
                    value.setVisibility(View.GONE);
                } else {
                    value.setVisibility(View.VISIBLE);
                    value.setText(row.value);
                }
            }
            return convertView;
        }
    }
}