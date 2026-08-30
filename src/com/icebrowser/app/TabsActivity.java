package com.icebrowser.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class TabsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_list);
            
            TextView title = (TextView) findViewById(R.id.title);
            if (title != null) title.setText("标签页");
            
            ListView listView = (ListView) findViewById(R.id.list);
            if (listView != null) {
                String[] items = {"新标签页", "无痕模式"};
                listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        finish();
                    }
                });
            }
        } catch (Throwable t) {
            android.util.Log.e("Tabs", "Error", t);
            finish();
        }
    }
}