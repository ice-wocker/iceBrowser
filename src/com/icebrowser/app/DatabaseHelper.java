package com.icebrowser.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 数据访问层（v2）。
 *
 * 相比旧版修复了三个致命问题：
 * 1. 历史记录之前从不写入 —— 现在 addHistory 按 URL 去重累加 visit_count；
 * 2. 书签无法删除/修改 —— 现在有完整 CRUD 与 isBookmarked；
 * 3. 下载从不入库 —— 现在提供 insertDownload/updateDownloadProgress。
 *
 * 所有方法都自吞异常并返回安全默认值，调用方不需要 try/catch。
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "IceDB";
    private static final String DB_NAME = "ice_browser.db";
    private static final int DB_VERSION = 2;

    public static final String FOLDER_ROOT = "root";

    // 下载状态
    public static final int DL_PENDING = 0;
    public static final int DL_RUNNING = 1;
    public static final int DL_PAUSED = 2;
    public static final int DL_COMPLETED = 3;
    public static final int DL_FAILED = 4;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS history ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "url TEXT NOT NULL,"
                + "title TEXT,"
                + "timestamp INTEGER NOT NULL,"
                + "visit_count INTEGER DEFAULT 1)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_history_url ON history(url)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_ts ON history(timestamp DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "url TEXT NOT NULL,"
                + "title TEXT,"
                + "folder TEXT DEFAULT 'root',"
                + "position INTEGER DEFAULT 0,"
                + "timestamp INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_bookmark_url ON bookmarks(url)");

        db.execSQL("CREATE TABLE IF NOT EXISTS downloads ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "url TEXT NOT NULL,"
                + "file_name TEXT,"
                + "file_path TEXT,"
                + "mime_type TEXT,"
                + "total_size INTEGER DEFAULT 0,"
                + "downloaded INTEGER DEFAULT 0,"
                + "status INTEGER DEFAULT 0,"
                + "dm_id INTEGER DEFAULT -1,"
                + "start_time INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_download_ts ON downloads(start_time DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 逐版本增量升级，尽量保留用户数据。
        if (oldVersion < 2) {
            safeExec(db, "ALTER TABLE bookmarks ADD COLUMN position INTEGER DEFAULT 0");
            safeExec(db, "ALTER TABLE downloads ADD COLUMN dm_id INTEGER DEFAULT -1");
            safeExec(db, "CREATE UNIQUE INDEX IF NOT EXISTS idx_bookmark_url ON bookmarks(url)");
            safeExec(db, "CREATE UNIQUE INDEX IF NOT EXISTS idx_history_url ON history(url)");
            safeExec(db, "CREATE INDEX IF NOT EXISTS idx_history_ts ON history(timestamp DESC)");
            safeExec(db, "CREATE INDEX IF NOT EXISTS idx_download_ts ON downloads(start_time DESC)");
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 忽略，避免旧版本回退时清库
    }

    private void safeExec(SQLiteDatabase db, String sql) {
        try {
            db.execSQL(sql);
        } catch (Exception e) {
            Log.w(TAG, "exec failed: " + sql, e);
        }
    }

    // =========================================================
    //  历史
    // =========================================================

    /** 记录一次访问：同 URL 则更新时间与访问次数，否则插入新行。 */
    public void addHistory(String url, String title) {
        if (url == null || url.isEmpty()) return;
        if (url.startsWith("about:") || url.startsWith("javascript:")) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            long now = System.currentTimeMillis();
            Cursor c = db.query("history", new String[]{"_id", "visit_count"},
                    "url=?", new String[]{url}, null, null, null, "1");
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                int count = c.getInt(1) + 1;
                ContentValues cv = new ContentValues();
                cv.put("title", title);
                cv.put("timestamp", now);
                cv.put("visit_count", count);
                db.update("history", cv, "_id=?", new String[]{String.valueOf(id)});
            } else {
                ContentValues cv = new ContentValues();
                cv.put("url", url);
                cv.put("title", title);
                cv.put("timestamp", now);
                cv.put("visit_count", 1);
                db.insert("history", null, cv);
            }
            if (c != null) c.close();
        } catch (Exception e) {
            Log.e(TAG, "addHistory", e);
        }
    }

    /** 最近历史，按时间倒序。 */
    public List<HistoryItem> getHistory(int limit) {
        List<HistoryItem> list = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query("history",
                    new String[]{"_id", "url", "title", "timestamp", "visit_count"},
                    null, null, null, null, "timestamp DESC", String.valueOf(limit));
            while (c.moveToNext()) {
                HistoryItem h = new HistoryItem();
                h.id = c.getLong(0);
                h.url = c.getString(1);
                h.title = c.getString(2);
                h.timestamp = c.getLong(3);
                h.visitCount = c.getInt(4);
                list.add(h);
            }
        } catch (Exception e) {
            Log.e(TAG, "getHistory", e);
        } finally {
            close(c);
        }
        return list;
    }

    /** 按标题或 URL 模糊搜索历史。 */
    public List<HistoryItem> searchHistory(String keyword, int limit) {
        List<HistoryItem> list = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return getHistory(limit);
        Cursor c = null;
        try {
            String like = "%" + keyword.trim() + "%";
            c = getReadableDatabase().query("history",
                    new String[]{"_id", "url", "title", "timestamp", "visit_count"},
                    "title LIKE ? OR url LIKE ?", new String[]{like, like},
                    null, null, "timestamp DESC", String.valueOf(limit));
            while (c.moveToNext()) {
                HistoryItem h = new HistoryItem();
                h.id = c.getLong(0);
                h.url = c.getString(1);
                h.title = c.getString(2);
                h.timestamp = c.getLong(3);
                h.visitCount = c.getInt(4);
                list.add(h);
            }
        } catch (Exception e) {
            Log.e(TAG, "searchHistory", e);
        } finally {
            close(c);
        }
        return list;
    }

    public void deleteHistoryItem(long id) {
        try {
            getWritableDatabase().delete("history", "_id=?", new String[]{String.valueOf(id)});
        } catch (Exception e) {
            Log.e(TAG, "deleteHistoryItem", e);
        }
    }

    public void deleteHistoryByUrl(String url) {
        try {
            getWritableDatabase().delete("history", "url=?", new String[]{url});
        } catch (Exception e) {
            Log.e(TAG, "deleteHistoryByUrl", e);
        }
    }

    public void deleteHistoryAll() {
        try {
            getWritableDatabase().delete("history", null, null);
        } catch (Exception e) {
            Log.e(TAG, "deleteHistoryAll", e);
        }
    }

    public int getHistoryCount() {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM history", null);
            if (c.moveToFirst()) return c.getInt(0);
        } catch (Exception e) {
            Log.e(TAG, "getHistoryCount", e);
        } finally {
            close(c);
        }
        return 0;
    }

    // =========================================================
    //  书签
    // =========================================================

    public boolean addBookmark(String url, String title, String folder) {
        if (url == null || url.isEmpty()) return false;
        try {
            SQLiteDatabase db = getWritableDatabase();
            if (isBookmarked(url)) {
                ContentValues cv = new ContentValues();
                cv.put("title", title);
                db.update("bookmarks", cv, "url=?", new String[]{url});
                return true;
            }
            ContentValues cv = new ContentValues();
            cv.put("url", url);
            cv.put("title", title);
            cv.put("folder", folder != null ? folder : FOLDER_ROOT);
            cv.put("position", bookmarksCount());
            cv.put("timestamp", System.currentTimeMillis());
            return db.insert("bookmarks", null, cv) > 0;
        } catch (Exception e) {
            Log.e(TAG, "addBookmark", e);
            return false;
        }
    }

    public boolean updateBookmark(long id, String title, String url) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("title", title);
            cv.put("url", url);
            return getWritableDatabase().update("bookmarks", cv, "_id=?",
                    new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            Log.e(TAG, "updateBookmark", e);
            return false;
        }
    }

    /** 按 URL 删除，返回是否删掉了记录。 */
    public boolean removeBookmark(String url) {
        try {
            return getWritableDatabase().delete("bookmarks", "url=?",
                    new String[]{url}) > 0;
        } catch (Exception e) {
            Log.e(TAG, "removeBookmark", e);
            return false;
        }
    }

    public void deleteBookmark(long id) {
        try {
            getWritableDatabase().delete("bookmarks", "_id=?", new String[]{String.valueOf(id)});
        } catch (Exception e) {
            Log.e(TAG, "deleteBookmark", e);
        }
    }

    public boolean isBookmarked(String url) {
        if (url == null || url.isEmpty()) return false;
        Cursor c = null;
        try {
            c = getReadableDatabase().query("bookmarks", new String[]{"_id"},
                    "url=?", new String[]{url}, null, null, null, "1");
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            Log.e(TAG, "isBookmarked", e);
            return false;
        } finally {
            close(c);
        }
    }

    public List<Bookmark> getBookmarks() {
        List<Bookmark> list = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query("bookmarks",
                    new String[]{"_id", "url", "title", "folder", "timestamp"},
                    null, null, null, null, "position ASC, _id ASC");
            while (c.moveToNext()) {
                Bookmark b = new Bookmark();
                b.id = c.getLong(0);
                b.url = c.getString(1);
                b.title = c.getString(2);
                b.folder = c.getString(3);
                b.timestamp = c.getLong(4);
                list.add(b);
            }
        } catch (Exception e) {
            Log.e(TAG, "getBookmarks", e);
        } finally {
            close(c);
        }
        return list;
    }

    public List<Bookmark> searchBookmarks(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return getBookmarks();
        List<Bookmark> list = new ArrayList<>();
        Cursor c = null;
        try {
            String like = "%" + keyword.trim() + "%";
            c = getReadableDatabase().query("bookmarks",
                    new String[]{"_id", "url", "title", "folder", "timestamp"},
                    "title LIKE ? OR url LIKE ?", new String[]{like, like},
                    null, null, "position ASC, _id ASC");
            while (c.moveToNext()) {
                Bookmark b = new Bookmark();
                b.id = c.getLong(0);
                b.url = c.getString(1);
                b.title = c.getString(2);
                b.folder = c.getString(3);
                b.timestamp = c.getLong(4);
                list.add(b);
            }
        } catch (Exception e) {
            Log.e(TAG, "searchBookmarks", e);
        } finally {
            close(c);
        }
        return list;
    }

    public int bookmarksCount() {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM bookmarks", null);
            if (c.moveToFirst()) return c.getInt(0);
        } catch (Exception e) {
            Log.e(TAG, "bookmarksCount", e);
        } finally {
            close(c);
        }
        return 0;
    }

    // =========================================================
    //  下载
    // =========================================================

    /** 插入一条下载记录，返回行 id（-1 表示失败）。 */
    public long insertDownload(String url, String fileName, String filePath,
                               String mimeType, long totalSize, int status) {
        return insertDownload(url, fileName, filePath, mimeType, totalSize, status, -1);
    }

    /** 插入一条下载记录（带系统 DownloadManager 的 id，用于回查真实进度）。 */
    public long insertDownload(String url, String fileName, String filePath,
                               String mimeType, long totalSize, int status, long dmId) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("url", url);
            cv.put("file_name", fileName);
            cv.put("file_path", filePath);
            cv.put("mime_type", mimeType);
            cv.put("total_size", totalSize);
            cv.put("downloaded", 0);
            cv.put("status", status);
            cv.put("dm_id", dmId);
            cv.put("start_time", System.currentTimeMillis());
            return getWritableDatabase().insert("downloads", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "insertDownload", e);
            return -1;
        }
    }

    public void updateDownloadProgress(long id, long downloaded, long totalSize, int status) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("downloaded", downloaded);
            if (totalSize > 0) cv.put("total_size", totalSize);
            cv.put("status", status);
            getWritableDatabase().update("downloads", cv, "_id=?",
                    new String[]{String.valueOf(id)});
        } catch (Exception e) {
            Log.e(TAG, "updateDownloadProgress", e);
        }
    }

    public void updateDownloadStatus(long id, int status, String filePath) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("status", status);
            if (filePath != null) cv.put("file_path", filePath);
            getWritableDatabase().update("downloads", cv, "_id=?",
                    new String[]{String.valueOf(id)});
        } catch (Exception e) {
            Log.e(TAG, "updateDownloadStatus", e);
        }
    }

    public List<DownloadItem> getDownloads() {
        List<DownloadItem> list = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query("downloads",
                    new String[]{"_id", "url", "file_name", "file_path", "mime_type",
                            "total_size", "downloaded", "status", "dm_id", "start_time"},
                    null, null, null, null, "start_time DESC");
            while (c.moveToNext()) {
                DownloadItem d = new DownloadItem();
                d.id = c.getLong(0);
                d.url = c.getString(1);
                d.fileName = c.getString(2);
                d.filePath = c.getString(3);
                d.mimeType = c.getString(4);
                d.totalSize = c.getLong(5);
                d.downloaded = c.getLong(6);
                d.status = c.getInt(7);
                d.dmId = c.getLong(8);
                d.startTime = c.getLong(9);
                list.add(d);
            }
        } catch (Exception e) {
            Log.e(TAG, "getDownloads", e);
        } finally {
            close(c);
        }
        return list;
    }

    public void deleteDownload(long id) {
        try {
            getWritableDatabase().delete("downloads", "_id=?", new String[]{String.valueOf(id)});
        } catch (Exception e) {
            Log.e(TAG, "deleteDownload", e);
        }
    }

    public void deleteDownloadsAll() {
        try {
            getWritableDatabase().delete("downloads", null, null);
        } catch (Exception e) {
            Log.e(TAG, "deleteDownloadsAll", e);
        }
    }

    // =========================================================
    //  工具
    // =========================================================

    private void close(Cursor c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    // =========================================================
    //  模型
    // =========================================================

    public static class HistoryItem {
        public long id;
        public String url;
        public String title;
        public long timestamp;
        public int visitCount;
    }

    public static class Bookmark {
        public long id;
        public String url;
        public String title;
        public String folder;
        public long timestamp;
    }

    public static class DownloadItem {
        public long id;
        public String url;
        public String fileName;
        public String filePath;
        public String mimeType;
        public long totalSize;
        public long downloaded;
        public int status;
        /** 系统 DownloadManager 的任务 id，-1 表示非系统下载。 */
        public long dmId = -1;
        public long startTime;

        /** 进度百分比 0~100；总大小未知时返回 -1。 */
        public int percent() {
            if (totalSize <= 0) return -1;
            int p = (int) (downloaded * 100 / totalSize);
            return p < 0 ? 0 : (p > 100 ? 100 : p);
        }
    }
}