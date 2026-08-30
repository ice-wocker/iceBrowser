package com.icebrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private ListView listView;
    private String[] items = {
        "主页", "搜索引擎", "主题", "阅读模式字体大小", "用户代理",
        "清除浏览数据", "关于"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            prefs = getSharedPreferences("ice_prefs", MODE_PRIVATE);
            setContentView(R.layout.activity_list);
            
            TextView title = (TextView) findViewById(R.id.title);
            if (title != null) title.setText("设置");
            
            listView = (ListView) findViewById(R.id.list);
            if (listView == null) {
                finish();
                return;
            }
            
            listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    handleClick(position);
                }
            });
        } catch (Throwable t) {
            android.util.Log.e("Settings", "Error", t);
            finish();
        }
    }
    
    private void handleClick(int position) {
        try {
            switch (position) {
                case 0: showHomepageDialog(); break;
                case 1: showSearchEngineDialog(); break;
                case 2: showThemeDialog(); break;
                case 3: showFontSizeDialog(); break;
                case 4: showUserAgentDialog(); break;
                case 5: showClearDataDialog(); break;
                case 6: showAbout(); break;
            }
        } catch (Exception e) {
            android.util.Log.e("Settings", "handleClick", e);
        }
    }
    
    private void showHomepageDialog() {
        final EditText et = new EditText(this);
        et.setText(prefs.getString("homepage", "file:///android_asset/home.html"));
        new AlertDialog.Builder(this)
            .setTitle("主页")
            .setView(et)
            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    prefs.edit().putString("homepage", et.getText().toString()).apply();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void showSearchEngineDialog() {
        final String[] engines = {"Bing", "Google", "DuckDuckGo", "百度", "搜狗"};
        String current = prefs.getString("search_engine", "Bing");
        int idx = 0;
        for (int i = 0; i < engines.length; i++) if (engines[i].equals(current)) { idx = i; break; }
        new AlertDialog.Builder(this)
            .setTitle("搜索引擎")
            .setSingleChoiceItems(engines, idx, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    prefs.edit().putString("search_engine", engines[which]).apply();
                    d.dismiss();
                }
            })
            .show();
    }
    
    private void showThemeDialog() {
        final String[] entries = {"跟随系统", "浅色", "深色"};
        new AlertDialog.Builder(this)
            .setTitle("主题")
            .setItems(entries, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    String[] values = {"auto", "light", "dark"};
                    prefs.edit().putString("theme", values[which]).apply();
                }
            })
            .show();
    }
    
    private void showFontSizeDialog() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(prefs.getInt("reader_font_size", 18)));
        new AlertDialog.Builder(this)
            .setTitle("阅读模式字体大小")
            .setView(et)
            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    try {
                        int size = Integer.parseInt(et.getText().toString());
                        prefs.edit().putInt("reader_font_size", size).apply();
                    } catch (Exception e) {}
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void showUserAgentDialog() {
        final String[] entries = {"默认", "桌面 Chrome"};
        new AlertDialog.Builder(this)
            .setTitle("用户代理")
            .setItems(entries, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    String[] values = {"default", "desktop"};
                    prefs.edit().putString("user_agent", values[which]).apply();
                }
            })
            .show();
    }
    
    private void showClearDataDialog() {
        new AlertDialog.Builder(this)
            .setTitle("清除浏览数据")
            .setMessage("确定要清除所有历史记录、Cookie、缓存吗？")
            .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    try {
                        DatabaseHelper db = new DatabaseHelper(SettingsActivity.this);
                        db.deleteHistoryAll();
                        db.close();
                        android.webkit.CookieManager.getInstance().removeAllCookies(null);
                        android.webkit.CookieManager.getInstance().flush();
                        Toast.makeText(SettingsActivity.this, "已清除", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {}
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void showAbout() {
        new AlertDialog.Builder(this)
            .setTitle("ice 浏览器")
            .setMessage("版本 2.0.0\n\n一款极简但功能强大的 Android 浏览器\n\n纯 Java · 零依赖 · 150KB\n\nhttps://github.com/ice-wocker/iceBrowser")
            .setPositiveButton("确定", null)
            .show();
    }
}