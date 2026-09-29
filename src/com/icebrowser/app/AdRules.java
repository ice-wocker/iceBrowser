package com.icebrowser.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 广告拦截的规则库与匹配逻辑（纯 Java，不依赖 Android）。
 *
 * <p>把它从 AdBlocker 里拆出来，是因为这部分才是真正会出错的地方——
 * 规则解析、例外优先级、域名后缀匹配、正则容错。AdBlocker 只剩「读 assets
 * 内容喂进来」和「跟 WebView 打交道」两件事，逻辑薄到不值得测。
 *
 * <p>拆出来之后这段代码可以用普通 JUnit 在 JVM 上直接跑，不需要
 * Robolectric 之类的第三方依赖——本项目的「零第三方依赖」底线得以保持。
 */
public final class AdRules {

    private final Set<String> hosts = new HashSet<>();
    private final List<String> paths = new ArrayList<>();
    private final List<Pattern> regexes = new ArrayList<>();
    private final Set<String> exceptionHosts = new HashSet<>();
    private final List<Pattern> exceptionRegexes = new ArrayList<>();

    /** 解析结果统计，便于日志与设置页展示。 */
    public int hostCount() { return hosts.size(); }
    public int pathCount() { return paths.size(); }
    public int regexCount() { return regexes.size(); }
    public int exceptionCount() { return exceptionHosts.size() + exceptionRegexes.size(); }

    /**
     * 逐行解析规则文本。格式见 assets/adblock.txt 头部注释。
     *
     * <p>容错原则：坏规则只跳过它自己，不能让整库加载失败。
     */
    public void load(String text) {
        if (text == null) return;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            // 注释：'#' 与 '!'（list v2 风格）
            if (line.startsWith("#") || line.startsWith("!")) continue;

            // 例外：永远放行
            if (line.startsWith("@@")) {
                String rest = line.substring(2).trim();
                if (rest.isEmpty()) continue;
                if (rest.startsWith("re:")) {
                    Pattern p = compile(rest.substring(3).trim());
                    if (p != null) exceptionRegexes.add(p);
                } else {
                    exceptionHosts.add(rest.toLowerCase(Locale.ROOT));
                }
                continue;
            }

            if (line.startsWith("re:")) {
                String body = line.substring(3).trim();
                if (body.isEmpty()) continue;
                Pattern p = compile(body);
                if (p != null) regexes.add(p);
                continue;
            }

            if (line.startsWith("path:")) {
                String body = line.substring(5).trim();
                if (body.isEmpty()) continue;
                paths.add(body.toLowerCase(Locale.ROOT));
                continue;
            }

            // 含 '/' 的裸规则按完整 URL 子串处理（如 facebook.com/tr）
            if (line.indexOf('/') >= 0) {
                paths.add(line.toLowerCase(Locale.ROOT));
            } else {
                hosts.add(line.toLowerCase(Locale.ROOT));
            }
        }
    }

    private static Pattern compile(String regex) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            return null;   // 坏正则跳过，不影响其它规则
        }
    }

    /**
     * 判断一个 URL 是否该被拦截。
     *
     * <p>命中顺序：例外 > 域名 > 路径子串 > 正则。
     */
    public boolean shouldBlock(String url) {
        if (url == null || url.isEmpty()) return false;
        // 内部资源与本地页面不动
        if (url.startsWith("file:///android_asset/") || url.startsWith("data:")
                || url.startsWith("blob:") || url.startsWith("about:")) {
            return false;
        }

        String lower = url.toLowerCase(Locale.ROOT);

        // 1) 例外优先放行
        for (String ex : exceptionHosts) {
            if (lower.contains(ex)) return false;
        }
        for (Pattern p : exceptionRegexes) {
            if (p.matcher(url).find()) return false;
        }

        // 2) 域名规则
        String host = hostOf(url).toLowerCase(Locale.ROOT);
        if (matchHost(host)) return true;

        // 3) 路径子串
        for (String rule : paths) {
            if (lower.contains(rule)) return true;
        }

        // 4) 正则
        for (Pattern p : regexes) {
            if (p.matcher(url).find()) return true;
        }
        return false;
    }

    /**
     * 域名匹配：检查完整 host 及它的每一级后缀。
     * host = "ads.cdn.doubleclick.net" 会查
     * "ads.cdn.doubleclick.net" / "cdn.doubleclick.net" / "doubleclick.net" / "net"。
     */
    boolean matchHost(String host) {
        if (host == null || host.isEmpty()) return false;
        if (hosts.contains(host)) return true;
        int idx = host.indexOf('.');
        while (idx >= 0 && idx + 1 < host.length()) {
            if (hosts.contains(host.substring(idx + 1))) return true;
            idx = host.indexOf('.', idx + 1);
        }
        return false;
    }

    /** 从 URL 里取 host（不含 scheme / path / 端口）。 */
    static String hostOf(String url) {
        if (url == null) return "";
        int start = url.indexOf("://");
        start = (start < 0) ? 0 : start + 3;
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') { end = i; break; }
        }
        String host = url.substring(start, end);
        int at = host.indexOf('@');          // 去掉 user:pass@
        if (at >= 0) host = host.substring(at + 1);
        int colon = host.indexOf(':');       // 去掉端口
        if (colon >= 0) host = host.substring(0, colon);
        return host;
    }

    /** 内置兜底规则：assets 读不到时至少还有一层保护。 */
    public static AdRules fallback() {
        AdRules r = new AdRules();
        Collections.addAll(r.hosts,
                "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                "googletagmanager.com", "googletagservices.com", "google-analytics.com",
                "adservice.google.com", "adnxs.com", "adroll.com", "criteo.com",
                "rubiconproject.com", "pubmatic.com", "openx.net", "yieldmo.com",
                "smaato.net", "inmobi.com", "mopub.com", "flurry.com",
                "chartboost.com", "vungle.com", "applovin.com", "tapjoy.com",
                "adcolony.com", "connect.facebook.net", "ads.youtube.com",
                "pagead2.googlesyndication.com", "tpc.googlesyndication.com");
        Collections.addAll(r.paths, "/pagead/", "/adserver/", "/ads/");
        return r;
    }
}
