package com.icebrowser.app;

import android.content.Context;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 广告 / 追踪器拦截（v3）。
 *
 * <p>规则从 assets/adblock.txt 加载，格式见该文件头部注释。真正的解析与匹配
 * 逻辑在 {@link AdRules}——那个类是纯 Java、可用普通 JUnit 测试的；
 * 这里只负责「读 assets」和「对接 WebView」，薄到不值得测。
 *
 * <p>规则库是进程内共享的：多个 Tab / 多次 new 只解析一次。
 */
public class AdBlocker {

    private static final String TAG = "IceAdBlock";
    private static final String ASSET = "adblock.txt";

    private static volatile AdRules sharedRules = null;
    private static volatile Context appContext = null;

    /** 累计拦截次数，仅用于展示。 */
    private volatile int blockedCount = 0;
    private volatile boolean enabled = true;

    public AdBlocker(Context ctx) {
        enabled = IcePrefs.getBool(ctx, IcePrefs.KEY_ADBLOCK_ENABLED, true);
        if (appContext == null) appContext = ctx.getApplicationContext();
        rules();   // 触发一次加载
    }

    private static AdRules rules() {
        AdRules r = sharedRules;
        if (r == null) {
            synchronized (AdBlocker.class) {
                r = sharedRules;
                if (r == null) {
                    r = loadRules(appContext);
                    sharedRules = r;
                }
            }
        }
        return r;
    }

    private static AdRules loadRules(Context ctx) {
        AdRules parsed = new AdRules();
        try {
            InputStream in = ctx.getAssets().open(ASSET);
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            in.close();

            parsed.load(sb.toString());
            Log.i(TAG, "rules loaded: host=" + parsed.hostCount()
                    + " path=" + parsed.pathCount()
                    + " regex=" + parsed.regexCount()
                    + " exception=" + parsed.exceptionCount());
            // 合法文件不该一条规则都没有；出现即视为资源损坏，走兜底
            if (parsed.hostCount() + parsed.pathCount() + parsed.regexCount() == 0) {
                Log.w(TAG, "no rules parsed, using built-in fallback");
                return AdRules.fallback();
            }
            return parsed;
        } catch (Exception e) {
            Log.e(TAG, "load rules failed, using built-in fallback", e);
            return AdRules.fallback();
        }
    }

    // =========================================================
    //  匹配
    // =========================================================

    public boolean shouldBlock(WebResourceRequest req) {
        if (req == null || req.getUrl() == null) return false;
        // 主文档（顶层导航）永不拦截，否则会把正常网页挡掉
        if (req.isForMainFrame()) return false;
        return shouldBlock(req.getUrl().toString());
    }

    public boolean shouldBlock(String url) {
        if (!enabled) return false;
        boolean block = rules().shouldBlock(url);
        if (block) blockedCount++;
        return block;
    }

    public WebResourceResponse createEmptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream(new byte[0]));
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    public void resetBlockedCount() {
        blockedCount = 0;
    }

    /** 规则库规模，供设置界面显示。 */
    public static String ruleStats() {
        AdRules r = rules();
        return r.hostCount() + " 条域名规则 / "
                + r.pathCount() + " 条路径规则 / "
                + r.regexCount() + " 条正则规则";
    }
}
