package com.icebrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 历史记录（v2）。
 *
 * 旧版调用的是 DatabaseHelper 里根本不存在的 getAllHistory()，页面一打开就崩。
 * 现在改用 getHistory()，并支持：点击回填到主界面、长按单条删除、一键清空。
 */
public class HistoryActivity extends Activity {

    private DatabaseHelper db;
    private ListView listView;
    private TextView emptyView;
    private final List<DatabaseHelper.HistoryItem> items = new ArrayList<>();
    private HistoryAdapter adapter;

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ThemeManager.applyTo(this);
            setContentView(R.layout.activity_list);

            db = new DatabaseHelper(this);

            TextView title = (TextView) findViewById(R.id.title);
            if (title != null) title.setText(R.string.history);

            ImageButton back = (ImageButton) findViewById(R.id.btn_back);
            if (back != null) back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });

            ImageButton action = (ImageButton) findViewById(R.id.btn_action);
            if (action != null) {
                action.setVisibility(View.VISIBLE);
                action.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { confirmClearAll(); }
                });
            }

            listView = (ListView) findViewById(R.id.list);
            emptyView = (TextView) findViewById(R.id.empty);
            if (listView == null) {
                finish();
                return;
            }
            if (emptyView != null) {
                emptyView.setText(R.string.no_history);
                listView.setEmptyView(emptyView);
            }

            adapter = new HistoryAdapter();
            listView.setAdapter(adapter);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (position < 0 || position >= items.size()) return;
                    Intent intent = new Intent();
                    intent.putExtra("url", items.get(position).url);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override public boolean onItemLongClick(AdapterView<?> parent, View view,
                                                         final int position, long id) {
                    if (position < 0 || position >= items.size()) return true;
                    final DatabaseHelper.HistoryItem item = items.get(position);
                    new AlertDialog.Builder(HistoryActivity.this)
                            .setTitle(item.title != null && !item.title.isEmpty()
                                    ? item.title : item.url)
                            .setItems(new String[]{"在浏览器打开", "删除此条", "清除全部"},
                                    new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface d, int which) {
                                            if (which == 0) {
                                                Intent intent = new Intent();
                                                intent.putExtra("url", item.url);
                                                setResult(RESULT_OK, intent);
                                                finish();
                                            } else if (which == 1) {
                                                db.deleteHistoryItem(item.id);
                                                loadHistory();
                                            } else {
                                                confirmClearAll();
                                            }
                                        }
                                    })
                            .show();
                    return true;
                }
            });

            loadHistory();
        } catch (Throwable t) {
            android.util.Log.e("History", "onCreate", t);
            Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        try {
            List<DatabaseHelper.HistoryItem> list = db.getHistory(500);
            items.clear();
            items.addAll(list);
            if (adapter != null) adapter.notifyDataSetChanged();
        } catch (Exception e) {
            android.util.Log.e("History", "loadHistory", e);
        }
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_history)
                .setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.clear, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        db.deleteHistoryAll();
                        loadHistory();
                        Toast.makeText(HistoryActivity.this, R.string.deleted, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (db != null) db.close();
        } catch (Exception ignored) {
        }
    }

    private class HistoryAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }

        @Override public Object getItem(int position) { return items.get(position); }

        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(HistoryActivity.this)
                        .inflate(R.layout.item_list, parent, false);
            }
            DatabaseHelper.HistoryItem item = items.get(position);
            TextView title = (TextView) convertView.findViewById(R.id.item_title);
            TextView sub = (TextView) convertView.findViewById(R.id.item_sub);

            String name = item.title;
            if (name == null || name.trim().isEmpty()) name = UrlUtils.prettyUrl(item.url);
            title.setText(name);

            String time = item.timestamp > 0 ? TIME_FMT.format(new Date(item.timestamp)) : "";
            String visits = item.visitCount > 1 ? (" · 访问 " + item.visitCount + " 次") : "";
            sub.setText(UrlUtils.prettyUrl(item.url) + "  " + time + visits);
            return convertView;
        }
    }
}