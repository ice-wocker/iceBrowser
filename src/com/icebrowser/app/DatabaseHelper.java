package com.icebrowser.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "ice_browser.db";
    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS history (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "url TEXT NOT NULL," +
                "title TEXT," +
                "timestamp INTEGER NOT NULL," +
                "visit_count INTEGER DEFAULT 1)");
        
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "url TEXT NOT NULL," +
                "title TEXT," +
                "folder TEXT DEFAULT 'root'," +
                "timestamp INTEGER NOT NULL)");
        
        db.execSQL("CREATE TABLE IF NOT EXISTS downloads (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "url TEXT NOT NULL," +
                "file_name TEXT," +
                "file_path TEXT," +
                "mime_type TEXT," +
                "total_size INTEGER DEFAULT 0," +
                "downloaded INTEGER DEFAULT 0," +
                "status INTEGER DEFAULT 0," +
                "start_time INTEGER NOT NULL)");
        
        db.execSQL("CREATE TABLE IF NOT EXISTS search_engines (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "search_url TEXT NOT NULL," +
                "is_default INTEGER DEFAULT 0)");
        
        insertDefaultEngines(db);
    }
    
    private void insertDefaultEngines(SQLiteDatabase db) {
        String[][] engines = {
            {"Bing", "https://www.bing.com/search?q=%s", "1"},
            {"Google", "https://www.google.com/search?q=%s", "0"},
            {"DuckDuckGo", "https://duckduckgo.com/?q=%s", "0"},
            {"百度", "https://www.baidu.com/s?wd=%s", "0"},
            {"搜狗", "https://www.sogou.com/web?query=%s", "0"}
        };
        for (String[] e : engines) {
            ContentValues cv = new ContentValues();
            cv.put("name", e[0]);
            cv.put("search_url", e[1]);
            cv.put("is_default", e[2]);
            db.insert("search_engines", null, cv);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public void addHistory(String url, String title) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("url", url);
            cv.put("title", title);
            cv.put("timestamp", System.currentTimeMillis());
            db.insert("history", null, cv);
        } catch (Exception e) {
            android.util.Log.e("DB", "addHistory", e);
        }
    }
    
    public void addBookmark(String url, String title, String folder) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("url", url);
            cv.put("title", title);
            cv.put("folder", folder != null ? folder : "root");
            cv.put("timestamp", System.currentTimeMillis());
            db.insert("bookmarks", null, cv);
        } catch (Exception e) {
            android.util.Log.e("DB", "addBookmark", e);
        }
    }
    
    public List<String[]> getAllBookmarks() {
        List<String[]> list = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.query("bookmarks", new String[]{"title", "url"}, null, null, null, null, "_id DESC");
            while (c != null && c.moveToNext()) {
                list.add(new String[]{c.getString(0), c.getString(1)});
            }
            if (c != null) c.close();
        } catch (Exception e) {
            android.util.Log.e("DB", "getAllBookmarks", e);
        }
        return list;
    }
    
    public List<String[]> getAllHistory() {
        List<String[]> list = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.query("history", new String[]{"title", "url", "timestamp"}, null, null, null, null, "_id DESC LIMIT 100");
            while (c != null && c.moveToNext()) {
                list.add(new String[]{c.getString(0), c.getString(1), String.valueOf(c.getLong(2))});
            }
            if (c != null) c.close();
        } catch (Exception e) {
            android.util.Log.e("DB", "getAllHistory", e);
        }
        return list;
    }
    
    public List<String[]> getAllDownloads() {
        List<String[]> list = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.query("downloads", new String[]{"file_name", "url", "status"}, null, null, null, null, "_id DESC LIMIT 100");
            while (c != null && c.moveToNext()) {
                list.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
            }
            if (c != null) c.close();
        } catch (Exception e) {
            android.util.Log.e("DB", "getAllDownloads", e);
        }
        return list;
    }
    
    public void deleteHistoryItem(long id) {
        try {
            getWritableDatabase().delete("history", "_id=?", new String[]{String.valueOf(id)});
        } catch (Exception e) {}
    }
    
    public void deleteHistoryAll() {
        try {
            getWritableDatabase().delete("history", null, null);
        } catch (Exception e) {}
    }
    
    public void deleteBookmark(long id) {
        try {
            getWritableDatabase().delete("bookmarks", "_id=?", new String[]{String.valueOf(id)});
        } catch (Exception e) {}
    }
}