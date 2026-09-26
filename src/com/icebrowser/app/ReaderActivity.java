package com.icebrowser.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * 阅读模式。
 *
 * 正文用 WebView 渲染抽取出来的纯文本，并跟随当前主题（浅色 / 深色 / 纯黑 / 护眼）
 * 自动调整底色与文字色；字号可调并持久化。
 */
public class ReaderActivity extends Activity {
    private WebView webView;
    private int fontSize = 18;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ThemeManager.applyTo(this);
            setContentView(R.layout.activity_reader);

            String title = getIntent().getStringExtra("title");
            String content = getIntent().getStringExtra("content");
            fontSize = IcePrefs.getInt(this, IcePrefs.KEY_READER_FONT_SIZE, 18);

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
                @Override public void onClick(View v) {
                    if (fontSize < 40) { fontSize += 2; saveAndReload(); }
                }
            });

            ImageButton btnFontDown = (ImageButton) findViewById(R.id.btn_font_down);
            if (btnFontDown != null) btnFontDown.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (fontSize > 12) { fontSize -= 2; saveAndReload(); }
                }
            });

            try {
                WebSettings settings = webView.getSettings();
                settings.setDefaultTextEncodingName("UTF-8");
                settings.setSupportZoom(true);
                settings.setBuiltInZoomControls(true);
                settings.setDisplayZoomControls(false);
                webView.setBackgroundColor(themeColor(R.attr.iceWebViewBg));
                if (content != null) {
                    webView.loadDataWithBaseURL(null, buildHtml(title, content),
                            "text/html", "UTF-8", null);
                }
            } catch (Exception e) {
                android.util.Log.e("Reader", "render", e);
            }
        } catch (Throwable t) {
            android.util.Log.e("Reader", "onCreate", t);
            finish();
        }
    }

    /** 生成跟随主题的正文页。 */
    private String buildHtml(String title, String content) {
        boolean dark = ThemeManager.isDark(this);
        String bg = toHex(themeColor(R.attr.iceWebViewBg));
        String fg = toHex(themeColor(R.attr.iceToolbarFg));
        String accent = toHex(themeColor(R.attr.iceAccent));
        String sub = toHex(themeColor(R.attr.iceSecondaryFg));
        String divider = toHex(themeColor(R.attr.iceDivider));

        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>"
            + "*{box-sizing:border-box}"
            + "html,body{background:" + bg + ";}"
            + "body{margin:0;padding:20px 22px 48px;color:" + fg + ";"
            + "font-family:-apple-system,'Noto Sans CJK SC','PingFang SC',sans-serif;"
            + "font-size:" + fontSize + "px;line-height:1.75;word-break:break-word;"
            + "-webkit-text-size-adjust:100%;}"
            + "h1{font-size:" + (fontSize + 7) + "px;line-height:1.4;color:" + fg + ";"
            + "margin:0 0 6px;font-weight:700;}"
            + ".meta{font-size:12px;color:" + sub + ";margin-bottom:18px;"
            + "padding-bottom:14px;border-bottom:1px solid " + divider + ";}"
            + "p{margin:0 0 1em;}"
            + "img{max-width:100%;height:auto;}"
            + "a{color:" + accent + ";}"
            + "::selection{background:" + (dark ? "rgba(138,180,248,.4)" : "rgba(26,115,232,.25)") + ";}"
            + "</style></head><body>"
            + "<h1>" + escapeHtml(title) + "</h1>"
            + "<div class=\"meta\">" + appName() + " · 阅读模式</div>"
            + paragraphs(content)
            + "</body></html>";
    }

    /** 把纯文本按空行切成段落，段落内换行转 <br>。 */
    private String paragraphs(String content) {
        if (content == null) return "";
        String[] blocks = content.split("\\n{2,}");
        StringBuilder sb = new StringBuilder();
        for (String block : blocks) {
            String p = escapeHtml(block).replace("\n", "<br>").trim();
            if (!p.isEmpty()) sb.append("<p>").append(p).append("</p>");
        }
        return sb.toString();
    }

    private String appName() {
        return escapeHtml(getString(R.string.app_name));
    }

    private void saveAndReload() {
        try {
            IcePrefs.setInt(this, IcePrefs.KEY_READER_FONT_SIZE, fontSize);
            recreate();
        } catch (Exception ignored) {}
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(attr, tv, true)) return tv.data;
        return 0xFFFFFFFF;
    }

    private static String toHex(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}