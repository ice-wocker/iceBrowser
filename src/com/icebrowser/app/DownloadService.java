package com.icebrowser.app;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.URLUtil;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 下载服务（v2）。
 *
 * 旧版的问题：只用系统 DownloadManager 排队，从不写数据库，
 * 所以「下载」页面永远是空的，也无法显示进度。
 *
 * 现在：
 *  - 每次下载都写入 downloads 表，并记录系统任务 id（dm_id）；
 *  - {@link #refreshAll} 回查 DownloadManager，把真实进度 / 状态同步进库；
 *  - 文件统一存到应用私有外部目录（Download/iceBrowser），
 *    因此不需要任何存储权限，配合 IceFileProvider 也能安全打开。
 */
public final class DownloadService {

    private DownloadService() {}

    /**
     * 发起一次下载。
     *
     * @return 写入数据库的行 id；失败返回 -1。
     */
    public static long startDownload(Context context, String url, String userAgent,
                                     String contentDisposition, String mimeType) {
        if (url == null || url.isEmpty()) return -1;
        DatabaseHelper db = new DatabaseHelper(context);
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "download_" + System.currentTimeMillis();
            }
            File dir = IceFileProvider.downloadDir(context);
            File target = new File(dir, fileName);
            // 同名文件自动加序号，避免覆盖
            target = uniqueFile(target);

            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(fileName);
            req.setDescription("ice 浏览器正在下载");
            req.setMimeType(mimeType);
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(context,
                    Environment.DIRECTORY_DOWNLOADS, "iceBrowser/" + target.getName());
            if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) req.addRequestHeader("Cookie", cookie);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(true);

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                db.insertDownload(url, target.getName(), target.getAbsolutePath(),
                        mimeType, 0, DatabaseHelper.DL_FAILED);
                return -1;
            }
            long dmId = dm.enqueue(req);
            long rowId = db.insertDownload(url, target.getName(), target.getAbsolutePath(),
                    mimeType, 0, DatabaseHelper.DL_RUNNING, dmId);
            return rowId;
        } catch (Exception e) {
            android.util.Log.e("IceDownload", "startDownload", e);
            db.insertDownload(url, null, null, mimeType, 0, DatabaseHelper.DL_FAILED);
            return -1;
        } finally {
            db.close();
        }
    }

    private static File uniqueFile(File file) {
        if (!file.exists()) return file;
        String name = file.getName();
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            File candidate = new File(file.getParentFile(), base + "(" + i + ")" + ext);
            if (!candidate.exists()) return candidate;
        }
        return file;
    }

    /**
     * 回查系统下载状态，把进度同步进数据库。
     * 只处理仍在进行中的记录，已完成的跳过，避免无谓查询。
     */
    public static void refreshAll(Context context) {
        DatabaseHelper db = new DatabaseHelper(context);
        try {
            List<DatabaseHelper.DownloadItem> items = db.getDownloads();
            if (items.isEmpty()) return;

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return;

            // 系统里已完成/失败的任务不再查
            Map<Long, Long> statusMap = new HashMap<>();
            for (DatabaseHelper.DownloadItem d : items) {
                if (d.dmId <= 0) continue;
                if (d.status == DatabaseHelper.DL_COMPLETED || d.status == DatabaseHelper.DL_FAILED) {
                    continue;
                }
                statusMap.put(d.id, d.dmId);
            }
            if (statusMap.isEmpty()) return;

            long[] ids = new long[statusMap.size()];
            int i = 0;
            for (Long v : statusMap.values()) {
                ids[i++] = v;
            }

            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(ids);
            Cursor c = null;
            try {
                c = dm.query(q);
                if (c == null) return;
                int idxId = c.getColumnIndex(DownloadManager.COLUMN_ID);
                int idxStatus = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int idxBytes = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int idxTotal = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

                while (c.moveToNext()) {
                    long dmId = idxId >= 0 ? c.getLong(idxId) : -1;
                    long rowId = -1;
                    for (Map.Entry<Long, Long> e : statusMap.entrySet()) {
                        if (e.getValue() == dmId) {
                            rowId = e.getKey();
                            break;
                        }
                    }
                    if (rowId < 0) continue;
                    int sysStatus = idxStatus >= 0 ? c.getInt(idxStatus) : 0;
                    long done = idxBytes >= 0 ? c.getLong(idxBytes) : 0;
                    long total = idxTotal >= 0 ? c.getLong(idxTotal) : 0;

                    switch (sysStatus) {
                        case DownloadManager.STATUS_SUCCESSFUL:
                            db.updateDownloadProgress(rowId, total > 0 ? total : done, total,
                                    DatabaseHelper.DL_COMPLETED);
                            break;
                        case DownloadManager.STATUS_FAILED:
                            db.updateDownloadProgress(rowId, done, total,
                                    DatabaseHelper.DL_FAILED);
                            break;
                        case DownloadManager.STATUS_PAUSED:
                            db.updateDownloadProgress(rowId, done, total,
                                    DatabaseHelper.DL_PAUSED);
                            break;
                        case DownloadManager.STATUS_RUNNING:
                        case DownloadManager.STATUS_PENDING:
                        default:
                            db.updateDownloadProgress(rowId, done, total,
                                    DatabaseHelper.DL_RUNNING);
                            break;
                    }
                }
            } finally {
                if (c != null) c.close();
            }
        } catch (Exception e) {
            android.util.Log.e("IceDownload", "refreshAll", e);
        } finally {
            db.close();
        }
    }

    /** 移除系统下载任务并删除已下载的文件。 */
    public static void removeDownload(Context context, DatabaseHelper.DownloadItem item) {
        if (item == null) return;
        try {
            if (item.dmId > 0) {
                DownloadManager dm =
                        (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) dm.remove(item.dmId);
            }
        } catch (Exception ignored) {
        }
        try {
            if (item.filePath != null) {
                File f = new File(item.filePath);
                if (f.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        } catch (Exception ignored) {
        }
        DatabaseHelper db = new DatabaseHelper(context);
        try {
            db.deleteDownload(item.id);
        } finally {
            db.close();
        }
    }
}