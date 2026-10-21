package me.fndlabs.itzeditor;

import android.app.Application;

import me.fndlabs.itzeditor.util.CrashReporter;

public final class ItzEditorApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
