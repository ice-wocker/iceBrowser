package com.icebrowser.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自研的极简 FileProvider（零依赖，不引入 androidx）。
 *
 * 为什么需要它：Android 7.0 起用 file:// 把文件交给外部应用会抛
 * FileUriExposedException。浏览器下载完要「打开 / 分享」，必须给出发送方
 * content:// URI，所以这里实现一个只读、带路径越权校验的 ContentProvider。
 *
 * URI 形式：content://com.icebrowser.app.files/<root>/<相对路径>
 * root 取值：ext（外部私有目录，下载默认位置）、files、cache。
 */
public class IceFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.icebrowser.app.files";

    private static final String ROOT_EXT = "ext";
    private static final String ROOT_FILES = "files";
    private static final String ROOT_CACHE = "cache";

    /** 把应用私有目录下的文件转成可对外授权的 content:// URI。 */
    public static Uri getUriForFile(Context ctx, File file) {
        if (file == null) return null;
        try {
            File canonical = file.getCanonicalFile();
            Map<String, File> roots = roots(ctx);
            for (Map.Entry<String, File> e : roots.entrySet()) {
                File root = e.getValue().getCanonicalFile();
                String rootPath = root.getPath();
                String filePath = canonical.getPath();
                if (filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator)) {
                    String rel = filePath.substring(rootPath.length());
                    if (rel.startsWith(File.separator)) rel = rel.substring(1);
                    return new Uri.Builder()
                            .scheme("content")
                            .authority(AUTHORITY)
                            .appendPath(e.getKey())
                            .appendPath(rel)
                            .build();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 下载文件的统一存放目录：外部私有目录下的 Download/iceBrowser。 */
    public static File downloadDir(Context ctx) {
        File base = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (base == null) base = new File(ctx.getFilesDir(), "Download");
        File dir = new File(base, "iceBrowser");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private static Map<String, File> roots(Context ctx) {
        Map<String, File> map = new LinkedHashMap<>();
        File ext = ctx.getExternalFilesDir(null);
        if (ext != null) map.put(ROOT_EXT, ext);
        map.put(ROOT_FILES, ctx.getFilesDir());
        map.put(ROOT_CACHE, ctx.getCacheDir());
        return map;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("只读 Provider，不支持写入: " + mode);
        }
        File file = resolve(uri);
        if (file == null || !file.exists()) {
            throw new FileNotFoundException("找不到文件: " + uri);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        File file = resolve(uri);
        if (file == null) return "application/octet-stream";
        return UrlUtils.guessMime(file.getName());
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = resolve(uri);
        if (file == null) return null;
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = file.length();
            else row[i] = null;
        }
        cursor.addRow(row);
        return cursor;
    }

    /**
     * 把 URI 解析成真实文件，并校验它确实落在允许的根目录内，
     * 防止 ../ 越权读取应用其他私有文件。
     */
    private File resolve(Uri uri) {
        try {
            if (getContext() == null) return null;
            java.util.List<String> segments = uri.getPathSegments();
            if (segments == null || segments.size() < 2) return null;
            String rootKey = segments.get(0);
            File root = roots(getContext()).get(rootKey);
            if (root == null) return null;

            StringBuilder rel = new StringBuilder();
            for (int i = 1; i < segments.size(); i++) {
                if (i > 1) rel.append(File.separator);
                rel.append(segments.get(i));
            }
            File target = new File(root, rel.toString());
            String canonicalRoot = root.getCanonicalPath();
            String canonicalTarget = target.getCanonicalPath();
            if (!canonicalTarget.startsWith(canonicalRoot + File.separator)) return null;
            return target;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("只读 Provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("只读 Provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("只读 Provider");
    }
}