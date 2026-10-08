package com.zremote.console;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 在界面创建前就把主题定下来，避免启动时闪一下。
 * 默认使用 ZCode 风格的深色（黑）。
 */
public class ZRemoteApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences("zremote", MODE_PRIVATE);
        boolean dark = !"light".equals(prefs.getString("theme", "dark"));
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
}