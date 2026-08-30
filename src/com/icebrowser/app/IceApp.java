package com.icebrowser.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class IceApp extends Application {
    private static final String TAG = "IceApp";
    private static IceApp instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "IceApp onCreate");
    }
    
    public static IceApp getInstance() {
        return instance;
    }
}
