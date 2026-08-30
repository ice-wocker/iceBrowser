package com.icebrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends Activity {
    private DatabaseHelper db;
    private ListView listView;
    private List<String[]> history = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            db = new DatabaseHelper(this);
            setContentView(R.layout.activity_list);
            
            TextView title = (TextView) findViewById(R.id.title);
            if (title != null) title.setText("历史记录");
            
            listView = (ListView) findViewById(R.id.list);
            if (listView == null) {
                finish();
                return;
            }
            
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (position < history.size()) {
                        Intent intent = new Intent();
                        intent.putExtra("url", history.get(position)[1]);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                }
            });
            
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                    new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle("清除记录")
                        .setMessage("确定要清除所有历史吗？")
                        .setPositiveButton("清除全部", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                try {
                                    db.deleteHistoryAll();
                                    loadHistory();
                                } catch (Exception e) {}
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
                    return true;
                }
            });
            
            loadHistory();
        } catch (Throwable t) {
            android.util.Log.e("History", "Error", t);
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
            history = db.getAllHistory();
            String[] items = new String[history.size()];
            for (int i = 0; i < history.size(); i++) {
                String title = history.get(i)[0];
                if (title == null || title.isEmpty()) title = history.get(i)[1];
                items[i] = title;
            }
            listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
        } catch (Exception e) {
            android.util.Log.e("History", "loadHistory", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (db != null) db.close();
        } catch (Exception e) {}
    }
}