package com.icebrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 下载记录（v2）。
 *
 * 修复三处旧问题：
 * 1. 调用不存在的 getAllDownloads() → 改用 getDownloads()；
 * 2. 打开文件用 file:// → Android 7.0+ 直接抛 FileUriExposedException，
 *    现在改为 {@link IceFileProvider} 生成 content:// 并授权读取；
 * 3. 从不刷新状态 → onResume 时回查系统 DownloadManager，显示真实进度。
 */
public class DownloadsActivity extends Activity {

    private ListView listView;
    private final List<DatabaseHelper.DownloadItem> items = new ArrayList<>();
    private DownloadsAdapter adapter;

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ThemeManager.applyTo(this);
            setContentView(R.layout.activity_list);

            TextView title = (TextView) findViewById(R.id.title);
            if (title != null) title.setText(R.string.downloads);

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
                emptyView.setText(R.string.no_downloads);
                listView.setEmptyView(emptyView);
            }

            adapter = new DownloadsAdapter();
            listView.setAdapter(adapter);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (position < 0 || position >= items.size()) return;
                    openFile(items.get(position));
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override public boolean onItemLongClick(AdapterView<?> parent, View view,
                                                         final int position, long id) {
                    if (position < 0 || position >= items.size()) return true;
                    final DatabaseHelper.DownloadItem item = items.get(position);
                    new AlertDialog.Builder(DownloadsActivity.this)
                            .setTitle(item.fileName != null ? item.fileName : item.url)
                            .setItems(new String[]{"打开", "分享", "删除记录和文件"},
                                    new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface d, int which) {
                                            if (which == 0) openFile(item);
                                            else if (which == 1) shareFile(item);
                                            else removeItem(item);
                                        }
                                    })
                            .show();
                    return true;
                }
            });

            loadDownloads();
        } catch (Throwable t) {
            android.util.Log.e("Downloads", "onCreate", t);
            Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 回查系统下载状态，保证列表里的进度是真实的
        DownloadService.refreshAll(this);
        loadDownloads();
    }

    private void loadDownloads() {
        try {
            DatabaseHelper db = new DatabaseHelper(this);
            List<DatabaseHelper.DownloadItem> list;
            try {
                list = db.getDownloads();
            } finally {
                db.close();
            }
            items.clear();
            items.addAll(list);
            if (adapter != null) adapter.notifyDataSetChanged();
        } catch (Exception e) {
            android.util.Log.e("Downloads", "loadDownloads", e);
        }
    }

    /** 定位下载文件：优先用数据库里的绝对路径，回退到约定的下载目录。 */
    private File resolveFile(DatabaseHelper.DownloadItem item) {
        if (item == null) return null;
        if (!TextUtils.isEmpty(item.filePath)) {
            File f = new File(item.filePath);
            if (f.exists()) return f;
        }
        if (!TextUtils.isEmpty(item.fileName)) {
            File f = new File(IceFileProvider.downloadDir(this), item.fileName);
            if (f.exists()) return f;
        }
        return null;
    }

    private String mimeOf(DatabaseHelper.DownloadItem item, File file) {
        if (item != null && !TextUtils.isEmpty(item.mimeType) && item.mimeType.contains("/")) {
            return item.mimeType;
        }
        return UrlUtils.guessMime(file != null ? file.getName() : (item != null ? item.fileName : null));
    }

    private void openFile(DatabaseHelper.DownloadItem item) {
        File file = resolveFile(item);
        if (file == null) {
            Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = IceFileProvider.getUriForFile(this, file);
        if (uri == null) {
            Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeOf(item, file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.open_file)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(DatabaseHelper.DownloadItem item) {
        File file = resolveFile(item);
        if (file == null) {
            Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = IceFileProvider.getUriForFile(this, file);
        if (uri == null) {
            Toast.makeText(this, "无法分享", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeOf(item, file));
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share)));
        } catch (Exception e) {
            Toast.makeText(this, "无法分享", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeItem(DatabaseHelper.DownloadItem item) {
        DownloadService.removeDownload(this, item);
        loadDownloads();
        Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show();
    }

    private void confirmClearAll() {
        if (items.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("清空下载")
                .setMessage("将删除全部 " + items.size() + " 条下载记录及已下载的文件。")
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        for (DatabaseHelper.DownloadItem item : new ArrayList<>(items)) {
                            DownloadService.removeDownload(DownloadsActivity.this, item);
                        }
                        loadDownloads();
                        Toast.makeText(DownloadsActivity.this, R.string.deleted,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String statusText(DatabaseHelper.DownloadItem item) {
        switch (item.status) {
            case DatabaseHelper.DL_COMPLETED:
                return "已完成";
            case DatabaseHelper.DL_FAILED:
                return "失败";
            case DatabaseHelper.DL_PAUSED:
                return "已暂停";
            case DatabaseHelper.DL_RUNNING: {
                int p = item.percent();
                return p >= 0 ? ("下载中 " + p + "%") : "下载中";
            }
            case DatabaseHelper.DL_PENDING:
            default:
                return "等待中";
        }
    }

    private static String readableSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private class DownloadsAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }

        @Override public Object getItem(int position) { return items.get(position); }

        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(DownloadsActivity.this)
                        .inflate(R.layout.item_list, parent, false);
            }
            DatabaseHelper.DownloadItem item = items.get(position);
            TextView title = (TextView) convertView.findViewById(R.id.item_title);
            TextView sub = (TextView) convertView.findViewById(R.id.item_sub);

            String name = item.fileName;
            if (TextUtils.isEmpty(name)) name = UrlUtils.prettyUrl(item.url);
            title.setText(name);

            StringBuilder sb = new StringBuilder();
            sb.append(statusText(item));
            String size = readableSize(item.totalSize);
            if (!size.isEmpty()) sb.append(" · ").append(size);
            if (item.startTime > 0) sb.append(" · ").append(TIME_FMT.format(new Date(item.startTime)));
            sub.setText(sb.toString());
            return convertView;
        }
    }
}