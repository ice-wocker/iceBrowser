package com.icebrowser.app;

import android.content.Context;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class AdBlocker {
    private final Set<String> blockedDomains = new HashSet<>();
    private final Set<Pattern> patterns = new HashSet<>();
    private boolean enabled = true;

    public AdBlocker(Context ctx) {
        loadRules();
    }

    private void loadRules() {
        String[] domains = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagmanager.com", "googletagservices.com", "adnxs.com",
            "adroll.com", "criteo.com", "rubiconproject.com", "pubmatic.com",
            "openx.net", "yieldmo.com", "smaato.net", "inmobi.com",
            "mopub.com", "flurry.com", "chartboost.com", "vungle.com",
            "applovin.com", "tapjoy.com", "adcolony.com", "facebook.com/tr",
            "google-analytics.com", "adservice.google.com", "ads.youtube.com",
            "pagead2.googlesyndication.com", "tpc.googlesyndication.com"
        };
        for (String d : domains) {
            blockedDomains.add(d);
        }
    }

    public boolean shouldBlock(WebResourceRequest req) {
        if (req == null) return false;
        return shouldBlock(req.getUrl().toString());
    }

    public boolean shouldBlock(String url) {
        if (!enabled || url == null) return false;
        for (String domain : blockedDomains) {
            if (url.contains(domain)) return true;
        }
        return false;
    }

    public WebResourceResponse createEmptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}