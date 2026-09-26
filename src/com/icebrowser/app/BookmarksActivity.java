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

import java.util.ArrayList;
import java.util.List;

/**
 * 书签（v2）。
 *
 * 旧版调用不存在的 getAllBookmarks() 导致崩溃；现在改用 getBookmarks()，
 * 并支持点击打开、长按删除、右上角一键清空。
 */
public class BookmarksActivity extends Activity {

    private DatabaseHelper db;
    private ListView listView;
    private final List<DatabaseHelper.Bookmark> items = new ArrayList<>();
    private BookmarksAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ThemeManager.applyTo(this);
            setContentView(R.layout.activity_list);

            db = new DatabaseHelper(this);

            TextView title = (TextView) findViewById(R.id.title);
            if (title != null) title.setText(R.string.bookmarks);

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
            TextView emptyView = (TextView) findViewById(R.id.empty);
            if (listView == null) {
                finish();
                return;
            }
            if (emptyView != null) {
                emptyView.setText(R.string.no_bookmarks);
                listView.setEmptyView(emptyView);
            }

            adapter = new BookmarksAdapter();
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
                    final DatabaseHelper.Bookmark item = items.get(position);
                    new AlertDialog.Builder(BookmarksActivity.this)
                            .setTitle(item.title != null && !item.title.isEmpty()
                                    ? item.title : item.url)
                            .setItems(new String[]{"打开", "复制链接", "删除书签"},
                                    new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface d, int which) {
                                            if (which == 0) {
                                                Intent intent = new Intent();
                                                intent.putExtra("url", item.url);
                                                setResult(RESULT_OK, intent);
                                                finish();
                                            } else if (which == 1) {
                                                copy(item.url);
                                            } else {
                                                db.deleteBookmark(item.id);
                                                loadBookmarks();
                                                Toast.makeText(BookmarksActivity.this,
                                                        R.string.bookmark_deleted, Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    })
                            .show();
                    return true;
                }
            });

            loadBookmarks();
        } catch (Throwable t) {
            android.util.Log.e("Bookmarks", "onCreate", t);
            Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBookmarks();
    }

    private void loadBookmarks() {
        try {
            List<DatabaseHelper.Bookmark> list = db.getBookmarks();
            items.clear();
            items.addAll(list);
            if (adapter != null) adapter.notifyDataSetChanged();
        } catch (Exception e) {
            android.util.Log.e("Bookmarks", "loadBookmarks", e);
        }
    }

    private void copy(String text) {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", text));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private void confirmClearAll() {
        if (items.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("清空书签")
                .setMessage("确定要删除全部 " + items.size() + " 个书签吗？")
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        // DatabaseHelper 只提供单条删除，这里逐条清理
                        for (DatabaseHelper.Bookmark b : new ArrayList<>(items)) {
                            db.deleteBookmark(b.id);
                        }
                        loadBookmarks();
                        Toast.makeText(BookmarksActivity.this, R.string.deleted,
                                Toast.LENGTH_SHORT).show();
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

    private class BookmarksAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }

        @Override public Object getItem(int position) { return items.get(position); }

        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(BookmarksActivity.this)
                        .inflate(R.layout.item_list, parent, false);
            }
            DatabaseHelper.Bookmark item = items.get(position);
            TextView title = (TextView) convertView.findViewById(R.id.item_title);
            TextView sub = (TextView) convertView.findViewById(R.id.item_sub);

            String name = item.title;
            if (name == null || name.trim().isEmpty()) name = UrlUtils.prettyUrl(item.url);
            title.setText(name);
            sub.setText(UrlUtils.prettyUrl(item.url));
            return convertView;
        }
    }
}