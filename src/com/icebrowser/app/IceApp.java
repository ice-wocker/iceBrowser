package com.icebrowser.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class IceApp extends Application {
    private static IceApp instance;
    private SharedPreferences prefs;

    public static IceApp getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences("ice_prefs", MODE_PRIVATE);
    }
    
    public SharedPreferences getPrefs() {
        return prefs;
    }
}