package com.icebrowser.app;

public class TabManager {
    private AdBlocker adBlocker;
    
    public TabManager(AdBlocker adBlocker) {
        this.adBlocker = adBlocker;
    }
    
    public AdBlocker getAdBlocker() {
        return adBlocker;
    }
}