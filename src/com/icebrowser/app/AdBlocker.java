package com.icebrowser.app;

import android.content.Context;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 广告 / 追踪器拦截（v2）。
 *
 * 规则不再硬编码在 Java 里，而是从 assets/adblock.txt 加载，格式见该文件头部注释：
 *  - 裸域名：host 或完整 URL 的子串匹配
 *  - path:xxx：完整 URL 子串匹配
 *  - re:xxx：完整 URL 正则匹配（大小写不敏感）
 *  - @@xxx / @@re:xxx：例外规则，永远放行
 *
 * 命中顺序：先查例外（放行），再查 host（最快）、path、regex。
 * 内置一份兜底规则，即使 assets 读取失败也能工作。
 */
public class AdBlocker {

    private static final String TAG = "IceAdBlock";
    private static final String ASSET = "adblock.txt";

    private static final Set<String> EXCEPTION_HOSTS = new HashSet<>();
    private static final List<Pattern> EXCEPTION_REGEX = new ArrayList<>();

    /** 累计拦截次数，仅用于展示。 */
    private volatile int blockedCount = 0;
    private volatile boolean enabled = true;

    /** 静态规则库是否已加载（多个实例共享磁盘读取）。 */
    private static volatile boolean rulesLoaded = false;

    public AdBlocker(Context ctx) {
        enabled = IcePrefs.getBool(ctx, IcePrefs.KEY_ADBLOCK_ENABLED, true);
        ensureRulesLoaded(ctx);
    }

    private static synchronized void ensureRulesLoaded(Context ctx) {
        if (rulesLoaded) return;
        try {
            Context app = ctx.getApplicationContext();
            InputStream in = app.getAssets().open(ASSET);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            int host = 0, path = 0, regex = 0, except = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#") || line.startsWith("!")) continue;

                if (line.startsWith("@@")) {
                    String r = line.substring(2).trim();
                    if (r.startsWith("re:")) {
                        Pattern p = compile(r.substring(3).trim());
                        if (p != null) {
                            EXCEPTION_REGEX.add(p);
                            except++;
                        }
                    } else if (!r.isEmpty()) {
                        EXCEPTION_HOSTS.add(r.toLowerCase(Locale.ROOT));
                        except++;
                    }
                    continue;
                }
                if (line.startsWith("re:")) {
                    Pattern p = compile(line.substring(3).trim());
                    if (p != null) {
                        SHARED_REGEX.add(p);
                        regex++;
                    }
                    continue;
                }
                if (line.startsWith("path:")) {
                    String r = line.substring(5).trim();
                    if (!r.isEmpty()) {
                        SHARED_PATHS.add(r.toLowerCase(Locale.ROOT));
                        path++;
                    }
                    continue;
                }
                if (line.indexOf('/') >= 0) {
                    // 含路径的规则（如 facebook.com/tr）按完整 URL 子串匹配
                    SHARED_PATHS.add(line.toLowerCase(Locale.ROOT));
                    path++;
                } else {
                    SHARED_HOSTS.add(line.toLowerCase(Locale.ROOT));
                    host++;
                }
            }
            br.close();
            in.close();
            Log.i(TAG, "rules loaded: host=" + host + " path=" + path
                    + " regex=" + regex + " exception=" + except);
        } catch (Exception e) {
            Log.e(TAG, "load rules failed, using built-in fallback", e);
            loadFallback();
        }
        rulesLoaded = true;
    }

    // 共享规则库：所有 AdBlocker 实例共用一份，避免每个 Tab 重复解析
    private static final Set<String> SHARED_HOSTS = new HashSet<>();
    private static final List<String> SHARED_PATHS = new ArrayList<>();
    private static final List<Pattern> SHARED_REGEX = new ArrayList<>();

    private static Pattern compile(String regex) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            Log.w(TAG, "bad regex: " + regex);
            return null;
        }
    }

    private static void loadFallback() {
        Collections.addAll(SHARED_HOSTS,
                "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                "googletagmanager.com", "googletagservices.com", "google-analytics.com",
                "adservice.google.com", "adnxs.com", "adroll.com", "criteo.com",
                "rubiconproject.com", "pubmatic.com", "openx.net", "yieldmo.com",
                "smaato.net", "inmobi.com", "mopub.com", "flurry.com",
                "chartboost.com", "vungle.com", "applovin.com", "tapjoy.com",
                "adcolony.com", "connect.facebook.net", "ads.youtube.com",
                "pagead2.googlesyndication.com", "tpc.googlesyndication.com");
        Collections.addAll(SHARED_PATHS, "/pagead/", "/adserver/", "/ads/");
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
        if (!enabled || url == null || url.isEmpty()) return false;
        // 内部资源与本地页面不动
        if (url.startsWith("file:///android_asset/") || url.startsWith("data:")
                || url.startsWith("blob:") || url.startsWith("about:")) {
            return false;
        }

        String lower = url.toLowerCase(Locale.ROOT);

        // 1) 例外优先放行
        for (String ex : EXCEPTION_HOSTS) {
            if (lower.contains(ex)) return false;
        }
        for (Pattern p : EXCEPTION_REGEX) {
            if (p.matcher(url).find()) return false;
        }

        // 2) 域名规则：按标签逐级比对后缀，避免遍历全部规则（O(标签数) 而非 O(规则数)）
        String host = UrlUtils.hostOf(url).toLowerCase(Locale.ROOT);
        if (matchHost(host)) {
            blockedCount++;
            return true;
        }

        // 3) 路径子串
        for (String rule : SHARED_PATHS) {
            if (lower.contains(rule)) {
                blockedCount++;
                return true;
            }
        }

        // 4) 正则
        for (Pattern p : SHARED_REGEX) {
            if (p.matcher(url).find()) {
                blockedCount++;
                return true;
            }
        }
        return false;
    }

    /**
     * 域名匹配：检查完整 host 及它的每一级后缀。
     * 例如 host = "ads.cdn.doubleclick.net" 会依次检查
     * "ads.cdn.doubleclick.net" / "cdn.doubleclick.net" / "doubleclick.net" / "net"。
     */
    private boolean matchHost(String host) {
        if (host == null || host.isEmpty()) return false;
        if (SHARED_HOSTS.contains(host)) return true;
        int idx = host.indexOf('.');
        while (idx >= 0 && idx + 1 < host.length()) {
            if (SHARED_HOSTS.contains(host.substring(idx + 1))) return true;
            idx = host.indexOf('.', idx + 1);
        }
        return false;
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
        return SHARED_HOSTS.size() + " 条域名规则 / "
                + SHARED_PATHS.size() + " 条路径规则 / "
                + SHARED_REGEX.size() + " 条正则规则";
    }
}