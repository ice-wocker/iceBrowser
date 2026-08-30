package com.icebrowser.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.TextView;

public class ReaderActivity extends Activity {
    private WebView webView;
    private int fontSize = 18;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_reader);
            
            String title = getIntent().getStringExtra("title");
            String content = getIntent().getStringExtra("content");
            fontSize = getSharedPreferences("ice_prefs", MODE_PRIVATE).getInt("reader_font_size", 18);
            
            TextView titleView = (TextView) findViewById(R.id.reader_title);
            if (titleView != null) titleView.setText(title != null ? title : "");
            
            webView = (WebView) findViewById(R.id.reader_web);
            if (webView == null) {
                finish();
                return;
            }
            
            ImageButton btnBack = (ImageButton) findViewById(R.id.btn_back);
            if (btnBack != null) btnBack.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });
            
            ImageButton btnFontUp = (ImageButton) findViewById(R.id.btn_font_up);
            if (btnFontUp != null) btnFontUp.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { fontSize += 2; saveAndReload(); }
            });
            
            ImageButton btnFontDown = (ImageButton) findViewById(R.id.btn_font_down);
            if (btnFontDown != null) btnFontDown.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { 
                    if (fontSize > 10) {
                        fontSize -= 2;
                        saveAndReload();
                    }
                }
            });
            
            try {
                WebSettings settings = webView.getSettings();
                settings.setDefaultTextEncodingName("UTF-8");
                webView.setBackgroundColor(0xFFFFFFFF);
                if (content != null) {
                    String html = "<html><head><style>" +
                        "body { font-size: " + fontSize + "px; line-height: 1.6; color: #212121; padding: 20px; }" +
                        "h1 { font-size: " + (fontSize + 8) + "px; color: #1A73E8; }" +
                        "</style></head><body>" +
                        "<h1>" + escapeHtml(title) + "</h1>" +
                        "<div>" + escapeHtml(content).replace("\n", "<br>") + "</div>" +
                        "</body></html>";
                    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                }
            } catch (Exception e) {
                android.util.Log.e("Reader", "Error", e);
            }
        } catch (Throwable t) {
            android.util.Log.e("Reader", "Error", t);
            finish();
        }
    }
    
    private void saveAndReload() {
        try {
            getSharedPreferences("ice_prefs", MODE_PRIVATE).edit().putInt("reader_font_size", fontSize).apply();
            recreate();
        } catch (Exception e) {}
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}