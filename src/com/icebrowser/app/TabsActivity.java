package com.icebrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * 真 tab 列表 - 显示所有打开的 tab 缩略图
 * 单击切换, 长按关闭, X 关闭
 */
public class TabsActivity extends Activity {
    private GridView gridView;
    private TabsManager tabManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tabs);
        
        tabManager = MainActivity.staticTabsManager;
        if (tabManager == null) {
            finish();
            return;
        }
        
        gridView = (GridView) findViewById(R.id.tabs_grid);
        if (gridView != null) {
            gridView.setAdapter(new TabsAdapter());
        }
        
        View btnNew = findViewById(R.id.btn_new_tab);
        if (btnNew != null) {
            btnNew.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    TabsManager.Tab t = tabManager.createTab(null, false);
                    t.webView.loadUrl("file:///android_asset/home.html");
                    finish();
                }
            });
        }
        
        View btnIncognito = findViewById(R.id.btn_incognito);
        if (btnIncognito != null) {
            btnIncognito.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    TabsManager.Tab t = tabManager.createTab(null, true);
                    t.webView.loadUrl("file:///android_asset/home.html?mode=incognito");
                    finish();
                }
            });
        }
        
        View btnCloseAll = findViewById(R.id.btn_close_all);
        if (btnCloseAll != null) {
            btnCloseAll.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(TabsActivity.this)
                        .setTitle("关闭所有标签?")
                        .setMessage("将关闭除当前外的所有标签页")
                        .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                tabManager.closeAll();
                                finish();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
                }
            });
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (gridView != null && tabManager != null) {
            ((BaseAdapter) gridView.getAdapter()).notifyDataSetChanged();
        }
    }
    
    private class TabsAdapter extends BaseAdapter {
        @Override public int getCount() { return tabManager != null ? tabManager.getTabCount() : 0; }
        
        @Override public Object getItem(int position) {
            return tabManager != null ? tabManager.getTab(position) : null;
        }
        
        @Override public long getItemId(int position) { return position; }
        
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(TabsActivity.this)
                    .inflate(R.layout.item_tab, parent, false);
            }
            
            final TabsManager.Tab tab = tabManager.getTab(position);
            if (tab == null) return convertView;
            
            ImageView thumb = (ImageView) convertView.findViewById(R.id.tab_thumb);
            TextView title = (TextView) convertView.findViewById(R.id.tab_title);
            TextView url = (TextView) convertView.findViewById(R.id.tab_url);
            View closeBtn = convertView.findViewById(R.id.tab_close);
            View activeIndicator = convertView.findViewById(R.id.tab_active);
            
            title.setText(tab.title != null ? tab.title : "新标签页");
            url.setText(tab.url != null ? tab.url : "");
            
            // 缩略图
            if (tab.webView != null) {
                tab.webView.setDrawingCacheEnabled(true);
                Bitmap cache = tab.webView.getDrawingCache();
                if (cache != null) {
                    Bitmap scaled = scaleBitmap(cache, 480, 800);
                    thumb.setImageBitmap(scaled);
                } else {
                    thumb.setImageResource(R.drawable.ic_globe);
                }
            } else {
                thumb.setImageResource(R.drawable.ic_globe);
            }
            
            // 当前 tab 高亮
            boolean isCurrent = position == tabManager.getCurrentIndex();
            activeIndicator.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
            convertView.setBackgroundResource(isCurrent ? R.drawable.tab_active_bg : R.drawable.tab_bg);
            
            // 单击切换
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    tabManager.switchToTab(position);
                    finish();
                }
            });
            
            // 长按关闭
            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    new AlertDialog.Builder(TabsActivity.this)
                        .setTitle("关闭标签?")
                        .setMessage(tab.title)
                        .setPositiveButton("关闭", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                tabManager.closeTab(position);
                                if (gridView.getAdapter() != null) {
                                    ((BaseAdapter) gridView.getAdapter()).notifyDataSetChanged();
                                }
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
                    return true;
                }
            });
            
            // X 按钮
            if (closeBtn != null) {
                closeBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        tabManager.closeTab(position);
                        if (gridView.getAdapter() != null) {
                            ((BaseAdapter) gridView.getAdapter()).notifyDataSetChanged();
                        }
                    }
                });
            }
            
            return convertView;
        }
        
        private Bitmap scaleBitmap(Bitmap src, int maxW, int maxH) {
            if (src == null) return null;
            int w = src.getWidth();
            int h = src.getHeight();
            if (w <= maxW && h <= maxH) return src;
            float scale = Math.min((float) maxW / w, (float) maxH / h);
            int nw = (int) (w * scale);
            int nh = (int) (h * scale);
            Bitmap result = Bitmap.createScaledBitmap(src, nw, nh, true);
            return result;
        }
    }
}