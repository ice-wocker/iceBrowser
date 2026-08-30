package com.icebrowser.app;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.webkit.URLUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadService {
    
    public static void startDownload(Context context, String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(fileName);
            request.setDescription("下载中...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "iceBrowser/" + fileName);
            request.setMimeType(mimeType);
            
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
            }
        } catch (Exception e) {
            android.util.Log.e("Download", "Error", e);
        }
    }
}