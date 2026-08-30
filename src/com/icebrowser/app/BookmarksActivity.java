package com.icebrowser.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class BookmarksActivity extends Activity {
    private DatabaseHelper db;
    private ListView listView;
    private List<String[]> bookmarks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            db = new DatabaseHelper(this);
            setContentView(R.layout.activity_list);
            
            TextView title = (TextView) findViewById(R.id.title);
            if (title != null) title.setText("书签");
            
            listView = (ListView) findViewById(R.id.list);
            if (listView == null) {
                finish();
                return;
            }
            
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (position < bookmarks.size()) {
                        Intent intent = new Intent();
                        intent.putExtra("url", bookmarks.get(position)[1]);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                }
            });
            
            loadBookmarks();
        } catch (Throwable t) {
            android.util.Log.e("Bookmarks", "Error", t);
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
            bookmarks = db.getAllBookmarks();
            String[] items = new String[bookmarks.size()];
            for (int i = 0; i < bookmarks.size(); i++) {
                String title = bookmarks.get(i)[0];
                if (title == null || title.isEmpty()) title = bookmarks.get(i)[1];
                items[i] = title;
            }
            listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
        } catch (Exception e) {
            android.util.Log.e("Bookmarks", "loadBookmarks", e);
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