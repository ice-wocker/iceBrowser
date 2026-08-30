package com.icebrowser.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadsActivity extends Activity {
    private ListView listView;
    private List<String[]> downloads = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_list);
            
            TextView title = (TextView) findViewById(R.id.title);
            if (title != null) title.setText("下载");
            
            listView = (ListView) findViewById(R.id.list);
            if (listView == null) {
                finish();
                return;
            }
            
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (position < downloads.size()) {
                        openFile(downloads.get(position)[0]);
                    }
                }
            });
            
            loadDownloads();
        } catch (Throwable t) {
            android.util.Log.e("Downloads", "Error", t);
            Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadDownloads();
    }
    
    private void loadDownloads() {
        try {
            DatabaseHelper db = new DatabaseHelper(this);
            downloads = db.getAllDownloads();
            db.close();
            
            String[] items = new String[downloads.size()];
            for (int i = 0; i < downloads.size(); i++) {
                items[i] = downloads.get(i)[0] != null ? downloads.get(i)[0] : "未知文件";
            }
            if (items.length == 0) {
                items = new String[]{"暂无下载"};
            }
            listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
        } catch (Exception e) {
            android.util.Log.e("Downloads", "loadDownloads", e);
        }
    }
    
    private void openFile(String fileName) {
        try {
            File downloadsDir = new File(getExternalFilesDir(null), "Download/iceBrowser");
            File file = new File(downloadsDir, fileName);
            if (!file.exists()) {
                File publicDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "iceBrowser");
                file = new File(publicDir, fileName);
            }
            if (!file.exists()) {
                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "打开文件"));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开", Toast.LENGTH_SHORT).show();
        }
    }
}