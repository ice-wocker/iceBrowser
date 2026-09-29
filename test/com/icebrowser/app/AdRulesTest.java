package com.icebrowser.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 广告拦截规则解析与匹配的测试。
 *
 * <p>AdRules 是纯 Java（无 Android 依赖），所以用普通 JUnit 直接跑，
 * 不需要 Robolectric——项目的「零第三方依赖」底线保住了。
 *
 * <p>这块逻辑此前完全没有测试。规则解析错一处，表现是「某些广告漏了」
 * 或更糟——「把正常网页挡了」，两种都很难靠肉眼发现。
 */
public class AdRulesTest {

    private static AdRules rulesOf(String text) {
        AdRules r = new AdRules();
        r.load(text);
        return r;
    }

    /** 裸域名按 host 及其各级后缀匹配。 */
    @Test
    public void hostRuleMatchesSubdomains() {
        AdRules r = rulesOf("doubleclick.net");
        assertTrue(r.shouldBlock("https://doubleclick.net/x"));
        assertTrue(r.shouldBlock("https://ads.doubleclick.net/x"));
        assertTrue(r.shouldBlock("https://a.b.doubleclick.net/x"));
        assertFalse(r.shouldBlock("https://notdoubleclick.net/x"));
        // 关键：后缀必须按 label 边界，evil-doubleclick.net 不该命中
        assertFalse(r.shouldBlock("https://evil-doubleclick.net/x"));
    }

    /** 注释与空行要被忽略。 */
    @Test
    public void ignoresCommentsAndBlankLines() {
        AdRules r = rulesOf("# 标题\n\n   \n! list v2 注释\nads.example.com\n");
        assertEquals(1, r.hostCount());
        assertEquals(0, r.pathCount());
        assertTrue(r.shouldBlock("https://ads.example.com/a"));
    }

    /** path: 规则对完整 URL 做子串匹配。 */
    @Test
    public void pathRuleMatchesFullUrl() {
        AdRules r = rulesOf("path:/pagead/");
        assertEquals(1, r.pathCount());
        assertEquals(0, r.hostCount());
        assertTrue(r.shouldBlock("https://site.com/pagead/banner.js"));
        assertTrue(r.shouldBlock("https://site.com/PageAd/banner.js"));   // 大小写不敏感
        assertFalse(r.shouldBlock("https://site.com/content/ad.js"));
    }

    /** 含 '/' 的裸规则按完整 URL 子串处理。 */
    @Test
    public void slashRuleBecomesPathRule() {
        AdRules r = rulesOf("facebook.com/tr");
        assertEquals(0, r.hostCount());
        assertEquals(1, r.pathCount());
        assertTrue(r.shouldBlock("https://facebook.com/tr?id=1"));
    }

    /** re: 规则按大小写不敏感的正则匹配完整 URL。 */
    @Test
    public void regexRule() {
        AdRules r = rulesOf("re:[/.]ads?[-/]");
        assertTrue(r.shouldBlock("https://site.com/Ad-banner.png"));
        assertTrue(r.shouldBlock("https://site.com/ads/x"));
        assertFalse(r.shouldBlock("https://site.com/readme.txt"));
    }

    /** 坏正则只跳过自己，不能让整库加载失败。 */
    @Test
    public void badRegexIsSkippedNotFatal() {
        AdRules r = rulesOf("re:([unclosed\nbroken.example.org\n");
        assertEquals(0, r.regexCount());
        assertEquals(1, r.hostCount());
        assertTrue(r.shouldBlock("https://broken.example.org/x"));
    }

    /** @@ 例外优先级最高：即使命中了拦截规则也要放行。 */
    @Test
    public void exceptionWinsOverHostRule() {
        AdRules r = rulesOf("doubleclick.net\n@@safe.doubleclick.net\n");
        assertTrue(r.shouldBlock("https://ads.doubleclick.net/x"));
        assertFalse(r.shouldBlock("https://safe.doubleclick.net/x"));
    }

    /** @@re: 例外按正则对完整 URL 判断。 */
    @Test
    public void exceptionRegex() {
        AdRules r = rulesOf("path:/ads/\n@@re:allowed\\.com/ads/\n");
        assertTrue(r.shouldBlock("https://x.com/ads/a"));
        assertFalse(r.shouldBlock("https://allowed.com/ads/a"));
    }

    /** 内部资源与本地页面绝不拦截。 */
    @Test
    public void internalUrlsNeverBlocked() {
        AdRules r = rulesOf("doubleclick.net\npath:/ads/\n");
        assertFalse(r.shouldBlock("file:///android_asset/home.html"));
        assertFalse(r.shouldBlock("data:text/html,<b>x</b>"));
        assertFalse(r.shouldBlock("about:blank"));
        assertFalse(r.shouldBlock("blob:https://x/ads/1"));
    }

    /** hostOf 要正确处理 scheme / 端口 / 用户信息 / 路径。 */
    @Test
    public void hostExtraction() {
        assertEquals("a.com", AdRules.hostOf("https://a.com/p?q=1"));
        assertEquals("a.com", AdRules.hostOf("http://a.com"));
        assertEquals("a.com", AdRules.hostOf("https://a.com:8443/x"));
        assertEquals("a.com", AdRules.hostOf("https://user:pw@a.com/x"));
        assertEquals("", AdRules.hostOf(""));
        assertEquals("", AdRules.hostOf(null));
    }

    /** 内置兜底规则必须能挡住最常见的广告域。 */
    @Test
    public void fallbackCoversCommonAdHosts() {
        AdRules r = AdRules.fallback();
        assertTrue(r.hostCount() > 20);
        assertTrue(r.shouldBlock("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"));
        assertTrue(r.shouldBlock("https://www.google-analytics.com/collect"));
        assertFalse(r.shouldBlock("https://example.com/index.html"));
    }
}
